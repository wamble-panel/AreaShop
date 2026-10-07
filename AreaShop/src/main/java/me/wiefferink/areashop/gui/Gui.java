package me.wiefferink.areashop.gui;

import me.wiefferink.areashop.AreaShop;
import me.wiefferink.areashop.messages.Message;
import me.wiefferink.areashop.regions.GeneralRegion;
import me.wiefferink.areashop.tools.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base for the menus AreaShop shows.
 *
 * <p>A menu owns its inventory, which is how {@link GuiListener} recognizes it: there is no matching
 * on titles, so another plugin opening a chest with the same name can never be mistaken for one of
 * these.
 */
public abstract class Gui implements InventoryHolder {

	/** Slots in a row of a chest inventory. */
	public static final int ROW = 9;

	protected final AreaShop plugin = AreaShop.getInstance();
	protected final Player player;

	/** What to do when a slot is clicked, empty for slots that do nothing. */
	private final Map<Integer, Consumer<ClickType>> handlers = new HashMap<>();

	private Inventory inventory;

	protected Gui(Player player) {
		this.player = player;
	}

	/**
	 * The title shown at the top of the menu.
	 * @return The title
	 */
	protected abstract Component title();

	/**
	 * How many rows of nine slots the menu has.
	 * @return Number of rows, between 1 and 6
	 */
	protected abstract int rows();

	/**
	 * Fill the menu with items, called again whenever the menu is refreshed.
	 */
	protected abstract void build();

	@Override
	public Inventory getInventory() {
		return inventory;
	}

	/**
	 * Show this menu to the player.
	 */
	public void open() {
		inventory = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows())) * ROW, title());
		build();
		player.openInventory(inventory);
	}

	/**
	 * Build the menu again, for when something it shows has changed.
	 *
	 * <p>Keeps the same inventory open, so the menu does not flicker and the player does not lose
	 * their place.
	 */
	public void refresh() {
		if(inventory == null) {
			open();
			return;
		}
		handlers.clear();
		inventory.clear();
		build();
	}

	/**
	 * Put an item in the menu that does nothing when clicked.
	 * @param slot The slot to put it in
	 * @param item The item to show
	 */
	protected void set(int slot, ItemStack item) {
		set(slot, item, null);
	}

	/**
	 * Put an item in the menu.
	 * @param slot    The slot to put it in
	 * @param item    The item to show
	 * @param onClick What to do when it is clicked, null for nothing
	 */
	protected void set(int slot, ItemStack item, Consumer<ClickType> onClick) {
		if(slot < 0 || slot >= inventory.getSize()) {
			return;
		}
		inventory.setItem(slot, item);
		if(onClick != null) {
			handlers.put(slot, onClick);
		}
	}

	/**
	 * Fill every empty slot of a row with the background item.
	 * @param row The row to fill, starting at 0
	 */
	protected void fillRow(int row) {
		ItemStack filler = filler();
		for(int slot = row * ROW; slot < (row + 1) * ROW && slot < inventory.getSize(); slot++) {
			if(inventory.getItem(slot) == null) {
				set(slot, filler);
			}
		}
	}

	/**
	 * Fill every empty slot of the menu with the background item.
	 *
	 * <p>Called at the end of building a menu whose buttons do not fill it, so the player sees a tidy
	 * panel instead of holes they can drop items into.
	 */
	protected void fillRest() {
		ItemStack filler = filler();
		for(int slot = 0; slot < inventory.getSize(); slot++) {
			if(inventory.getItem(slot) == null) {
				set(slot, filler);
			}
		}
	}

	/**
	 * The item put in the slots that are not buttons.
	 * @return The background item
	 */
	private static ItemStack filler() {
		return Icon.of(Material.GRAY_STAINED_GLASS_PANE).name(Message.fromString(" ")).build();
	}

	/**
	 * Put the button that goes back to the menu that opened this one, or closes it when there is none.
	 * @param slot   The slot to put it in
	 * @param parent The menu to go back to, may be null
	 */
	protected void setBack(int slot, Gui parent) {
		if(parent == null) {
			set(slot, Icon.of(Material.BARRIER).name("panel-close").build(), click -> player.closeInventory());
			return;
		}
		set(slot, Icon.of(Material.ARROW).name("panel-back").build(), click -> parent.open());
	}

	/**
	 * Check if the player may change anything about a region from the panel.
	 *
	 * <p>That is its holder, and staff that were given {@code areashop.panel.others} so they can help
	 * a player out without having to take their shop over first.
	 *
	 * @param region The region to check
	 * @return true when the player may manage it
	 */
	protected boolean mayManage(GeneralRegion region) {
		return !region.isDeleted()
				&& (region.isOwner(player) || player.hasPermission("areashop.panel.others"));
	}

	/**
	 * Check if the player may look at a region in the panel without changing it.
	 *
	 * <p>Besides whoever manages it, that is the player currently renting it from its holder and
	 * anyone that was given access to build in it.
	 *
	 * @param region The region to check
	 * @return true when the region should show up for the player
	 */
	protected boolean maySee(GeneralRegion region) {
		if(region.isDeleted()) {
			return false;
		}
		return mayManage(region)
				|| region.getRentOutFeature().isRenter(player.getUniqueId())
				|| region.getFriendsFeature().getFriends().contains(player.getUniqueId());
	}

	/**
	 * Handle a click on one of the slots of this menu.
	 * @param slot      The slot that was clicked
	 * @param clickType How it was clicked
	 */
	void handleClick(int slot, ClickType clickType) {
		Consumer<ClickType> handler = handlers.get(slot);
		if(handler == null) {
			return;
		}
		try {
			handler.accept(clickType);
		} catch(RuntimeException e) {
			AreaShop.warn("Menu click of", player.getName(), "failed:", Utils.getStackTrace(e));
			player.closeInventory();
		}
	}

	/**
	 * Close this menu and run a command as the player.
	 *
	 * <p>Going through the command means the menu can never skip a permission check, a limit or a
	 * confirmation, all of that keeps living in one place.
	 *
	 * @param command The command to run, without the leading slash
	 */
	protected void runCommand(String command) {
		player.closeInventory();
		player.performCommand(command);
	}
}
