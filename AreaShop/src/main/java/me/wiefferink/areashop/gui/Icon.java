package me.wiefferink.areashop.gui;

import me.wiefferink.areashop.messages.Colors;
import me.wiefferink.areashop.messages.Message;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the items shown in a menu.
 *
 * <p>Item names and lore are built from the language files, so a menu can be translated the same way
 * the chat messages are, colors and all.
 */
public final class Icon {

	private final ItemStack item;
	private final List<Component> lore = new ArrayList<>();
	private Component name;

	private Icon(Material material) {
		this.item = new ItemStack(material);
	}

	/**
	 * Start building an icon.
	 * @param material The material to show
	 * @return The builder
	 */
	public static Icon of(Material material) {
		return new Icon(material);
	}

	/**
	 * Start building the head of a player.
	 * @param player The player to show the head of
	 * @return The builder
	 */
	public static Icon head(OfflinePlayer player) {
		Icon icon = new Icon(Material.PLAYER_HEAD);
		ItemMeta meta = icon.item.getItemMeta();
		if(meta instanceof SkullMeta skull) {
			skull.setOwningPlayer(player);
			icon.item.setItemMeta(skull);
		}
		return icon;
	}

	/**
	 * Set how many of the item to show, which players read as a count.
	 * @param amount The amount, clamped to what an item stack can hold
	 * @return this
	 */
	public Icon amount(int amount) {
		item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), amount)));
		return this;
	}

	/**
	 * Set the name of the item from a message.
	 * @param message The message to use
	 * @return this
	 */
	public Icon name(Message message) {
		this.name = plain(message.toComponent());
		return this;
	}

	/**
	 * Set the name of the item from a language key.
	 * @param key          Key of the message
	 * @param replacements Values to fill in
	 * @return this
	 */
	public Icon name(String key, Object... replacements) {
		return name(Message.fromKey(key).replacements(replacements));
	}

	/**
	 * Add lore lines from a message, where {@code [break]} starts a new line.
	 * @param message The message to use
	 * @return this
	 */
	public Icon lore(Message message) {
		String single = message.getSingle();
		if(single.isEmpty()) {
			return this;
		}
		for(String line : single.split("\n", -1)) {
			lore.add(plain(Colors.toComponent(line)));
		}
		return this;
	}

	/**
	 * Add lore lines from a language key, where {@code [break]} starts a new line.
	 * @param key          Key of the message
	 * @param replacements Values to fill in
	 * @return this
	 */
	public Icon lore(String key, Object... replacements) {
		return lore(Message.fromKey(key).replacements(replacements));
	}

	/**
	 * Add an empty lore line, to group things that belong together.
	 * @return this
	 */
	public Icon blank() {
		lore.add(Component.empty());
		return this;
	}

	/**
	 * Finish the item.
	 * @return The item to put in a menu
	 */
	public ItemStack build() {
		ItemMeta meta = item.getItemMeta();
		if(meta != null) {
			if(name != null) {
				meta.displayName(name);
			}
			if(!lore.isEmpty()) {
				meta.lore(lore);
			}
			item.setItemMeta(meta);
		}
		return item;
	}

	/**
	 * Switch off the italics that Minecraft puts on every renamed item.
	 * @param component The component to fix up
	 * @return The component, no longer italic unless it asked to be
	 */
	private static Component plain(Component component) {
		return component.decoration(TextDecoration.ITALIC, false);
	}
}
