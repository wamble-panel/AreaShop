package me.wiefferink.areashop.gui;

import me.wiefferink.areashop.integrations.VillagerMarketHook;
import me.wiefferink.areashop.integrations.VillagerMarketShop;
import me.wiefferink.areashop.messages.Message;
import me.wiefferink.areashop.regions.GeneralRegion;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Comparator;
import java.util.List;

/**
 * The menu listing the VillagerMarket stalls standing inside a region.
 */
public class MarketGui extends Gui {

	/** Rows used for the stalls, the last row holds the navigation. */
	private static final int STALL_ROWS = 4;

	/** How many stalls fit on one page. */
	private static final int PAGE_SIZE = STALL_ROWS * ROW;

	private final GeneralRegion region;
	private final Gui parent;
	private int page;

	/**
	 * Construct the menu of the stalls of one region.
	 * @param player The player looking at it
	 * @param region The region to show the stalls of
	 * @param parent The menu to go back to, may be null
	 */
	public MarketGui(Player player, GeneralRegion region, Gui parent) {
		super(player);
		this.region = region;
		this.parent = parent;
	}

	@Override
	protected Component title() {
		return Message.fromKey("panel-marketTitle").replacements(region).toComponent();
	}

	@Override
	protected int rows() {
		return STALL_ROWS + 1;
	}

	@Override
	protected void build() {
		if(!mayManage(region)) {
			set(22, Icon.of(Material.BARRIER).name("panel-shopGoneName").lore("panel-shopGoneLore").build());
			buildNavigation(1);
			return;
		}

		List<VillagerMarketShop> stalls = VillagerMarketHook.getInstance().getShopsIn(region);
		stalls.sort(Comparator.comparingInt(stall -> stall.location() == null ? 0 : stall.location().getBlockX()));

		int pages = Math.max(1, (stalls.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		page = Math.max(0, Math.min(page, pages - 1));

		if(stalls.isEmpty()) {
			set(13, Icon.of(Material.BARRIER)
					.name("panel-noStallsName")
					.lore("panel-noStallsLore")
					.build());
		} else {
			int first = page * PAGE_SIZE;
			for(int slot = 0; slot < PAGE_SIZE && first + slot < stalls.size(); slot++) {
				VillagerMarketShop stall = stalls.get(first + slot);
				set(slot, buildStallIcon(stall), click -> new MarketShopGui(player, region, stall, this).open());
			}
		}

		buildNavigation(pages);
	}

	/**
	 * Build the item shown for one stall.
	 * @param stall The stall to show
	 * @return The item
	 */
	private ItemStack buildStallIcon(VillagerMarketShop stall) {
		Icon icon = Icon.of(Material.VILLAGER_SPAWN_EGG)
				.name("panel-stallName", stall.name() == null ? stall.uuid().toString() : stall.name())
				.lore("panel-stallLore", stall.shopSize(), stall.storageSize());

		if(stall.hasOwner()) {
			icon.lore("panel-stallOwner", stall.ownerName() == null ? stall.uuid().toString() : stall.ownerName());
		} else {
			icon.lore("panel-stallNoOwner");
		}

		return icon.blank().lore("panel-stallOpen").build();
	}

	/**
	 * Build the bottom row with the page buttons.
	 * @param pages Total number of pages
	 */
	private void buildNavigation(int pages) {
		int row = STALL_ROWS * ROW;

		if(page > 0) {
			set(row, Icon.of(Material.ARROW).name("panel-previousPage", page, pages).build(), click -> {
				page--;
				refresh();
			});
		}

		setBack(row + 4, parent);

		if(page < pages - 1) {
			set(row + 8, Icon.of(Material.ARROW).name("panel-nextPage", page + 2, pages).build(), click -> {
				page++;
				refresh();
			});
		}

		fillRow(STALL_ROWS);
	}
}
