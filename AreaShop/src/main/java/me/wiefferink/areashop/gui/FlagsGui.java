package me.wiefferink.areashop.gui;

import me.wiefferink.areashop.messages.Message;
import me.wiefferink.areashop.regions.GeneralRegion;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * The menu where the owner of a shop changes what is allowed inside it.
 *
 * <p>Which settings show up here is decided by the server owner in the {@code panelFlags} section
 * of config.yml, so a player can never reach a WorldGuard flag that was not offered to them.
 */
public class FlagsGui extends Gui {

	private final GeneralRegion region;
	private final Gui parent;

	/**
	 * Construct the settings menu of one shop.
	 * @param player The player looking at it
	 * @param region The region to change
	 * @param parent The menu to go back to, may be null
	 */
	public FlagsGui(Player player, GeneralRegion region, Gui parent) {
		super(player);
		this.region = region;
		this.parent = parent;
	}

	@Override
	protected Component title() {
		return Message.fromKey("panel-settingsTitle").replacements(region).toComponent();
	}

	@Override
	protected int rows() {
		return 5;
	}

	@Override
	protected void build() {
		if(!mayManage(region)) {
			set(22, Icon.of(Material.BARRIER).name("panel-shopGoneName").lore("panel-shopGoneLore").build());
			buildBack();
			return;
		}

		List<PanelFlag> panelFlags = PanelFlag.all();
		for(int index = 0; index < panelFlags.size() && index < ROW * 4; index++) {
			PanelFlag panelFlag = panelFlags.get(index);
			set(index, buildIcon(panelFlag), click -> cycle(panelFlag));
		}

		buildBack();
	}

	/**
	 * Build the item of one setting, showing every option with the chosen one marked.
	 * @param panelFlag The setting to show
	 * @return The item
	 */
	private ItemStack buildIcon(PanelFlag panelFlag) {
		PanelFlag.Option current = panelFlag.getCurrent(region);

		Icon icon = Icon.of(panelFlag.getItem()).name(Message.fromString(panelFlag.getName()));
		if(!panelFlag.getDescription().isEmpty()) {
			icon.lore(Message.fromString(panelFlag.getDescription()));
		}
		icon.blank();

		for(PanelFlag.Option option : panelFlag.getOptions()) {
			boolean chosen = current != null && current.key().equals(option.key());
			icon.lore(chosen ? "panel-settingChosen" : "panel-settingOption", option.name());
		}

		icon.blank();
		icon.lore(panelFlag.isAllowed(player) ? "panel-settingChange" : "panel-settingNoPermission");
		return icon.build();
	}

	/**
	 * Move a setting to its next option and apply it right away.
	 * @param panelFlag The setting to change
	 */
	private void cycle(PanelFlag panelFlag) {
		if(!panelFlag.isAllowed(player)) {
			plugin.message(player, "panel-settingNoPermissionMessage");
			return;
		}

		PanelFlag.Option next = panelFlag.getNext(region);
		if(next == null) {
			return;
		}

		panelFlag.set(region, next);
		// Writes the flags to WorldGuard and saves the region
		region.update();

		plugin.message(player, "panel-settingChanged", region, panelFlag.getName(), next.name());
		refresh();
	}

	/**
	 * Add the button that goes back to the shop menu.
	 */
	private void buildBack() {
		setBack(40, parent);
		fillRest();
	}
}
