package me.wiefferink.areashop.features;

import me.wiefferink.areashop.AreaShop;
import me.wiefferink.areashop.events.notify.DeletedRegionEvent;
import me.wiefferink.areashop.events.notify.SoldRegionEvent;
import me.wiefferink.areashop.events.notify.UnrentedRegionEvent;
import me.wiefferink.areashop.integrations.VillagerMarketHook;
import me.wiefferink.areashop.integrations.VillagerMarketShop;
import me.wiefferink.areashop.regions.GeneralRegion;
import me.wiefferink.areashop.tools.Utils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Keeps the VillagerMarket shops inside a region in step with who rents it.
 *
 * <p>Without this, a shop villager keeps standing in a region after the rent runs out, still owned
 * by the player who left, and the next renter gets a stall full of someone else's goods they cannot
 * touch. What happens instead is up to the server owner, through {@code villagerMarket.onRelease}
 * in the config.
 */
public class VillagerMarketFeature extends RegionFeature {

	/** What to do with the shops in a region that goes back on the market. */
	private enum Action {
		/** Leave everything as it is. */
		NOTHING,
		/** Hand the shops back, the villager stays for the next renter. */
		ABANDON,
		/** Hand the shops back and then take the villagers away. */
		REMOVE;

		/**
		 * Read the setting from the config.
		 * @param value The configured value
		 * @return The matching action, abandoning when the value is not one of these
		 */
		static Action parse(String value) {
			if(value == null) {
				return ABANDON;
			}
			for(Action action : values()) {
				if(action.name().equalsIgnoreCase(value.trim())) {
					return action;
				}
			}
			AreaShop.warn("Unknown villagerMarket.onRelease setting:", value + ", using 'abandon'");
			return ABANDON;
		}
	}

	/**
	 * Clear the shops when a rent runs out or is given up.
	 * @param event The event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onUnrented(UnrentedRegionEvent event) {
		release(event.getRegion(), event.getOldRenter());
	}

	/**
	 * Clear the shops when a region is sold back.
	 * @param event The event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onSold(SoldRegionEvent event) {
		release(event.getRegion(), event.getOldBuyer());
	}

	/**
	 * Clear the shops when a region is taken out of AreaShop completely.
	 * @param event The event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeleted(DeletedRegionEvent event) {
		release(event.getRegion(), null);
	}

	/**
	 * Deal with the shops of a region that is going back on the market.
	 * @param region        The region that was released
	 * @param previousOwner The player that had it, may be null
	 */
	private void release(GeneralRegion region, UUID previousOwner) {
		VillagerMarketHook hook = VillagerMarketHook.getInstance();
		if(!hook.isAvailable()) {
			return;
		}

		Action action = Action.parse(plugin.getConfig().getString("villagerMarket.onRelease", "abandon"));
		if(action == Action.NOTHING) {
			return;
		}

		List<VillagerMarketShop> shops = hook.getShopsIn(region);
		if(shops.isEmpty()) {
			return;
		}

		List<String> cleared = new ArrayList<>();
		for(VillagerMarketShop shop : shops) {
			// Only shops of the player that is leaving, so an admin shop placed in the region as
			// part of the build is left alone
			if(!shouldClear(shop, previousOwner)) {
				continue;
			}

			// Always hand it back first, that is what returns the stock and the money to the owner
			boolean handedBack = hook.abandon(shop);
			boolean removed = action == Action.REMOVE && hook.remove(shop);

			if(handedBack || removed) {
				cleared.add(shop.name() == null ? shop.uuid().toString() : shop.name());
			}
		}

		if(cleared.isEmpty()) {
			return;
		}

		AreaShop.debug("Cleared", cleared.size(), "VillagerMarket shop(s) from region", region.getName(),
				"with action", action.name().toLowerCase(Locale.ROOT));
		notifyPreviousOwner(region, previousOwner, cleared);
	}

	/**
	 * Check if a shop belongs to the player that is leaving the region.
	 *
	 * <p>When the previous owner is not known, which happens when a region is deleted, every shop
	 * that belongs to a player is cleared.
	 *
	 * @param shop          The shop to check
	 * @param previousOwner The player that had the region, may be null
	 * @return true when the shop should be cleared
	 */
	private boolean shouldClear(VillagerMarketShop shop, UUID previousOwner) {
		if(!shop.hasOwner()) {
			return false;
		}
		if(previousOwner == null) {
			return true;
		}
		if(plugin.getConfig().getBoolean("villagerMarket.clearShopsOfOtherPlayers", true)) {
			return true;
		}
		return previousOwner.equals(shop.owner().getUniqueId());
	}

	/**
	 * Tell the player that lost the region what happened to their shops.
	 * @param region        The region that was released
	 * @param previousOwner The player that had it, may be null
	 * @param cleared       Names of the shops that were cleared
	 */
	private void notifyPreviousOwner(GeneralRegion region, UUID previousOwner, List<String> cleared) {
		if(previousOwner == null) {
			return;
		}
		OfflinePlayer player = Bukkit.getOfflinePlayer(previousOwner);
		if(!player.isOnline()) {
			return;
		}
		plugin.message(player.getPlayer(), "villagermarket-shopsCleared", cleared.size(),
				Utils.createCommaSeparatedList(cleared), region);
	}
}
