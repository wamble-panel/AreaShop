package me.wiefferink.areashop.gui;

import me.wiefferink.areashop.features.SubletFeature;
import me.wiefferink.areashop.integrations.VillagerMarketHook;
import me.wiefferink.areashop.messages.Message;
import me.wiefferink.areashop.regions.BuyRegion;
import me.wiefferink.areashop.regions.GeneralRegion;
import me.wiefferink.areashop.regions.RentRegion;
import me.wiefferink.areashop.tools.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * The menu for one shop, with everything its holder can do to it.
 *
 * <p>Laid out in three bands: the shop itself at the top, the buttons that change nothing but what
 * the shop is like in the middle, and the ones that cost or return money below them. Players that
 * only have access to a shop, rather than holding it, get the top band and the way in.
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
		return 6;
	}

	@Override
	protected void build() {
		// The shop is gone or was taken over while the menu was open
		if(!maySee(region)) {
			set(22, Icon.of(Material.BARRIER).name("panel-shopGoneName").lore("panel-shopGoneLore").build());
			setBack(49, parent);
			fillRest();
			return;
		}

		buildHeader();
		buildTeleport();

		if(mayManage(region)) {
			buildFriends();
			buildSettings();
			buildMarket();
			buildSublet();
			buildName();
			buildMoneyActions();
		} else {
			set(22, Icon.of(Material.PAPER)
					.name("panel-viewOnlyName")
					.lore("panel-viewOnlyLore")
					.build());
		}

		setBack(49, parent);
		fillRest();
	}

	/**
	 * Add the book at the top that describes the shop.
	 */
	private void buildHeader() {
		Icon icon = Icon.of(Material.WRITABLE_BOOK)
				.name("panel-shopName", region)
				.lore("panel-shopLore", region);

		SubletFeature sublet = region.getSubletFeature();
		if(sublet.isRentedOut()) {
			icon.blank().lore("panel-shopRentedOut", SubletFeature.nameOf(sublet.getTenant()),
					Utils.millisToHumanFormat(sublet.getTimeLeft()));
		}

		set(4, icon.build());
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
	 * Add the button that opens the subletting menu, where the holder rents the whole shop out to
	 * another player.
	 */
	private void buildSublet() {
		if(!player.hasPermission("areashop.sublet") || !plugin.getConfig().getBoolean("sublet.enabled", true)) {
			return;
		}
		SubletFeature sublet = region.getSubletFeature();
		String lore;
		if(sublet.isRentedOut()) {
			lore = "panel-subletLoreRented";
		} else if(sublet.isOffered()) {
			lore = "panel-subletLoreOn";
		} else {
			lore = "panel-subletLoreOff";
		}
		set(23, Icon.of(Material.GOLD_INGOT)
				.name("panel-subletName")
				.lore(lore, SubletFeature.nameOf(sublet.getTenant()), Utils.millisToHumanFormat(sublet.getTimeLeft()))
				.build(), click -> new SubletGui(player, region, this).open());
	}

	/**
	 * Add the button that renames the shop, which only the staff that may use '/as setname' get.
	 */
	private void buildName() {
		if(!player.hasPermission("areashop.setname")) {
			return;
		}
		Icon icon = Icon.of(Material.NAME_TAG)
				.name("panel-nameName")
				.lore(region.hasDisplayName() ? "panel-nameLoreSet" : "panel-nameLoreUnset", region);
		set(24, icon.build(), click -> {
			player.closeInventory();
			plugin.message(player, "panel-namePrompt", region);
		});
	}

	/**
	 * Add the buttons that cost or return money, which run the matching command so that every
	 * permission, limit and confirmation keeps working exactly as it does in chat.
	 */
	private void buildMoneyActions() {
		if(region instanceof RentRegion rent) {
			if(rent.isRented() && player.hasPermission("areashop.rent")) {
				set(30, Icon.of(Material.CLOCK)
						.name("panel-extendName")
						.lore("panel-extendLore", rent)
						.build(), click -> runCommand("areashop rent " + region.getName()));
			}
			if(rent.isRented() && player.hasPermission("areashop.unrentown")) {
				set(32, Icon.of(Material.BARRIER)
						.name("panel-unrentName")
						.lore("panel-unrentLore", rent)
						.build(), click -> runCommand("areashop unrent " + region.getName()));
			}
			return;
		}

		if(region instanceof BuyRegion buy && buy.isSold()) {
			if(buy.isInResellingMode()) {
				if(player.hasPermission("areashop.stopresell")) {
					set(30, Icon.of(Material.GOLD_INGOT)
							.name("panel-stopResellName")
							.lore("panel-stopResellLore", buy)
							.build(), click -> runCommand("areashop stopresell " + region.getName()));
				}
			} else if(player.hasPermission("areashop.resell")) {
				// Reselling needs a price, which a menu cannot ask for, so the command is offered
				// in chat with everything but the number already filled in
				set(30, Icon.of(Material.GOLD_INGOT)
						.name("panel-resellName")
						.lore("panel-resellLore", buy)
						.build(), click -> {
							player.closeInventory();
							plugin.message(player, "panel-resellPrompt", region);
						});
			}

			if(player.hasPermission("areashop.sellown")) {
				set(32, Icon.of(Material.BARRIER)
						.name("panel-sellName")
						.lore("panel-sellLore", buy)
						.build(), click -> runCommand("areashop sell " + region.getName()));
			}
		}
	}
}
