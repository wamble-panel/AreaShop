package me.wiefferink.areashop.gui;

import me.wiefferink.areashop.features.SubletFeature;
import me.wiefferink.areashop.messages.Message;
import me.wiefferink.areashop.regions.GeneralRegion;
import me.wiefferink.areashop.tools.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The menu listing the shops whose holder is renting the whole of them out to another player.
 */
public class SubletMarketGui extends Gui {

	/** Rows used for the offers, the last row holds the navigation. */
	private static final int OFFER_ROWS = 5;

	/** How many offers fit on one page. */
	private static final int PAGE_SIZE = OFFER_ROWS * ROW;

	private final Gui parent;

	private List<GeneralRegion> offers = List.of();
	private int page;

	/**
	 * Construct the list of shops players are renting out.
	 * @param player The player looking at it
	 */
	public SubletMarketGui(Player player) {
		this(player, null);
	}

	/**
	 * Construct the list of shops players are renting out.
	 * @param player The player looking at it
	 * @param parent The menu to go back to, may be null
	 */
	public SubletMarketGui(Player player, Gui parent) {
		super(player);
		this.parent = parent;
	}

	@Override
	protected Component title() {
		return Message.fromKey("panel-subletMarketTitle").toComponent();
	}

	@Override
	protected int rows() {
		return OFFER_ROWS + 1;
	}

	@Override
	protected void build() {
		offers = findOffers();
		int pages = Math.max(1, (offers.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		page = Math.max(0, Math.min(page, pages - 1));

		if(offers.isEmpty()) {
			set(22, Icon.of(Material.BARRIER)
					.name("panel-noOffersName")
					.lore("panel-noOffersLore")
					.build());
		} else {
			int first = page * PAGE_SIZE;
			for(int slot = 0; slot < PAGE_SIZE && first + slot < offers.size(); slot++) {
				GeneralRegion region = offers.get(first + slot);
				set(slot, buildOfferIcon(region), click -> rent(region));
			}
		}

		buildNavigation(pages);
	}

	/**
	 * Find the shops that are up for rent from their holder right now.
	 * @return The shops on offer, cheapest first, including the one the player already rents
	 */
	private List<GeneralRegion> findOffers() {
		List<GeneralRegion> result = new ArrayList<>();
		for(GeneralRegion region : plugin.getFileManager().getRegions()) {
			SubletFeature sublet = region.getSubletFeature();
			if(region.isOwner(player)) {
				continue;
			}
			// What you already rent stays visible so you can add time to it
			if(sublet.isAvailable() || sublet.isTenant(player.getUniqueId())) {
				result.add(region);
			}
		}
		result.sort(Comparator.comparingDouble(region -> region.getSubletFeature().getPrice()));
		return result;
	}

	/**
	 * Build the item shown for one offer.
	 * @param region The region offering space
	 * @return The item
	 */
	private ItemStack buildOfferIcon(GeneralRegion region) {
		SubletFeature sublet = region.getSubletFeature();
		boolean mine = sublet.isTenant(player.getUniqueId());

		Icon icon = Icon.of(mine ? Material.LIME_CONCRETE : Material.CHEST)
				.name("panel-offerName", region)
				.lore("panel-offerLore", Utils.formatCurrency(sublet.getPrice()), sublet.getDuration(),
						sublet.getHolder() == null ? "?" : sublet.getHolder().getName());

		if(mine) {
			icon.blank().lore("panel-offerAlreadyIn", Utils.millisToHumanFormat(sublet.getTimeLeft()));
		}

		return icon.blank().lore(mine ? "panel-offerExtend" : "panel-offerRent").build();
	}

	/**
	 * Try to rent a shop from its holder for the player.
	 * @param region The shop to rent
	 */
	private void rent(GeneralRegion region) {
		if(!player.hasPermission("areashop.subrent")) {
			plugin.message(player, "sublet-noPermissionRent");
			return;
		}

		SubletFeature.Result result = region.getSubletFeature().rentTo(player);
		if(result == SubletFeature.Result.SUCCESS) {
			plugin.message(player, result.getMessageKey(), region,
					Utils.formatCurrency(region.getSubletFeature().getPrice()),
					region.getSubletFeature().getDuration());
		} else {
			plugin.message(player, result.getMessageKey(), region);
		}
		refresh();
	}

	/**
	 * Build the bottom row with the page buttons.
	 * @param pages Total number of pages
	 */
	private void buildNavigation(int pages) {
		int row = OFFER_ROWS * ROW;

		if(page > 0) {
			set(row, Icon.of(Material.ARROW).name("panel-previousPage", page, pages).build(), click -> {
				page--;
				refresh();
			});
		}

		set(row + 4, Icon.of(Material.BOOK)
				.name("panel-pageStatus", page + 1, pages)
				.lore("panel-availableCount", offers.size())
				.build());

		setBack(row + 6, parent);

		if(page < pages - 1) {
			set(row + 8, Icon.of(Material.ARROW).name("panel-nextPage", page + 2, pages).build(), click -> {
				page++;
				refresh();
			});
		}

		fillRow(OFFER_ROWS);
	}
}
