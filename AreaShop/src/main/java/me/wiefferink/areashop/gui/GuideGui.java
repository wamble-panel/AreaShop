package me.wiefferink.areashop.gui;

import me.wiefferink.areashop.messages.Message;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * A menu that explains how renting, buying and running a shop works, in plain words.
 *
 * <p>Every word of it comes from the language file, so a server can reword the whole thing, and
 * translators get it along with the rest of the messages.
 */
public class GuideGui extends Gui {

	/**
	 * One page of the guide.
	 * @param slot     Where it sits in the menu
	 * @param material The item that stands for it
	 * @param key      Name of its language keys, which are 'guide-&lt;key&gt;' and 'guide-&lt;key&gt;Lore'
	 */
	private record Topic(int slot, Material material, String key) {
	}

	/** The topics, laid out in two rows so they read left to right. */
	private static final List<Topic> TOPICS = List.of(
			new Topic(10, Material.CHEST, "shops"),
			new Topic(11, Material.CLOCK, "renting"),
			new Topic(12, Material.EMERALD, "buying"),
			new Topic(13, Material.PLAYER_HEAD, "access"),
			new Topic(14, Material.COMPARATOR, "settings"),
			new Topic(15, Material.VILLAGER_SPAWN_EGG, "stalls"),
			new Topic(16, Material.GOLD_INGOT, "rentingOut"),
			new Topic(21, Material.OAK_SIGN, "signs"),
			new Topic(22, Material.COMPASS, "finding"),
			new Topic(23, Material.WRITABLE_BOOK, "commands")
	);

	private final Gui parent;

	/**
	 * Construct the guide.
	 * @param player The player reading it
	 * @param parent The menu to go back to, may be null
	 */
	public GuideGui(Player player, Gui parent) {
		super(player);
		this.parent = parent;
	}

	@Override
	protected Component title() {
		return Message.fromKey("guide-title").toComponent();
	}

	@Override
	protected int rows() {
		return 5;
	}

	@Override
	protected void build() {
		set(4, Icon.of(Material.KNOWLEDGE_BOOK)
				.name("guide-headerName")
				.lore("guide-headerLore")
				.build());

		for(Topic topic : TOPICS) {
			set(topic.slot(), Icon.of(topic.material())
					.name("guide-" + topic.key())
					.lore("guide-" + topic.key() + "Lore")
					.build());
		}

		if(parent != null) {
			set(40, Icon.of(Material.ARROW).name("panel-back").build(), click -> parent.open());
		} else {
			set(40, Icon.of(Material.BARRIER).name("guide-close").build(), click -> player.closeInventory());
		}
		fillRow(4);
	}
}
