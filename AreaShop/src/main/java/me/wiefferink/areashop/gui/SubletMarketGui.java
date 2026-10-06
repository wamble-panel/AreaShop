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
 * The menu listing the regions whose holder is renting space in them out.
 */
public class SubletMarketGui extends Gui {

	/** Rows used for the offers, the last row holds the navigation. */
	private static final int OFFER_ROWS = 5;

	/** How many offers fit on one page. */
	private static final int PAGE_SIZE = OFFER_ROWS * ROW;

	private int page;

	public SubletMarketGui(Player player) {
		super(player);
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
		List<GeneralRegion> offers = findOffers();
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
	 * Find the regions that are renting space out to others right now.
	 * @return The regions offering space, the ones the player already rents space in first
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
	 * Try to rent a spot in a region for the player.
	 * @param region The region to rent space in
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

		set(row + 4, Icon.of(Material.BOOK).name("panel-pageStatus", page + 1, pages).build());

		if(page < pages - 1) {
			set(row + 8, Icon.of(Material.ARROW).name("panel-nextPage", page + 2, pages).build(), click -> {
				page++;
				refresh();
			});
		}

		fillRow(OFFER_ROWS);
	}
}
