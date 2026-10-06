package me.wiefferink.areashop.gui;

import me.wiefferink.areashop.messages.Colors;
import me.wiefferink.areashop.messages.Message;
import me.wiefferink.areashop.regions.BuyRegion;
import me.wiefferink.areashop.regions.GeneralRegion;
import me.wiefferink.areashop.regions.RentRegion;
import me.wiefferink.areashop.tools.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The menu a player gets from {@code /as panel}, listing the regions they rent or own.
 */
public class ShopsGui extends Gui {

	/** Rows used for the shops themselves, the last row holds the navigation. */
	private static final int SHOP_ROWS = 5;

	/** How many shops fit on one page. */
	private static final int PAGE_SIZE = SHOP_ROWS * ROW;

	private final List<GeneralRegion> shops;
	private int page;

	public ShopsGui(Player player) {
		super(player);
		this.shops = findShops();
	}

	/**
	 * Find the regions the player rents or owns.
	 * @return The regions, sorted by the name they are shown under
	 */
	private List<GeneralRegion> findShops() {
		List<GeneralRegion> result = new ArrayList<>();
		for(GeneralRegion region : plugin.getFileManager().getRegions()) {
			if(region.isOwner(player)) {
				result.add(region);
			}
		}
		result.sort(Comparator.comparing(region -> Colors.strip(region.getDisplayName()), String.CASE_INSENSITIVE_ORDER));
		return result;
	}

	@Override
	protected Component title() {
		return Message.fromKey("panel-title").replacements(player.getName()).toComponent();
	}

	@Override
	protected int rows() {
		return SHOP_ROWS + 1;
	}

	@Override
	protected void build() {
		int pages = Math.max(1, (shops.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		page = Math.max(0, Math.min(page, pages - 1));

		if(shops.isEmpty()) {
			set(22, Icon.of(Material.BARRIER)
					.name("panel-noShopsName")
					.lore("panel-noShopsLore")
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
	 * Build the bottom row with the page buttons.
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

		set(row + 4, Icon.of(Material.BOOK)
				.name("panel-pageStatus", page + 1, pages)
				.lore("panel-shopCount", shops.size())
				.build());

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
		return Icon.of(iconMaterial(region))
				.name("panel-shopName", region)
				.lore("panel-shopLore", region)
				.blank()
				.lore("panel-shopOpen")
				.build();
	}

	/**
	 * Pick the item that shows the state of a region at a glance.
	 * @param region The region to pick for
	 * @return The material to show
	 */
	private Material iconMaterial(GeneralRegion region) {
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
