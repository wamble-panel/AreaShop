package me.wiefferink.areashop.features;

import me.wiefferink.areashop.regions.GeneralRegion;
import me.wiefferink.areashop.tools.Utils;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Lets the player holding a region rent the whole of it out to one other player.
 *
 * <p>A market is rented or bought from the server by one player, who can then hand it over to
 * someone else for a while and be paid for it. The region is let out as a whole: there is one
 * subtenant at a time, who has the run of the place until their time is up.
 *
 * <p>This sits next to the renting and buying of the region instead of inside it. The server keeps
 * dealing with one holder, who stays responsible for the rent or the purchase, and subletting only
 * hands out access that runs out on its own.
 */
public class SubletFeature extends RegionFeature {

	/** Where the offer and the subtenant are kept in the region file. */
	private static final String PATH = "general.sublet";

	public SubletFeature() {
	}

	public SubletFeature(GeneralRegion region) {
		setRegion(region);
	}

	/**
	 * Check if the holder is offering the region to other players.
	 * @return true when someone could rent it right now or when it is already rented out
	 */
	public boolean isOffered() {
		return getRegion().getConfig().getBoolean(PATH + ".enabled", false)
				&& getPrice() >= 0
				&& getDurationMillis() > 0;
	}

	/**
	 * Check if another player has the region at the moment.
	 * @return true when there is a subtenant whose time has not run out
	 */
	public boolean isRentedOut() {
		return getTenant() != null && getEndTime() > System.currentTimeMillis();
	}

	/**
	 * Check if the region can be taken by someone right now.
	 * @return true when it is offered and nobody has it
	 */
	public boolean isAvailable() {
		return isOffered() && !isRentedOut();
	}

	/**
	 * Get what the holder asks for the region.
	 * @return The price, or -1 when nothing is set
	 */
	public double getPrice() {
		return getRegion().getConfig().getDouble(PATH + ".price", -1);
	}

	/**
	 * Get how long the region is let out for, as written by the holder.
	 * @return The duration, or null when nothing is set
	 */
	public String getDuration() {
		return getRegion().getConfig().getString(PATH + ".duration");
	}

	/**
	 * Get how long the region is let out for.
	 * @return The duration in milliseconds, or 0 when it is not a valid duration
	 */
	public long getDurationMillis() {
		String duration = getDuration();
		if(duration == null) {
			return 0;
		}
		return Math.max(0, Utils.durationStringToLong(duration));
	}

	/**
	 * Start offering the region to other players.
	 * @param price    What renting it costs
	 * @param duration How long a rental lasts, like '7 days'
	 */
	public void offer(double price, String duration) {
		getRegion().setSetting(PATH + ".enabled", true);
		getRegion().setSetting(PATH + ".price", price);
		getRegion().setSetting(PATH + ".duration", duration);
	}

	/**
	 * Stop offering the region to other players.
	 *
	 * <p>Whoever has it keeps it until their time is up, they paid for it.
	 */
	public void stopOffering() {
		getRegion().setSetting(PATH + ".enabled", false);
	}

	/**
	 * Get the player that has the region at the moment.
	 * @return The subtenant, or null when nobody has it
	 */
	public UUID getTenant() {
		String stored = getRegion().getConfig().getString(PATH + ".tenant");
		if(stored == null) {
			return null;
		}
		try {
			return UUID.fromString(stored);
		} catch(IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * Get when the time of the subtenant runs out.
	 * @return The end of their time in milliseconds since the epoch, or 0 when nobody has the region
	 */
	public long getEndTime() {
		return getRegion().getConfig().getLong(PATH + ".until", 0);
	}

	/**
	 * Get how long the subtenant has left.
	 * @return The time left in milliseconds, or 0 when nobody has the region
	 */
	public long getTimeLeft() {
		return Math.max(0, getEndTime() - System.currentTimeMillis());
	}

	/**
	 * Check if a player is the one renting the region.
	 * @param player The player to check
	 * @return true when they have it and their time has not run out
	 */
	public boolean isTenant(UUID player) {
		return player != null && player.equals(getTenant()) && getEndTime() > System.currentTimeMillis();
	}

	/**
	 * Rent the region to a player, or add time when they already have it.
	 *
	 * <p>The money goes to whoever holds the region. Only one player can have it at a time, so this
	 * turns anyone else away until the current rental is over.
	 *
	 * @param player The player renting the region
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
		if(isRentedOut() && !isTenant(player.getUniqueId())) {
			return Result.ALREADY_RENTED;
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

		// Paying again before the time is up adds to what is left instead of replacing it
		long from = Math.max(System.currentTimeMillis(), isTenant(player.getUniqueId()) ? getEndTime() : 0);
		region.setSetting(PATH + ".tenant", player.getUniqueId().toString());
		region.setSetting(PATH + ".until", from + getDurationMillis());
		region.getFriendsFeature().addFriend(player.getUniqueId(), player);
		region.update();
		return Result.SUCCESS;
	}

	/**
	 * Take the region back from whoever has it.
	 * @return The player it was taken from, or null when nobody had it
	 */
	public UUID endRental() {
		UUID tenant = getTenant();
		if(tenant == null) {
			return null;
		}

		GeneralRegion region = getRegion();
		region.setSetting(PATH + ".tenant", null);
		region.setSetting(PATH + ".until", null);
		region.getFriendsFeature().deleteFriend(tenant, null);
		region.update();
		return tenant;
	}

	/**
	 * Take the region back when the time of the subtenant has run out.
	 * @return true when a rental was ended
	 */
	public boolean removeExpired() {
		UUID tenant = getTenant();
		if(tenant == null || getEndTime() > System.currentTimeMillis()) {
			return false;
		}

		endRental();
		OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(tenant);
		if(offlinePlayer.isOnline()) {
			plugin.message(offlinePlayer.getPlayer(), "sublet-expired", getRegion());
		}

		OfflinePlayer holder = getHolder();
		if(holder != null && holder.isOnline()) {
			plugin.message(holder.getPlayer(), "sublet-expiredHolder", nameOf(tenant), getRegion());
		}
		return true;
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
	 * Get a readable name for a player.
	 * @param player The player to name
	 * @return Their name, or their id when the server does not know it
	 */
	public static String nameOf(UUID player) {
		if(player == null) {
			return "";
		}
		String name = Bukkit.getOfflinePlayer(player).getName();
		return name == null ? player.toString() : name;
	}

	/**
	 * What came of trying to rent a region.
	 */
	public enum Result {
		SUCCESS("sublet-success"),
		NOT_OFFERED("sublet-notOffered"),
		OWN_REGION("sublet-ownRegion"),
		ALREADY_RENTED("sublet-alreadyRented"),
		NO_HOLDER("sublet-noHolder"),
		NO_ECONOMY("general-noEconomy"),
		NOT_ENOUGH_MONEY("sublet-notEnoughMoney"),
		PAYMENT_FAILED("sublet-paymentFailed");

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
