package me.wiefferink.areashop.gui;

import me.wiefferink.areashop.messages.Colors;
import me.wiefferink.areashop.messages.Message;
import me.wiefferink.areashop.regions.BuyRegion;
import me.wiefferink.areashop.regions.GeneralRegion;
import me.wiefferink.areashop.regions.RentRegion;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The menu listing the shops that nobody has yet, so a player can take one without going looking.
 *
 * <p>Renting and buying stay with the commands behind the buttons, which is what the signs use too,
 * so every limit, permission and message keeps working the same way.
 */
public class AvailableGui extends Gui {

	/** Rows used for the shops, the last row holds the navigation. */
	private static final int SHOP_ROWS = 5;

	/** How many shops fit on one page. */
	private static final int PAGE_SIZE = SHOP_ROWS * ROW;

	private final Gui parent;

	/** What kinds of shop the list shows. */
	private Filter filter = Filter.ALL;

	private List<GeneralRegion> shops = List.of();
	private int page;

	/**
	 * What the list is narrowed down to.
	 */
	private enum Filter {
		/** Everything that can be taken. */
		ALL("panel-filterAll", Material.COMPASS),
		/** Only the shops rented by the period. */
		RENT("panel-filterRent", Material.CLOCK),
		/** Only the shops bought outright. */
		BUY("panel-filterBuy", Material.EMERALD);

		private final String key;
		private final Material item;

		Filter(String key, Material item) {
			this.key = key;
			this.item = item;
		}
	}

	/**
	 * Construct the list of shops that are up for grabs.
	 * @param player The player looking at it
	 * @param parent The menu to go back to, may be null
	 */
	public AvailableGui(Player player, Gui parent) {
		super(player);
		this.parent = parent;
	}

	@Override
	protected Component title() {
		return Message.fromKey("panel-availableTitle").toComponent();
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
					.name("panel-noneAvailableName")
					.lore("panel-noneAvailableLore")
					.build());
		} else {
			int first = page * PAGE_SIZE;
			for(int slot = 0; slot < PAGE_SIZE && first + slot < shops.size(); slot++) {
				GeneralRegion region = shops.get(first + slot);
				set(slot, buildIcon(region), click -> take(region));
			}
		}

		buildNavigation(pages);
	}

	/**
	 * Find the shops that nobody holds at the moment.
	 * @return The shops, cheapest first
	 */
	private List<GeneralRegion> findShops() {
		List<GeneralRegion> result = new ArrayList<>();

		if(filter != Filter.BUY) {
			for(RentRegion region : plugin.getFileManager().getRents()) {
				if(!region.isDeleted() && !region.isRented()) {
					result.add(region);
				}
			}
		}
		if(filter != Filter.RENT) {
			for(BuyRegion region : plugin.getFileManager().getBuys()) {
				// A shop a player put back up for sale counts as available too
				if(!region.isDeleted() && (!region.isSold() || region.isInResellingMode())) {
					result.add(region);
				}
			}
		}

		result.sort(Comparator.<GeneralRegion>comparingDouble(AvailableGui::priceOf)
				.thenComparing(region -> Colors.strip(region.getDisplayName()), String.CASE_INSENSITIVE_ORDER));
		return result;
	}

	/**
	 * Get what a shop costs, used to put the cheap ones first.
	 * @param region The region to price
	 * @return The price, or 0 when it has none
	 */
	private static double priceOf(GeneralRegion region) {
		if(region instanceof RentRegion rent) {
			return rent.getPrice();
		}
		if(region instanceof BuyRegion buy) {
			return buy.isInResellingMode() ? buy.getResellPrice() : buy.getPrice();
		}
		return 0;
	}

	/**
	 * Build the item shown for one shop.
	 * @param region The region to show
	 * @return The item
	 */
	private ItemStack buildIcon(GeneralRegion region) {
		boolean rent = region instanceof RentRegion;
		boolean resell = region instanceof BuyRegion buy && buy.isInResellingMode();

		String loreKey;
		if(rent) {
			loreKey = "panel-availableRentLore";
		} else if(resell) {
			loreKey = "panel-availableResellLore";
		} else {
			loreKey = "panel-availableBuyLore";
		}

		Icon icon = Icon.of(rent ? Material.CLOCK : resell ? Material.GOLD_INGOT : Material.EMERALD)
				.name("panel-availableName", region)
				.lore(loreKey, region)
				.blank();

		if(!player.hasPermission(rent ? "areashop.rent" : "areashop.buy")) {
			return icon.lore("panel-availableNoPermission").build();
		}
		return icon.lore(rent ? "panel-availableRentClick" : "panel-availableBuyClick").build();
	}

	/**
	 * Try to take a shop for the player, by running the command the signs run.
	 * @param region The region to take
	 */
	private void take(GeneralRegion region) {
		if(region instanceof RentRegion) {
			runCommand("areashop rent " + region.getName());
			return;
		}
		runCommand("areashop buy " + region.getName());
	}

	/**
	 * Build the bottom row with the page buttons and the filter.
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

		set(row + 2, Icon.of(filter.item)
				.name("panel-filterName")
				.lore(filter.key)
				.blank()
				.lore("panel-filterChange")
				.build(), click -> {
					Filter[] all = Filter.values();
					filter = all[(filter.ordinal() + 1) % all.length];
					page = 0;
					refresh();
				});

		set(row + 4, Icon.of(Material.BOOK)
				.name("panel-pageStatus", page + 1, pages)
				.lore("panel-availableCount", shops.size())
				.build());

		setBack(row + 6, parent);

		if(page < pages - 1) {
			set(row + 8, Icon.of(Material.ARROW).name("panel-nextPage", page + 2, pages).build(), click -> {
				page++;
				refresh();
			});
		}

		fillRow(SHOP_ROWS);
	}
}
