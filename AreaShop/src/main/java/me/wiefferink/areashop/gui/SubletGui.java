package me.wiefferink.areashop.gui;

import me.wiefferink.areashop.features.SubletFeature;
import me.wiefferink.areashop.messages.Message;
import me.wiefferink.areashop.regions.GeneralRegion;
import me.wiefferink.areashop.tools.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * The menu where the holder of a region rents it out to another player.
 */
public class SubletGui extends Gui {

	private final GeneralRegion region;
	private final Gui parent;

	/**
	 * Construct the renting out menu of one region.
	 * @param player The player looking at it
	 * @param region The region to rent out
	 * @param parent The menu to go back to, may be null
	 */
	public SubletGui(Player player, GeneralRegion region, Gui parent) {
		super(player);
		this.region = region;
		this.parent = parent;
	}

	@Override
	protected Component title() {
		return Message.fromKey("panel-subletTitle").replacements(region).toComponent();
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

		SubletFeature sublet = region.getSubletFeature();
		buildTenant(sublet);
		buildOffer(sublet);
		buildBack();
	}

	/**
	 * Show who has the region at the moment, with the button to take it back.
	 * @param sublet The renting out of the region
	 */
	private void buildTenant(SubletFeature sublet) {
		UUID tenant = sublet.getTenant();

		if(tenant == null || !sublet.isRentedOut()) {
			set(11, Icon.of(Material.BARRIER)
					.name("panel-subletNobodyName")
					.lore("panel-subletNobodyLore")
					.build());
			return;
		}

		set(11, Icon.head(Bukkit.getOfflinePlayer(tenant))
				.name("panel-tenantName", SubletFeature.nameOf(tenant))
				.lore("panel-tenantTimeLeft", Utils.millisToHumanFormat(sublet.getTimeLeft()))
				.blank()
				.lore("panel-tenantRemove")
				.build(), click -> {
					sublet.endRental();
					plugin.message(player, "sublet-removed", SubletFeature.nameOf(tenant), region);
					refresh();
				});
	}

	/**
	 * Show whether the region is on offer, with the buttons to change that.
	 * @param sublet The renting out of the region
	 */
	private void buildOffer(SubletFeature sublet) {
		if(sublet.isOffered()) {
			set(15, Icon.of(Material.LIME_DYE)
					.name("panel-subletOnName")
					.lore("panel-subletOnLore", Utils.formatCurrency(sublet.getPrice()), sublet.getDuration())
					.build(), click -> {
						sublet.stopOffering();
						plugin.message(player, "sublet-stopped", region);
						refresh();
					});
		} else {
			set(15, Icon.of(Material.GRAY_DYE)
					.name("panel-subletOffName")
					.lore("panel-subletOffLore")
					.build(), click -> {
						player.closeInventory();
						plugin.message(player, "sublet-setPrompt", region);
					});
		}

		set(16, Icon.of(Material.NAME_TAG)
				.name("panel-subletChangeName")
				.lore("panel-subletChangeLore")
				.build(), click -> {
					player.closeInventory();
					plugin.message(player, "sublet-setPrompt", region);
				});
	}

	/**
	 * Add the button that goes back to the shop menu.
	 */
	private void buildBack() {
		if(parent != null) {
			set(22, Icon.of(Material.ARROW).name("panel-back").build(), click -> parent.open());
		}
		fillRow(2);
	}
}
