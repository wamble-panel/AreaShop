package me.wiefferink.areashop.features;

import me.wiefferink.areashop.AreaShop;
import me.wiefferink.areashop.regions.GeneralRegion;
import me.wiefferink.areashop.tools.Utils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lets the player holding a region rent space in it out to other players.
 *
 * <p>A market is rented from the server by one player, who then sublets spots in it to the players
 * running the stalls. A subtenant gets build access for as long as they paid for, and the money goes
 * to the player who holds the region rather than to the server.
 *
 * <p>This deliberately sits next to the renting and buying of the region itself instead of inside
 * it: the region keeps one holder that the server deals with, and subletting only hands out access
 * that runs out on its own.
 */
public class SubletFeature extends RegionFeature {

	/** Where the offer and the subtenants are kept in the region file. */
	private static final String PATH = "general.sublet";

	public SubletFeature() {
	}

	public SubletFeature(GeneralRegion region) {
		setRegion(region);
	}

	/**
	 * Check if the holder of the region is offering space in it.
	 * @return true when others can rent space here
	 */
	public boolean isOffered() {
		return getRegion().getConfig().getBoolean(PATH + ".enabled", false)
				&& getPrice() >= 0
				&& getDurationMillis() > 0;
	}

	/**
	 * Get what the holder asks for a spot.
	 * @return The price, or -1 when nothing is set
	 */
	public double getPrice() {
		return getRegion().getConfig().getDouble(PATH + ".price", -1);
	}

	/**
	 * Get how long a spot is rented for, as written by the holder.
	 * @return The duration, or null when nothing is set
	 */
	public String getDuration() {
		return getRegion().getConfig().getString(PATH + ".duration");
	}

	/**
	 * Get how long a spot is rented for.
	 * @return The duration in milliseconds, or 0 when it is not a valid duration
	 */
	public long getDurationMillis() {
		String duration = getDuration();
		if(duration == null) {
			return 0;
		}
		long result = Utils.durationStringToLong(duration);
		return Math.max(0, result);
	}

	/**
	 * Start offering space in the region.
	 * @param price    What a spot costs
	 * @param duration How long a spot lasts, like '7 days'
	 */
	public void offer(double price, String duration) {
		getRegion().setSetting(PATH + ".enabled", true);
		getRegion().setSetting(PATH + ".price", price);
		getRegion().setSetting(PATH + ".duration", duration);
	}

	/**
	 * Stop offering space in the region.
	 *
	 * <p>Players that already rented a spot keep it until their time runs out, they paid for it.
	 */
	public void stopOffering() {
		getRegion().setSetting(PATH + ".enabled", false);
	}

	/**
	 * Get the players renting space, with the moment their time runs out.
	 * @return The subtenants, by uuid, with the end of their time in milliseconds since the epoch
	 */
	public Map<UUID, Long> getTenants() {
		Map<UUID, Long> result = new LinkedHashMap<>();
		ConfigurationSection section = getRegion().getConfig().getConfigurationSection(PATH + ".tenants");
		if(section == null) {
			return result;
		}
		for(String key : section.getKeys(false)) {
			try {
				result.put(UUID.fromString(key), section.getLong(key));
			} catch(IllegalArgumentException e) {
				AreaShop.warn("Region", getRegion().getName(), "has a subtenant that is not a player id:", key);
			}
		}
		return result;
	}

	/**
	 * Check if a player is renting space in the region.
	 * @param player The player to check
	 * @return true when they rented a spot that has not run out
	 */
	public boolean isTenant(UUID player) {
		Long until = getTenants().get(player);
		return until != null && until > System.currentTimeMillis();
	}

	/**
	 * Get when the time of a subtenant runs out.
	 * @param player The player to check
	 * @return The end of their time in milliseconds since the epoch, or 0 when they rent nothing
	 */
	public long getEndTime(UUID player) {
		Long until = getTenants().get(player);
		return until == null ? 0 : until;
	}

	/**
	 * Rent a spot to a player, or add time when they already have one.
	 *
	 * <p>The money goes to whoever holds the region. When nobody does, which happens for a region
	 * that is for sale again, nothing is rented out.
	 *
	 * @param player The player renting the spot
	 * @return The result of trying to rent it
	 */
	public Result rentTo(Player player) {
		GeneralRegion region = getRegion();
		if(!isOffered()) {
			return Result.NOT_OFFERED;
		}
		if(region.isOwner(player)) {
			return Result.OWN_REGION;
		}

		OfflinePlayer holder = getHolder();
		if(holder == null) {
			return Result.NO_HOLDER;
		}

		Economy economy = plugin.getEconomy();
		if(economy == null) {
			return Result.NO_ECONOMY;
		}

		double price = getPrice();
		if(price > 0 && !economy.has(player, price)) {
			return Result.NOT_ENOUGH_MONEY;
		}

		int maximum = plugin.getConfig().getInt("sublet.maxTenants", 0);
		if(maximum > 0 && !isTenant(player.getUniqueId()) && countActiveTenants() >= maximum) {
			return Result.FULL;
		}

		if(price > 0) {
			EconomyResponse taken = economy.withdrawPlayer(player, price);
			if(!taken.transactionSuccess()) {
				return Result.PAYMENT_FAILED;
			}
			EconomyResponse given = economy.depositPlayer(holder, price);
			if(!given.transactionSuccess()) {
				// Put it back, nobody should pay for something that did not arrive
				economy.depositPlayer(player, price);
				return Result.PAYMENT_FAILED;
			}
		}

		// Adding time to a spot that is still running keeps what is left of it
		long from = Math.max(System.currentTimeMillis(), getEndTime(player.getUniqueId()));
		setTenant(player.getUniqueId(), from + getDurationMillis());
		region.getFriendsFeature().addFriend(player.getUniqueId(), player);
		region.update();
		return Result.SUCCESS;
	}

	/**
	 * Take the spot of a player away, without giving anything back.
	 * @param player The player to remove
	 */
	public void remove(UUID player) {
		GeneralRegion region = getRegion();
		region.setSetting(PATH + ".tenants." + player, null);
		region.getFriendsFeature().deleteFriend(player, null);
		region.update();
	}

	/**
	 * Take away the spots that have run out.
	 * @return The players whose spot ran out
	 */
	public List<UUID> removeExpired() {
		List<UUID> expired = new ArrayList<>();
		long now = System.currentTimeMillis();
		for(Map.Entry<UUID, Long> tenant : getTenants().entrySet()) {
			if(tenant.getValue() <= now) {
				expired.add(tenant.getKey());
			}
		}

		for(UUID player : expired) {
			remove(player);
			OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player);
			if(offlinePlayer.isOnline()) {
				plugin.message(offlinePlayer.getPlayer(), "sublet-expired", getRegion());
			}
		}
		return expired;
	}

	/**
	 * Count the players that currently rent a spot.
	 * @return How many spots are taken
	 */
	public int countActiveTenants() {
		int result = 0;
		long now = System.currentTimeMillis();
		for(Long until : getTenants().values()) {
			if(until > now) {
				result++;
			}
		}
		return result;
	}

	/**
	 * Get the player that holds the region and receives the money.
	 * @return The holder, or null when nobody holds the region right now
	 */
	public OfflinePlayer getHolder() {
		UUID holder = getRegion().getOwner();
		return holder == null ? null : Bukkit.getOfflinePlayer(holder);
	}

	/**
	 * Remember until when a player rented a spot.
	 * @param player The player
	 * @param until  The end of their time in milliseconds since the epoch
	 */
	private void setTenant(UUID player, long until) {
		getRegion().setSetting(PATH + ".tenants." + player, until);
	}

	/**
	 * What came of trying to rent a spot.
	 */
	public enum Result {
		SUCCESS("sublet-success"),
		NOT_OFFERED("sublet-notOffered"),
		OWN_REGION("sublet-ownRegion"),
		NO_HOLDER("sublet-noHolder"),
		NO_ECONOMY("general-noEconomy"),
		NOT_ENOUGH_MONEY("sublet-notEnoughMoney"),
		PAYMENT_FAILED("sublet-paymentFailed"),
		FULL("sublet-full");

		private final String messageKey;

		Result(String messageKey) {
			this.messageKey = messageKey;
		}

		/**
		 * Get the message to send about this result.
		 * @return The language key of the message
		 */
		public String getMessageKey() {
			return messageKey;
		}
	}
}
