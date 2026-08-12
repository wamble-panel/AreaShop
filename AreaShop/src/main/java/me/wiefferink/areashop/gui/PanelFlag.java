package me.wiefferink.areashop.gui;

import me.wiefferink.areashop.AreaShop;
import me.wiefferink.areashop.regions.GeneralRegion;
import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A setting the owner of a region can change from the panel, backed by one or more WorldGuard flags.
 *
 * <p>Which settings exist is up to the server owner, they are read from the {@code panelFlags}
 * section of config.yml. That keeps players away from flags that would let them break the server,
 * while still letting an owner offer whatever makes sense for their marketplace:
 *
 * <pre>
 * panelFlags:
 *   entry:
 *     item: OAK_DOOR
 *     name: '&amp;e&amp;lWho can enter'
 *     description: '&amp;7Choose who may walk into your shop.'
 *     options:
 *       everyone:
 *         name: '&amp;aEveryone'
 *         flags:
 *           entry: ''
 *       friends:
 *         name: '&amp;eOnly you and your friends'
 *         flags:
 *           entry: 'deny g:nonmembers'
 * </pre>
 *
 * <p>The flag values use the same notation as the {@code flagProfiles} section, so
 * {@code deny g:nonmembers} sets the flag to deny for the nonmembers group, and an empty value
 * clears the flag again.
 */
public class PanelFlag {

	/** Where the choice of a region is remembered. */
	public static final String SETTING_PATH = "general.panelFlags";

	private final String key;
	private final Material item;
	private final String name;
	private final String description;
	private final String permission;
	private final List<Option> options;

	/**
	 * One of the choices of a setting.
	 * @param key   Key of the option in the config
	 * @param name  Name to show for it
	 * @param flags The WorldGuard flags to apply when it is chosen
	 */
	public record Option(String key, String name, Map<String, String> flags) {
	}

	private PanelFlag(String key, Material item, String name, String description, String permission, List<Option> options) {
		this.key = key;
		this.item = item;
		this.name = name;
		this.description = description;
		this.permission = permission;
		this.options = options;
	}

	public String getKey() {
		return key;
	}

	public Material getItem() {
		return item;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public List<Option> getOptions() {
		return options;
	}

	/**
	 * Check if a player is allowed to change this setting.
	 * @param player The player to check
	 * @return true when the player may change it
	 */
	public boolean isAllowed(Player player) {
		return permission == null || player.hasPermission(permission);
	}

	/**
	 * Get the option a region is currently set to.
	 * @param region The region to read
	 * @return The chosen option, the first one when the region has no choice stored
	 */
	public Option getCurrent(GeneralRegion region) {
		String chosen = region.getConfig().getString(SETTING_PATH + "." + key);
		if(chosen != null) {
			for(Option option : options) {
				if(option.key().equalsIgnoreCase(chosen)) {
					return option;
				}
			}
		}
		return options.isEmpty() ? null : options.get(0);
	}

	/**
	 * Get the option that follows the current one, wrapping around at the end.
	 * @param region The region to read
	 * @return The next option, or null when this setting has no options
	 */
	public Option getNext(GeneralRegion region) {
		if(options.isEmpty()) {
			return null;
		}
		Option current = getCurrent(region);
		int index = options.indexOf(current);
		return options.get((index + 1) % options.size());
	}

	/**
	 * Remember the choice of a region, without applying it yet.
	 * @param region The region to change
	 * @param option The option to store
	 */
	public void set(GeneralRegion region, Option option) {
		region.setSetting(SETTING_PATH + "." + key, option == null ? null : option.key());
	}

	/**
	 * Read the settings that the server owner offers in the panel.
	 * @return The settings, in the order they are written in the config
	 */
	public static List<PanelFlag> all() {
		List<PanelFlag> result = new ArrayList<>();
		ConfigurationSection section = section(AreaShop.getInstance().getConfig(), "panelFlags");
		if(section == null) {
			return result;
		}

		for(String key : section.getKeys(false)) {
			ConfigurationSection flagSection = section.getConfigurationSection(key);
			if(flagSection == null) {
				continue;
			}

			String itemName = flagSection.getString("item");
			Material item = itemName == null ? null : Material.matchMaterial(itemName.toUpperCase(Locale.ROOT));
			if(item == null || !item.isItem()) {
				if(itemName != null) {
					AreaShop.warn("Setting '" + key + "' in the panelFlags section has an item that does not exist:", itemName);
				}
				item = Material.PAPER;
			}

			List<Option> options = readOptions(key, flagSection.getConfigurationSection("options"));
			if(options.isEmpty()) {
				AreaShop.warn("Setting '" + key + "' in the panelFlags section has no options, skipping it");
				continue;
			}

			result.add(new PanelFlag(
					key,
					item,
					flagSection.getString("name", key),
					flagSection.getString("description", ""),
					flagSection.getString("permission"),
					options
			));
		}
		return result;
	}

	/**
	 * Read a section from the config, falling back to the bundled defaults.
	 *
	 * <p>{@code getConfigurationSection} does not look at the defaults of a configuration, so a
	 * section that only exists in hiddenConfig.yml would otherwise come back empty.
	 *
	 * @param config The configuration to read from
	 * @param path   The path of the section
	 * @return The section, or null when neither the config nor the defaults have it
	 */
	private static ConfigurationSection section(YamlConfiguration config, String path) {
		if(config.isSet(path)) {
			return config.getConfigurationSection(path);
		}
		Configuration defaults = config.getDefaults();
		return defaults == null ? null : defaults.getConfigurationSection(path);
	}

	/**
	 * Read the options of one setting.
	 * @param flagKey Key of the setting, used for logging
	 * @param section The options section
	 * @return The options, in the order they are written in the config
	 */
	private static List<Option> readOptions(String flagKey, ConfigurationSection section) {
		List<Option> result = new ArrayList<>();
		if(section == null) {
			return result;
		}

		for(String optionKey : section.getKeys(false)) {
			ConfigurationSection optionSection = section.getConfigurationSection(optionKey);
			if(optionSection == null) {
				continue;
			}

			Map<String, String> flags = new LinkedHashMap<>();
			ConfigurationSection flagsSection = optionSection.getConfigurationSection("flags");
			if(flagsSection != null) {
				for(String flagName : flagsSection.getKeys(false)) {
					flags.put(flagName, flagsSection.getString(flagName, ""));
				}
			}
			if(flags.isEmpty()) {
				AreaShop.warn("Option '" + optionKey + "' of panel setting '" + flagKey + "' does not set any flag");
			}
			result.add(new Option(optionKey, optionSection.getString("name", optionKey), flags));
		}
		return result;
	}
}
