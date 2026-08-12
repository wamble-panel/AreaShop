package me.wiefferink.areashop.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Sends clicks in an AreaShop menu to the menu that was clicked.
 */
public class GuiListener implements Listener {

	/**
	 * Handle a click in a menu.
	 * @param event The event
	 */
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onInventoryClick(InventoryClickEvent event) {
		if(!(event.getInventory().getHolder() instanceof Gui gui)) {
			return;
		}

		// Nothing in a menu may be picked up, moved or shift clicked into, whichever half was clicked
		event.setCancelled(true);

		if(event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
			return;
		}
		gui.handleClick(event.getRawSlot(), event.getClick());
	}

	/**
	 * Stop items being dragged into a menu.
	 * @param event The event
	 */
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onInventoryDrag(InventoryDragEvent event) {
		if(!(event.getInventory().getHolder() instanceof Gui)) {
			return;
		}
		for(int slot : event.getRawSlots()) {
			if(slot < event.getInventory().getSize()) {
				event.setCancelled(true);
				return;
			}
		}
	}
}
