package me.wiefferink.areashop.integrations;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

/**
 * One VillagerMarket shop, read through {@link VillagerMarketHook}.
 *
 * <p>Holds a snapshot of what AreaShop needs plus the shop object itself, so the hook can act on it
 * again without looking it up a second time.
 *
 * @param handle     The VillagerMarket shop object this was read from
 * @param uuid       The uuid of the villager entity, which VillagerMarket uses as the shop id
 * @param name       The name the shop shows
 * @param location   Where the villager stands, null when the world is not loaded
 * @param owner      The player renting the shop, null for an admin shop or an unclaimed one
 * @param shopSize   Number of slots players can buy from
 * @param storageSize Number of slots the owner can stock
 */
public record VillagerMarketShop(
		Object handle,
		UUID uuid,
		String name,
		Location location,
		OfflinePlayer owner,
		int shopSize,
		int storageSize
) {

	/**
	 * Check if this shop is rented by a player, rather than being an admin shop or standing empty.
	 * @return true when a player owns it
	 */
	public boolean hasOwner() {
		return owner != null;
	}

	/**
	 * Get the name of the player renting the shop.
	 * @return The name of the owner, or null when nobody owns it
	 */
	public String ownerName() {
		return owner == null ? null : owner.getName();
	}
}
