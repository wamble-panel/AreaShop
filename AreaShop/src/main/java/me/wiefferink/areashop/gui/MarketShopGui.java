package me.wiefferink.areashop.gui;

import me.wiefferink.areashop.integrations.VillagerMarketHook;
import me.wiefferink.areashop.integrations.VillagerMarketShop;
import me.wiefferink.areashop.messages.Message;
import me.wiefferink.areashop.regions.GeneralRegion;
import me.wiefferink.areashop.tools.Utils;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * The menu for one VillagerMarket stall, where the owner of the region makes it bigger.
 *
 * <p>A stall has two sizes: the shopfront customers buy from, and the storage its owner stocks.
 * Both go up a row at a time, for a price the server owner sets.
 */
public class MarketShopGui extends Gui {

	/** A chest row, the step every upgrade moves in. */
	private static final int SLOTS_PER_ROW = 9;

	private final GeneralRegion region;
	private final Gui parent;
	private VillagerMarketShop stall;

	/**
	 * Construct the menu of one stall.
	 * @param player The player looking at it
	 * @param region The region the stall stands in
	 * @param stall  The stall to show
	 * @param parent The menu to go back to, may be null
	 */
	public MarketShopGui(Player player, GeneralRegion region, VillagerMarketShop stall, Gui parent) {
		super(player);
		this.region = region;
		this.stall = stall;
		this.parent = parent;
	}

	@Override
	protected Component title() {
		return Message.fromKey("panel-stallTitle")
				.replacements(stall.name() == null ? stall.uuid().toString() : stall.name())
				.toComponent();
	}

	@Override
	protected int rows() {
		return 3;
	}

	@Override
	protected void build() {
		if(region.isDeleted() || !region.isOwner(player)) {
			set(13, Icon.of(Material.BARRIER).name("panel-shopGoneName").lore("panel-shopGoneLore").build());
			buildBack();
			return;
		}

		set(4, Icon.of(Material.VILLAGER_SPAWN_EGG)
				.name("panel-stallName", stall.name() == null ? stall.uuid().toString() : stall.name())
				.lore("panel-stallLore", stall.shopSize(), stall.storageSize())
				.build());

		buildUpgrade(11, "shopfront", stall.shopSize(), VillagerMarketHook::setShopSize);
		buildUpgrade(15, "storage", stall.storageSize(), VillagerMarketHook::setStorageSize);

		buildBack();
	}

	/**
	 * Add the button that makes one of the sizes of the stall bigger.
	 * @param slot    Where to put the button
	 * @param what    Which size this is, used for the config and the messages
	 * @param current The current number of slots
	 * @param apply   What to call on VillagerMarket to set the new size
	 */
	private void buildUpgrade(int slot, String what, int current, SizeSetter apply) {
		if(!plugin.getConfig().getBoolean("villagerMarket.upgrades.enabled", true)) {
			return;
		}

		int maximum = plugin.getConfig().getInt("villagerMarket.upgrades." + what + ".maxSlots", 54);
		double price = plugin.getConfig().getDouble("villagerMarket.upgrades." + what + ".pricePerRow", 0);
		int next = current + SLOTS_PER_ROW;

		if(next > maximum) {
			set(slot, Icon.of(Material.BARRIER)
					.name("panel-upgrade" + capitalize(what) + "Name")
					.lore("panel-upgradeMaxed", current)
					.build());
			return;
		}

		set(slot, Icon.of(Material.ANVIL)
				.name("panel-upgrade" + capitalize(what) + "Name")
				.lore("panel-upgradeLore", current, next, Utils.formatCurrency(price))
				.build(), click -> upgrade(what, next, price, apply));
	}

	/**
	 * Charge the player and make the stall bigger.
	 * @param what  Which size this is
	 * @param next  The number of slots to go to
	 * @param price What it costs
	 * @param apply What to call on VillagerMarket to set the new size
	 */
	private void upgrade(String what, int next, double price, SizeSetter apply) {
		if(!player.hasPermission("areashop.panel.market")) {
			plugin.message(player, "panel-marketNoPermission");
			return;
		}

		VillagerMarketHook hook = VillagerMarketHook.getInstance();
		if(!hook.isAvailable()) {
			plugin.message(player, "panel-marketUnavailable");
			player.closeInventory();
			return;
		}

		Economy economy = plugin.getEconomy();
		if(economy == null) {
			plugin.message(player, "general-noEconomy");
			return;
		}
		if(price > 0 && !economy.has(player, price)) {
			plugin.message(player, "panel-upgradeNotEnoughMoney", Utils.formatCurrency(price));
			return;
		}

		// Change the stall first, so nobody is charged for an upgrade that did not happen
		if(!apply.set(hook, stall, next)) {
			plugin.message(player, "panel-upgradeFailed");
			return;
		}

		if(price > 0) {
			EconomyResponse response = economy.withdrawPlayer(player, price);
			if(!response.transactionSuccess()) {
				// Put the stall back the way it was, the player has not paid for it
				apply.set(hook, stall, next - SLOTS_PER_ROW);
				plugin.message(player, "panel-upgradeFailed");
				return;
			}
		}

		plugin.message(player, "panel-upgraded" + capitalize(what), next, Utils.formatCurrency(price));
		refreshStall();
	}

	/**
	 * Read the stall again, so the menu shows the new sizes.
	 */
	private void refreshStall() {
		List<VillagerMarketShop> stalls = VillagerMarketHook.getInstance().getShopsIn(region);
		for(VillagerMarketShop candidate : stalls) {
			if(candidate.uuid().equals(stall.uuid())) {
				stall = candidate;
				break;
			}
		}
		refresh();
	}

	/**
	 * Add the button that goes back to the list of stalls.
	 */
	private void buildBack() {
		if(parent == null) {
			return;
		}
		set(22, Icon.of(Material.ARROW).name("panel-back").build(), click -> parent.open());
		fillRow(2);
	}

	/**
	 * Make the first letter of a word uppercase, to build a language key from a config name.
	 * @param word The word to change
	 * @return The word with its first letter in uppercase
	 */
	private static String capitalize(String word) {
		return Character.toUpperCase(word.charAt(0)) + word.substring(1);
	}

	/**
	 * One of the two size setters of VillagerMarket.
	 */
	@FunctionalInterface
	private interface SizeSetter {
		/**
		 * Set a size of a stall.
		 * @param hook  The hook to call through
		 * @param shop  The stall to change
		 * @param slots The new number of slots
		 * @return true when it was changed
		 */
		boolean set(VillagerMarketHook hook, VillagerMarketShop shop, int slots);
	}
}
