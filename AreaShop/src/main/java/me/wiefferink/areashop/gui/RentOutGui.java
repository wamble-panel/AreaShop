package me.wiefferink.areashop.gui;

import me.wiefferink.areashop.features.RentOutFeature;
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
public class RentOutGui extends Gui {

	private final GeneralRegion region;
	private final Gui parent;

	/**
	 * Construct the renting out menu of one region.
	 * @param player The player looking at it
	 * @param region The region to rent out
	 * @param parent The menu to go back to, may be null
	 */
	public RentOutGui(Player player, GeneralRegion region, Gui parent) {
		super(player);
		this.region = region;
		this.parent = parent;
	}

	@Override
	protected Component title() {
		return Message.fromKey("panel-rentOutTitle").replacements(region).toComponent();
	}

	@Override
	protected int rows() {
		return 3;
	}

	@Override
	protected void build() {
		if(!mayManage(region)) {
			set(13, Icon.of(Material.BARRIER).name("panel-shopGoneName").lore("panel-shopGoneLore").build());
			buildBack();
			return;
		}

		RentOutFeature rentOut = region.getRentOutFeature();
		buildRenter(rentOut);
		buildOffer(rentOut);
		buildBack();
	}

	/**
	 * Show who has the region at the moment, with the button to take it back.
	 * @param rentOut The renting out of the region
	 */
	private void buildRenter(RentOutFeature rentOut) {
		UUID renter = rentOut.getRenter();

		if(renter == null || !rentOut.isRentedOut()) {
			set(11, Icon.of(Material.BARRIER)
					.name("panel-rentOutNobodyName")
					.lore("panel-rentOutNobodyLore")
					.build());
			return;
		}

		set(11, Icon.head(Bukkit.getOfflinePlayer(renter))
				.name("panel-renterName", RentOutFeature.nameOf(renter))
				.lore("panel-renterTimeLeft", Utils.millisToHumanFormat(rentOut.getTimeLeft()))
				.blank()
				.lore("panel-renterRemove")
				.build(), click -> {
					rentOut.endRental();
					plugin.message(player, "rentout-removed", RentOutFeature.nameOf(renter), region);
					refresh();
				});
	}

	/**
	 * Show whether the region is on offer, with the buttons to change that.
	 * @param rentOut The renting out of the region
	 */
	private void buildOffer(RentOutFeature rentOut) {
		if(rentOut.isOffered()) {
			set(15, Icon.of(Material.LIME_DYE)
					.name("panel-rentOutOnName")
					.lore("panel-rentOutOnLore", Utils.formatCurrency(rentOut.getPrice()), rentOut.getDuration())
					.build(), click -> {
						rentOut.stopOffering();
						plugin.message(player, "rentout-stopped", region);
						refresh();
					});
		} else {
			set(15, Icon.of(Material.GRAY_DYE)
					.name("panel-rentOutOffName")
					.lore("panel-rentOutOffLore")
					.build(), click -> {
						player.closeInventory();
						plugin.message(player, "rentout-setPrompt", region);
					});
		}

		set(16, Icon.of(Material.NAME_TAG)
				.name("panel-rentOutChangeName")
				.lore("panel-rentOutChangeLore")
				.build(), click -> {
					player.closeInventory();
					plugin.message(player, "rentout-setPrompt", region);
				});
	}

	/**
	 * Add the button that goes back to the shop menu.
	 */
	private void buildBack() {
		setBack(22, parent);
		fillRest();
	}
}
