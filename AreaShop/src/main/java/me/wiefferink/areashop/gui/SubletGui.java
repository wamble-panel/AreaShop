package me.wiefferink.areashop.gui;

import me.wiefferink.areashop.features.SubletFeature;
import me.wiefferink.areashop.messages.Message;
import me.wiefferink.areashop.regions.GeneralRegion;
import me.wiefferink.areashop.tools.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The menu where the holder of a region rents space in it out to other players.
 */
public class SubletGui extends Gui {

	/** Rows used for the subtenants, the last row holds the offer and the navigation. */
	private static final int TENANT_ROWS = 3;

	private final GeneralRegion region;
	private final Gui parent;

	/**
	 * Construct the subletting menu of one region.
	 * @param player The player looking at it
	 * @param region The region to rent space in out
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
		return TENANT_ROWS + 1;
	}

	@Override
	protected void build() {
		if(region.isDeleted() || !region.isOwner(player)) {
			set(13, Icon.of(Material.BARRIER).name("panel-shopGoneName").lore("panel-shopGoneLore").build());
			buildNavigation();
			return;
		}

		SubletFeature sublet = region.getSubletFeature();
		List<Map.Entry<UUID, Long>> tenants = new ArrayList<>(sublet.getTenants().entrySet());

		if(tenants.isEmpty()) {
			set(13, Icon.of(Material.BARRIER)
					.name("panel-noTenantsName")
					.lore("panel-noTenantsLore")
					.build());
		} else {
			for(int slot = 0; slot < TENANT_ROWS * ROW && slot < tenants.size(); slot++) {
				Map.Entry<UUID, Long> tenant = tenants.get(slot);
				set(slot, buildTenantIcon(tenant.getKey(), tenant.getValue()), click -> {
					if(player.hasPermission("areashop.sublet")) {
						sublet.remove(tenant.getKey());
						plugin.message(player, "sublet-removed", nameOf(tenant.getKey()), region);
						refresh();
					}
				});
			}
		}

		buildOffer(sublet);
		buildNavigation();
	}

	/**
	 * Build the head shown for one subtenant.
	 * @param tenant The player renting a spot
	 * @param until  When their time runs out, in milliseconds since the epoch
	 * @return The item
	 */
	private ItemStack buildTenantIcon(UUID tenant, long until) {
		OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(tenant);
		long left = until - System.currentTimeMillis();

		return Icon.head(offlinePlayer)
				.name("panel-tenantName", nameOf(tenant))
				.lore(left > 0 ? "panel-tenantTimeLeft" : "panel-tenantExpired", Utils.millisToHumanFormat(Math.max(0, left)))
				.blank()
				.lore("panel-tenantRemove")
				.build();
	}

	/**
	 * Build the button that turns the offer on and off, and the one that changes it.
	 * @param sublet The subletting of the region
	 */
	private void buildOffer(SubletFeature sublet) {
		int row = TENANT_ROWS * ROW;
		boolean offered = sublet.isOffered();

		if(offered) {
			set(row + 2, Icon.of(Material.LIME_DYE)
					.name("panel-subletOnName")
					.lore("panel-subletOnLore", Utils.formatCurrency(sublet.getPrice()), sublet.getDuration(), sublet.countActiveTenants())
					.build(), click -> {
						sublet.stopOffering();
						plugin.message(player, "sublet-stopped", region);
						refresh();
					});
		} else {
			set(row + 2, Icon.of(Material.GRAY_DYE)
					.name("panel-subletOffName")
					.lore("panel-subletOffLore")
					.build(), click -> {
						player.closeInventory();
						plugin.message(player, "sublet-setPrompt", region);
					});
		}

		set(row + 6, Icon.of(Material.NAME_TAG)
				.name("panel-subletChangeName")
				.lore("panel-subletChangeLore")
				.build(), click -> {
					player.closeInventory();
					plugin.message(player, "sublet-setPrompt", region);
				});
	}

	/**
	 * Build the bottom row with the button back to the shop.
	 */
	private void buildNavigation() {
		if(parent != null) {
			set(TENANT_ROWS * ROW + 4, Icon.of(Material.ARROW).name("panel-back").build(), click -> parent.open());
		}
		fillRow(TENANT_ROWS);
	}

	/**
	 * Get a readable name for a player.
	 * @param player The player to name
	 * @return Their name, or their id when the server does not know it
	 */
	private static String nameOf(UUID player) {
		String name = Bukkit.getOfflinePlayer(player).getName();
		return name == null ? player.toString() : name;
	}
}
