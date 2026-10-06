package me.wiefferink.areashop.gui;

import me.wiefferink.areashop.integrations.VillagerMarketHook;
import me.wiefferink.areashop.messages.Message;
import me.wiefferink.areashop.regions.BuyRegion;
import me.wiefferink.areashop.regions.GeneralRegion;
import me.wiefferink.areashop.regions.RentRegion;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * The menu for one shop, with everything its owner can do to it.
 */
public class ShopGui extends Gui {

	private final GeneralRegion region;
	private final Gui parent;

	/**
	 * Construct the menu of one shop.
	 * @param player The player looking at it
	 * @param region The region to show
	 * @param parent The menu to go back to, may be null
	 */
	public ShopGui(Player player, GeneralRegion region, Gui parent) {
		super(player);
		this.region = region;
		this.parent = parent;
	}

	@Override
	protected Component title() {
		return Message.fromKey("panel-shopTitle").replacements(region).toComponent();
	}

	@Override
	protected int rows() {
		return 5;
	}

	@Override
	protected void build() {
		// The region is gone or was taken over while the menu was open
		if(region.isDeleted() || !region.isOwner(player)) {
			set(22, Icon.of(Material.BARRIER).name("panel-shopGoneName").lore("panel-shopGoneLore").build());
			buildBack();
			return;
		}

		set(4, Icon.of(Material.WRITABLE_BOOK)
				.name("panel-shopName", region)
				.lore("panel-shopLore", region)
				.build());

		buildTeleport();
		buildFriends();
		buildSettings();
		buildMarket();
		buildMoneyActions();
		buildBack();
	}

	/**
	 * Add the button that teleports to the shop.
	 */
	private void buildTeleport() {
		if(!player.hasPermission("areashop.teleport") && !player.hasPermission("areashop.teleportall")) {
			return;
		}
		set(19, Icon.of(Material.ENDER_PEARL)
				.name("panel-teleportName")
				.lore("panel-teleportLore")
				.build(), click -> runCommand("areashop teleport " + region.getName()));
	}

	/**
	 * Add the button that opens the friends menu.
	 */
	private void buildFriends() {
		if(!player.hasPermission("areashop.addfriend") && !player.hasPermission("areashop.delfriend")) {
			return;
		}
		set(20, Icon.of(Material.PLAYER_HEAD)
				.name("panel-friendsName")
				.lore("panel-friendsLore", region.getFriendsFeature().getFriends().size())
				.build(), click -> new FriendsGui(player, region, this).open());
	}

	/**
	 * Add the button that opens the settings menu, when the server offers any.
	 */
	private void buildSettings() {
		if(!player.hasPermission("areashop.panel.settings") || PanelFlag.all().isEmpty()) {
			return;
		}
		set(21, Icon.of(Material.COMPARATOR)
				.name("panel-settingsName")
				.lore("panel-settingsLore")
				.build(), click -> new FlagsGui(player, region, this).open());
	}

	/**
	 * Add the button that opens the VillagerMarket stalls of the region, when that plugin is around
	 * and there is anything standing in the region.
	 */
	private void buildMarket() {
		if(!player.hasPermission("areashop.panel.market") || !VillagerMarketHook.getInstance().isAvailable()) {
			return;
		}
		int stalls = VillagerMarketHook.getInstance().getShopsIn(region).size();
		if(stalls == 0) {
			return;
		}
		set(22, Icon.of(Material.VILLAGER_SPAWN_EGG)
				.name("panel-marketName")
				.lore("panel-marketLore", stalls)
				.build(), click -> new MarketGui(player, region, this).open());
	}

	/**
	 * Add the buttons that cost or return money, which run the matching command so that every
	 * permission, limit and confirmation keeps working exactly as it does in chat.
	 */
	private void buildMoneyActions() {
		if(region instanceof RentRegion rent) {
			if(rent.isRented() && player.hasPermission("areashop.rent")) {
				set(23, Icon.of(Material.CLOCK)
						.name("panel-extendName")
						.lore("panel-extendLore", rent)
						.build(), click -> runCommand("areashop rent " + region.getName()));
			}
			if(rent.isRented() && player.hasPermission("areashop.unrentown")) {
				set(24, Icon.of(Material.BARRIER)
						.name("panel-unrentName")
						.lore("panel-unrentLore", rent)
						.build(), click -> runCommand("areashop unrent " + region.getName()));
			}
			return;
		}

		if(region instanceof BuyRegion buy && buy.isSold()) {
			if(buy.isInResellingMode()) {
				if(player.hasPermission("areashop.stopresell")) {
					set(23, Icon.of(Material.GOLD_INGOT)
							.name("panel-stopResellName")
							.lore("panel-stopResellLore", buy)
							.build(), click -> runCommand("areashop stopresell " + region.getName()));
				}
			} else if(player.hasPermission("areashop.resell")) {
				// Reselling needs a price, which a menu cannot ask for, so the command is offered
				// in chat with everything but the number already filled in
				set(23, Icon.of(Material.GOLD_INGOT)
						.name("panel-resellName")
						.lore("panel-resellLore", buy)
						.build(), click -> {
							player.closeInventory();
							plugin.message(player, "panel-resellPrompt", region);
						});
			}

			if(player.hasPermission("areashop.sellown")) {
				set(24, Icon.of(Material.BARRIER)
						.name("panel-sellName")
						.lore("panel-sellLore", buy)
						.build(), click -> runCommand("areashop sell " + region.getName()));
			}
		}
	}

	/**
	 * Add the button that goes back to the list of shops.
	 */
	private void buildBack() {
		if(parent == null) {
			return;
		}
		set(40, Icon.of(Material.ARROW).name("panel-back").build(), click -> parent.open());
		fillRow(4);
	}
}
