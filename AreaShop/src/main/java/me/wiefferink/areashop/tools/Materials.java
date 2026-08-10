package me.wiefferink.areashop.tools;

import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.Locale;
import java.util.Map;

/**
 * Helpers around sign materials.
 *
 * <p>Which materials count as a sign comes from the vanilla block tags, so every wood type that
 * Minecraft adds is picked up without a plugin update.
 */
public final class Materials {

	/**
	 * Material names used by AreaShop before 1.14, mapped to what they are called now.
	 * Regions saved by an older version of AreaShop still have these written in their config.
	 */
	private static final Map<String, String> LEGACY_NAMES = Map.of(
			"SIGN", "OAK_SIGN",
			"SIGN_POST", "OAK_SIGN",
			"LEGACY_SIGN", "OAK_SIGN",
			"LEGACY_SIGN_POST", "OAK_SIGN",
			"WALL_SIGN", "OAK_WALL_SIGN",
			"LEGACY_WALL_SIGN", "OAK_WALL_SIGN"
	);

	private Materials() {
	}

	/**
	 * Get the material belonging to a sign material name.
	 * @param name Name of the sign material, as written in the region config
	 * @return The material, or null when the name is not a sign
	 */
	public static Material signNameToMaterial(String name) {
		if(name == null || name.isBlank()) {
			return null;
		}

		String cleaned = name.trim().toUpperCase(Locale.ROOT);
		if(cleaned.startsWith("MINECRAFT:")) {
			cleaned = cleaned.substring("MINECRAFT:".length());
		}
		cleaned = LEGACY_NAMES.getOrDefault(cleaned, cleaned);

		Material result = Material.getMaterial(cleaned);
		if(result == null || !isSign(result)) {
			return null;
		}
		return result;
	}

	/**
	 * Check if a material is a sign, of any wood type and of any of the standing, wall
	 * and hanging variants.
	 * @param material Material to check
	 * @return true if the given material is a sign
	 */
	public static boolean isSign(Material material) {
		return material != null
				&& (Tag.ALL_SIGNS.isTagged(material) || Tag.ALL_HANGING_SIGNS.isTagged(material));
	}

	/**
	 * Check if a material name refers to a sign.
	 * @param name Name to check
	 * @return true if the given name is that of a sign
	 */
	public static boolean isSign(String name) {
		return signNameToMaterial(name) != null;
	}

	/**
	 * Check if a material is a sign that hangs on a wall, which needs a block behind it.
	 * @param material Material to check
	 * @return true if the given material is a wall sign
	 */
	public static boolean isWallSign(Material material) {
		return material != null
				&& (Tag.WALL_SIGNS.isTagged(material) || Tag.WALL_HANGING_SIGNS.isTagged(material));
	}
}
