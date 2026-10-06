package me.wiefferink.areashop.integrations;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import me.wiefferink.areashop.AreaShop;
import me.wiefferink.areashop.regions.GeneralRegion;
import me.wiefferink.areashop.tools.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Talks to VillagerMarket, so AreaShop regions and the shop villagers standing in them know about
 * each other.
 *
 * <p>Everything goes through reflection on purpose. VillagerMarket is an optional plugin and its API
 * is published for one version at a time, so building against it would either force every server to
 * install it or tie AreaShop to one VillagerMarket release. Looking the methods up at runtime means
 * AreaShop runs the same with or without it, and a VillagerMarket update that moves something only
 * switches this integration off with a clear message instead of breaking the plugin.
 *
 * @see <a href="https://github.com/Bestem0r/VillagerMarket">VillagerMarket</a>
 */
public final class VillagerMarketHook {

	private static VillagerMarketHook instance;

	private final boolean available;
	private String unavailableReason;

	private Object shopManager;
	private Class<?> playerShopClass;

	private Method shopManagerGetShops;
	private Method shopManagerRemoveShop;
	private Method shopManagerReloadAll;
	private Method shopGetEntityUuid;
	private Method shopGetEntityInfo;
	private Method shopGetShopName;
	private Method shopGetShopSize;
	private Method shopGetStorageSize;
	private Method shopSetShopfrontSize;
	private Method shopSetStorageSize;
	private Method entityInfoGetLocation;
	private Method playerShopHasOwner;
	private Method playerShopGetOwnerUuid;
	private Method playerShopAbandon;

	private VillagerMarketHook() {
		this.available = resolve();
	}

	/**
	 * Get the hook, setting it up the first time it is asked for.
	 * @return The hook, which may not be available
	 */
	public static VillagerMarketHook getInstance() {
		if(instance == null) {
			instance = new VillagerMarketHook();
			if(instance.available) {
				AreaShop.info("Hooked into VillagerMarket");
			} else {
				AreaShop.debug("VillagerMarket integration is off:", instance.unavailableReason);
			}
		}
		return instance;
	}

	/**
	 * Forget the hook, so it is set up again on the next use.
	 * Called on reload, in case VillagerMarket was installed or removed in the meantime.
	 */
	public static void reset() {
		instance = null;
	}

	/**
	 * Check if VillagerMarket is present and everything AreaShop needs was found.
	 * @return true when the integration can be used
	 */
	public boolean isAvailable() {
		return available;
	}

	/**
	 * Why the integration is not available.
	 * @return The reason, or null when it is available
	 */
	public String getUnavailableReason() {
		return unavailableReason;
	}

	/**
	 * Look up everything AreaShop uses from VillagerMarket.
	 * @return true when all of it was found
	 */
	private boolean resolve() {
		Plugin villagerMarket = Bukkit.getPluginManager().getPlugin("VillagerMarket");
		if(villagerMarket == null || !villagerMarket.isEnabled()) {
			unavailableReason = "VillagerMarket is not installed";
			return false;
		}

		try {
			Class<?> api = Class.forName("net.bestemor.villagermarket.VillagerMarketAPI");
			shopManager = api.getMethod("getShopManager").invoke(null);
			if(shopManager == null) {
				unavailableReason = "VillagerMarket has not started its shop manager yet";
				return false;
			}

			Class<?> shopManagerClass = shopManager.getClass();
			shopManagerGetShops = shopManagerClass.getMethod("getShops");
			shopManagerRemoveShop = shopManagerClass.getMethod("removeShop", UUID.class);
			shopManagerReloadAll = findOptional(shopManagerClass, "reloadAll");

			Class<?> villagerShopClass = Class.forName("net.bestemor.villagermarket.shop.VillagerShop");
			shopGetEntityUuid = villagerShopClass.getMethod("getEntityUUID");
			shopGetEntityInfo = villagerShopClass.getMethod("getEntityInfo");
			shopGetShopName = villagerShopClass.getMethod("getShopName");
			shopGetShopSize = villagerShopClass.getMethod("getShopSize");
			shopGetStorageSize = villagerShopClass.getMethod("getStorageSize");
			shopSetShopfrontSize = villagerShopClass.getMethod("setShopfrontSize", int.class);
			shopSetStorageSize = villagerShopClass.getMethod("setStorageSize", int.class);

			Class<?> entityInfoClass = Class.forName("net.bestemor.villagermarket.shop.EntityInfo");
			entityInfoGetLocation = entityInfoClass.getMethod("getLocation");

			playerShopClass = Class.forName("net.bestemor.villagermarket.shop.PlayerShop");
			playerShopHasOwner = playerShopClass.getMethod("hasOwner");
			playerShopGetOwnerUuid = playerShopClass.getMethod("getOwnerUUID");
			playerShopAbandon = playerShopClass.getMethod("abandon");

			return true;
		} catch(ClassNotFoundException | NoSuchMethodException e) {
			unavailableReason = "this VillagerMarket version does not have " + e.getMessage();
			return false;
		} catch(ReflectiveOperationException | RuntimeException e) {
			unavailableReason = "could not read the VillagerMarket API: " + e.getMessage();
			return false;
		}
	}

	/**
	 * Look up a method that AreaShop can do without.
	 * @param owner The class to look in
	 * @param name  Name of the method
	 * @return The method, or null when this VillagerMarket version does not have it
	 */
	private static Method findOptional(Class<?> owner, String name) {
		try {
			return owner.getMethod(name);
		} catch(NoSuchMethodException e) {
			return null;
		}
	}

	/**
	 * Get all shop villagers standing inside a region.
	 * @param region The region to look in
	 * @return The shops inside it, empty when there are none or the integration is off
	 */
	public List<VillagerMarketShop> getShopsIn(GeneralRegion region) {
		if(!available || region == null) {
			return Collections.emptyList();
		}

		ProtectedRegion worldGuardRegion = region.getRegion();
		if(worldGuardRegion == null || region.getWorld() == null) {
			return Collections.emptyList();
		}

		List<VillagerMarketShop> result = new ArrayList<>();
		try {
			Collection<?> shops = (Collection<?>)shopManagerGetShops.invoke(shopManager);
			for(Object shop : shops) {
				Location location = locationOf(shop);
				if(location == null || location.getWorld() == null) {
					continue;
				}
				if(!location.getWorld().equals(region.getWorld())) {
					continue;
				}
				if(!worldGuardRegion.contains(BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ()))) {
					continue;
				}
				result.add(read(shop, location));
			}
		} catch(ReflectiveOperationException | RuntimeException e) {
			AreaShop.warn("Could not read the VillagerMarket shops of region", region.getName() + ":", Utils.getStackTrace(e));
		}
		return result;
	}

	/**
	 * Get the location of the villager of a shop.
	 * @param shop The VillagerMarket shop object
	 * @return The location, or null when it is not known
	 * @throws ReflectiveOperationException When VillagerMarket does not answer as expected
	 */
	private Location locationOf(Object shop) throws ReflectiveOperationException {
		Object entityInfo = shopGetEntityInfo.invoke(shop);
		if(entityInfo == null) {
			return null;
		}
		return (Location)entityInfoGetLocation.invoke(entityInfo);
	}

	/**
	 * Read what AreaShop needs from a VillagerMarket shop object.
	 * @param shop     The VillagerMarket shop object
	 * @param location Where its villager stands
	 * @return The shop
	 * @throws ReflectiveOperationException When VillagerMarket does not answer as expected
	 */
	private VillagerMarketShop read(Object shop, Location location) throws ReflectiveOperationException {
		OfflinePlayer owner = null;
		if(playerShopClass.isInstance(shop) && Boolean.TRUE.equals(playerShopHasOwner.invoke(shop))) {
			UUID ownerUuid = (UUID)playerShopGetOwnerUuid.invoke(shop);
			if(ownerUuid != null) {
				owner = Bukkit.getOfflinePlayer(ownerUuid);
			}
		}

		return new VillagerMarketShop(
				shop,
				(UUID)shopGetEntityUuid.invoke(shop),
				(String)shopGetShopName.invoke(shop),
				location,
				owner,
				(int)shopGetShopSize.invoke(shop),
				(int)shopGetStorageSize.invoke(shop)
		);
	}

	/**
	 * Hand a shop back to the server, the way VillagerMarket does when a rent runs out.
	 *
	 * <p>The owner keeps what was in it: VillagerMarket refunds the deposit, pays out the money the
	 * shop collected, and puts the stock in the storage they can claim with {@code /vm
	 * expiredstorage}. The villager stays where it is, without an owner, ready for whoever rents
	 * the region next.
	 *
	 * @param shop The shop to hand back
	 * @return true when it was handed back, false when it had no owner or the call failed
	 */
	public boolean abandon(VillagerMarketShop shop) {
		if(!available || shop == null || !playerShopClass.isInstance(shop.handle())) {
			return false;
		}
		try {
			if(!Boolean.TRUE.equals(playerShopHasOwner.invoke(shop.handle()))) {
				return false;
			}
			playerShopAbandon.invoke(shop.handle());
			return true;
		} catch(ReflectiveOperationException | RuntimeException e) {
			AreaShop.warn("Could not hand back VillagerMarket shop", shop.uuid() + ":", Utils.getStackTrace(e));
			return false;
		}
	}

	/**
	 * Delete a shop and the villager standing for it.
	 *
	 * <p>Hand the shop back first if it still has an owner, otherwise their stock goes with it.
	 *
	 * @param shop The shop to delete
	 * @return true when it was deleted
	 */
	public boolean remove(VillagerMarketShop shop) {
		if(!available || shop == null) {
			return false;
		}
		try {
			shopManagerRemoveShop.invoke(shopManager, shop.uuid());
			return true;
		} catch(ReflectiveOperationException | RuntimeException e) {
			AreaShop.warn("Could not remove VillagerMarket shop", shop.uuid() + ":", Utils.getStackTrace(e));
			return false;
		}
	}

	/**
	 * Change how many slots players can buy from in a shop.
	 * @param shop  The shop to change
	 * @param slots The new number of slots, a multiple of nine
	 * @return true when it was changed
	 */
	public boolean setShopSize(VillagerMarketShop shop, int slots) {
		return setSize(shopSetShopfrontSize, shop, slots, "shopfront");
	}

	/**
	 * Change how many slots the owner of a shop can stock.
	 * @param shop  The shop to change
	 * @param slots The new number of slots, a multiple of nine
	 * @return true when it was changed
	 */
	public boolean setStorageSize(VillagerMarketShop shop, int slots) {
		return setSize(shopSetStorageSize, shop, slots, "storage");
	}

	/**
	 * Change one of the sizes of a shop and make VillagerMarket pick it up.
	 * @param setter The VillagerMarket method that sets the size
	 * @param shop   The shop to change
	 * @param slots  The new number of slots
	 * @param what   Name of the size, for logging
	 * @return true when it was changed
	 */
	private boolean setSize(Method setter, VillagerMarketShop shop, int slots, String what) {
		if(!available || shop == null) {
			return false;
		}
		try {
			// VillagerMarket writes the new size to the shop file itself
			setter.invoke(shop.handle(), slots);
			// The open menus were built with the old size, so they have to be rebuilt
			if(shopManagerReloadAll != null) {
				shopManagerReloadAll.invoke(shopManager);
			}
			return true;
		} catch(ReflectiveOperationException | RuntimeException e) {
			AreaShop.warn("Could not change the", what, "size of VillagerMarket shop", shop.uuid() + ":", Utils.getStackTrace(e));
			return false;
		}
	}
}
