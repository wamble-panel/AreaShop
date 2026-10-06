package me.wiefferink.areashop.gui;

import me.wiefferink.areashop.features.SubletFeature;
import me.wiefferink.areashop.messages.Colors;
import me.wiefferink.areashop.messages.Message;
import me.wiefferink.areashop.regions.BuyRegion;
import me.wiefferink.areashop.regions.GeneralRegion;
import me.wiefferink.areashop.regions.RentRegion;
import me.wiefferink.areashop.tools.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The menu a player gets from {@code /as panel}, listing the shops they have anything to do with.
 *
 * <p>That is the ones they rent or own, the one they are renting from another player, and the ones
 * they were given access to build in. Which of the three a shop is shows on the item, and only the
 * shops they hold themselves can be managed.
 */
public class ShopsGui extends Gui {

	/** Rows used for the shops themselves, the last row holds the navigation. */
	private static final int SHOP_ROWS = 5;

	/** How many shops fit on one page. */
	private static final int PAGE_SIZE = SHOP_ROWS * ROW;

	/** The player whose shops are listed, which is not the viewer when staff look someone up. */
	private final OfflinePlayer subject;

	private List<GeneralRegion> shops = List.of();
	private int page;

	/**
	 * Construct the list of the shops of the player looking at it.
	 * @param player The player
	 */
	public ShopsGui(Player player) {
		this(player, player);
	}

	/**
	 * Construct the list of the shops of a player.
	 * @param player  The player looking at it
	 * @param subject The player whose shops to list
	 */
	public ShopsGui(Player player, OfflinePlayer subject) {
		super(player);
		this.subject = subject;
	}

	/**
	 * How the player looking at the menu is involved with a shop.
	 */
	private enum Role {
		/** They rent or own it from the server. */
		HOLDER,
		/** They rent it from the player holding it. */
		TENANT,
		/** They were given access to build in it. */
		GUEST
	}

	/**
	 * Find the shops to list.
	 *
	 * <p>Read again every time the menu is built, so a shop that was just unrented or handed over
	 * stops showing up without the player having to reopen anything.
	 *
	 * @return The shops, the ones held by the player first and then by the name they are shown under
	 */
	private List<GeneralRegion> findShops() {
		List<GeneralRegion> result = new ArrayList<>();
		for(GeneralRegion region : plugin.getFileManager().getRegions()) {
			if(roleIn(region) != null) {
				result.add(region);
			}
		}
		result.sort(Comparator.comparingInt(this::rankOf)
				.thenComparing(region -> Colors.strip(region.getDisplayName()), String.CASE_INSENSITIVE_ORDER));
		return result;
	}

	/**
	 * Get the order a shop is listed in, so the ones the player holds come first.
	 * @param region The region to rank
	 * @return A lower number for the shops that matter most to the player
	 */
	private int rankOf(GeneralRegion region) {
		Role role = roleIn(region);
		return role == null ? Role.values().length : role.ordinal();
	}

	/**
	 * Work out how the player is involved with a shop.
	 * @param region The region to check
	 * @return The role, or null when the shop does not concern them
	 */
	private Role roleIn(GeneralRegion region) {
		if(region.isDeleted()) {
			return null;
		}
		UUID id = subject.getUniqueId();
		if(region.isOwner(id)) {
			return Role.HOLDER;
		}
		// Only the player's own list shows what they rent from others, staff look up what someone holds
		if(!subject.getUniqueId().equals(player.getUniqueId())) {
			return null;
		}
		if(region.getSubletFeature().isTenant(id)) {
			return Role.TENANT;
		}
		if(region.getFriendsFeature().getFriends().contains(id)) {
			return Role.GUEST;
		}
		return null;
	}

	@Override
	protected Component title() {
		String name = subject.getName() == null ? subject.getUniqueId().toString() : subject.getName();
		return Message.fromKey(isOwnList() ? "panel-title" : "panel-titleOther").replacements(name).toComponent();
	}

	@Override
	protected int rows() {
		return SHOP_ROWS + 1;
	}

	@Override
	protected void build() {
		shops = findShops();

		int pages = Math.max(1, (shops.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		page = Math.max(0, Math.min(page, pages - 1));

		if(shops.isEmpty()) {
			set(22, Icon.of(Material.BARRIER)
					.name(isOwnList() ? "panel-noShopsName" : "panel-noShopsOtherName")
					.lore(isOwnList() ? "panel-noShopsLore" : "panel-noShopsOtherLore")
					.build());
		} else {
			int first = page * PAGE_SIZE;
			for(int slot = 0; slot < PAGE_SIZE && first + slot < shops.size(); slot++) {
				GeneralRegion region = shops.get(first + slot);
				set(slot, buildShopIcon(region), click -> new ShopGui(player, region, this).open());
			}
		}

		buildNavigation(pages);
	}

	/**
	 * Check whether the player is looking at their own shops.
	 * @return true when this is the player's own list
	 */
	private boolean isOwnList() {
		return subject.getUniqueId().equals(player.getUniqueId());
	}

	/**
	 * Build the bottom row, with the page buttons and the ways out of the menu.
	 * @param pages Total number of pages
	 */
	private void buildNavigation(int pages) {
		int row = SHOP_ROWS * ROW;

		if(page > 0) {
			set(row, Icon.of(Material.ARROW).name("panel-previousPage", page, pages).build(), click -> {
				page--;
				refresh();
			});
		}

		set(row + 2, Icon.of(Material.KNOWLEDGE_BOOK)
				.name("panel-guideName")
				.lore("panel-guideLore")
				.build(), click -> new GuideGui(player, this).open());

		set(row + 3, Icon.of(Material.COMPASS)
				.name("panel-browseName")
				.lore("panel-browseLore")
				.build(), click -> new AvailableGui(player, this).open());

		set(row + 4, Icon.of(Material.BOOK)
				.name("panel-pageStatus", page + 1, pages)
				.lore("panel-shopCount", shops.size())
				.build());

		if(plugin.getConfig().getBoolean("sublet.enabled", true)) {
			set(row + 5, Icon.of(Material.GOLD_INGOT)
					.name("panel-subletMarketName")
					.lore("panel-subletMarketLore")
					.build(), click -> new SubletMarketGui(player, this).open());
		}

		set(row + 6, Icon.of(Material.BARRIER).name("panel-close").build(), click -> player.closeInventory());

		if(page < pages - 1) {
			set(row + 8, Icon.of(Material.ARROW).name("panel-nextPage", page + 2, pages).build(), click -> {
				page++;
				refresh();
			});
		}

		fillRow(SHOP_ROWS);
	}

	/**
	 * Build the item shown for one region.
	 * @param region The region to show
	 * @return The item
	 */
	private ItemStack buildShopIcon(GeneralRegion region) {
		Role role = roleIn(region);
		Icon icon = Icon.of(iconMaterial(region, role))
				.name("panel-shopName", region)
				.lore("panel-shopLore", region);

		if(role == Role.HOLDER && region.getSubletFeature().isRentedOut()) {
			icon.lore("panel-shopRentedOut",
					SubletFeature.nameOf(region.getSubletFeature().getTenant()),
					Utils.millisToHumanFormat(region.getSubletFeature().getTimeLeft()));
		}

		icon.blank();
		if(role == Role.TENANT) {
			icon.lore("panel-shopRole-tenant",
					Utils.millisToHumanFormat(region.getSubletFeature().getTimeLeft()));
		} else if(role == Role.GUEST) {
			icon.lore("panel-shopRole-guest");
		}

		return icon.lore("panel-shopOpen").build();
	}

	/**
	 * Pick the item that shows the state of a region at a glance.
	 * @param region The region to pick for
	 * @param role   How the player is involved with it
	 * @return The material to show
	 */
	private Material iconMaterial(GeneralRegion region, Role role) {
		if(role == Role.TENANT) {
			return Material.LIME_CONCRETE;
		}
		if(role == Role.GUEST) {
			return Material.PLAYER_HEAD;
		}
		if(region instanceof RentRegion rent) {
			return isExpiringSoon(rent) ? Material.CLOCK : Material.CHEST;
		}
		if(region instanceof BuyRegion buy && buy.isInResellingMode()) {
			return Material.GOLD_INGOT;
		}
		return Material.CHEST;
	}

	/**
	 * Check if a rent runs out soon enough to warn about it.
	 * @param rent The region to check
	 * @return true when the rent is close to running out
	 */
	private boolean isExpiringSoon(RentRegion rent) {
		if(!rent.isRented()) {
			return false;
		}
		String warningSetting = rent.getStringSetting("rent.warningOnLoginTime");
		if(warningSetting == null || warningSetting.isEmpty()) {
			return false;
		}
		return rent.getTimeLeft() < Utils.durationStringToLong(warningSetting);
	}
}
