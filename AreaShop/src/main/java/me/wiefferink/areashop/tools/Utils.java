package me.wiefferink.areashop.tools;

import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import me.wiefferink.areashop.AreaShop;
import me.wiefferink.areashop.interfaces.WorldEditSelection;
import me.wiefferink.areashop.regions.BuyRegion;
import me.wiefferink.areashop.regions.GeneralRegion;
import me.wiefferink.areashop.regions.RentRegion;
import me.wiefferink.areashop.messages.Colors;
import me.wiefferink.areashop.messages.Log;
import me.wiefferink.areashop.messages.Message;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Utils {

	// Not used
	private Utils() {
	}

	private static YamlConfiguration config;
	private static Set<String> identifiers;
	private static Set<String> seconds;
	private static Set<String> minutes;
	private static Set<String> hours;
	private static Set<String> days;
	private static Set<String> weeks;
	private static Set<String> months;
	private static Set<String> years;
	private static Map<Double, String> suffixes;

	/**
	 * Initialize the utilities class with constants.
	 * @param pluginConfig The config of the plugin
	 */
	public static void initialize(YamlConfiguration pluginConfig) {
		config = pluginConfig;

		// Setup individual identifiers
		seconds = getSetAndDefaults("seconds");
		minutes = getSetAndDefaults("minutes");
		hours = getSetAndDefaults("hours");
		days = getSetAndDefaults("days");
		weeks = getSetAndDefaults("weeks");
		months = getSetAndDefaults("months");
		years = getSetAndDefaults("years");
		// Setup all time identifiers
		identifiers = new HashSet<>();
		identifiers.addAll(seconds);
		identifiers.addAll(minutes);
		identifiers.addAll(hours);
		identifiers.addAll(days);
		identifiers.addAll(weeks);
		identifiers.addAll(months);
		identifiers.addAll(years);

		suffixes = new HashMap<>();
		// This stuff should not be necessary, but it is, getConfigurationSection() does not do proper fallback to defaults!
		// TODO: Create a custom configuration that fixes this behavior
		ConfigurationSection suffixesSection = null;
		if(config.isSet("metricSymbols")) {
			suffixesSection = config.getConfigurationSection("metricSymbols");
		} else {
			Configuration defaults = config.getDefaults();
			if(defaults != null) {
				suffixesSection = defaults.getConfigurationSection("metricSymbols");
			}
		}
		if(suffixesSection != null) {
			for(String key : suffixesSection.getKeys(false)) {
				try {
					suffixes.put(Double.parseDouble(key), suffixesSection.getString(key));
				} catch(NumberFormatException e) {
					Log.warn("Key '" + key + "' in the metricSymbols section of config.yml is not a number!");
				}
			}
		}
	}

	/**
	 * Get a string list from the config, combined with the entries specified in the default config.
	 * @param path The path to read the lists from
	 * @return List with all values defined in the config and the default config combined
	 */
	private static Set<String> getSetAndDefaults(String path) {
		Set<String> result = new HashSet<>(config.getStringList(path));
		ConfigurationSection defaults = config.getDefaults();
		if(defaults != null) {
			result.addAll(defaults.getStringList(path));
		}
		return result;
	}

	/**
	 * Create a message with a list of parts.
	 * @param replacements The parts to use
	 * @param messagePart  The message to use for the parts
	 * @return A Message object containing the parts combined into one message
	 */
	public static Message combinedMessage(Collection<?> replacements, String messagePart) {
		return combinedMessage(replacements, messagePart, ", ");
	}

	/**
	 * Create a message with a list of parts.
	 * @param replacements The parts to use
	 * @param messagePart  The message to use for the parts
	 * @param combiner     The string to use as combiner
	 * @return A Message object containing the parts combined into one message
	 */
	public static Message combinedMessage(Collection<?> replacements, String messagePart, String combiner) {
		Message result = Message.empty();
		boolean first = true;
		for(Object part : replacements) {
			if(first) {
				first = false;
			} else {
				result.append(combiner);
			}
			result.append(Message.fromKey(messagePart).replacements(part));
		}
		return result;
	}

	/**
	 * Gets the online players.
	 * @return Online players
	 */
	public static Collection<? extends Player> getOnlinePlayers() {
		return Bukkit.getOnlinePlayers();
	}

	/**
	 * Turn a throwable into a string, to write it to the log.
	 * @param throwable The throwable to print, may be null
	 * @return The stacktrace of the throwable
	 */
	public static String getStackTrace(Throwable throwable) {
		if(throwable == null) {
			return "";
		}
		StringWriter result = new StringWriter();
		throwable.printStackTrace(new PrintWriter(result, true));
		return result.toString();
	}

	/**
	 * Glue objects together with something in between.
	 * @param parts     The objects to glue together, null values are printed as 'null'
	 * @param separator The string to put between the parts
	 * @return The parts glued together
	 */
	public static String join(Object[] parts, String separator) {
		return parts == null ? "" : join(Arrays.asList(parts), separator);
	}

	/**
	 * Glue part of an array together with something in between.
	 * @param parts     The objects to glue together, null values are printed as 'null'
	 * @param separator The string to put between the parts
	 * @param start     Index to start at, inclusive
	 * @param end       Index to stop at, exclusive
	 * @return The selected parts glued together
	 */
	public static String join(Object[] parts, String separator, int start, int end) {
		if(parts == null) {
			return "";
		}
		int from = Math.max(0, start);
		int to = Math.min(parts.length, end);
		return from >= to ? "" : join(Arrays.asList(parts).subList(from, to), separator);
	}

	/**
	 * Glue objects together with something in between.
	 * @param parts     The objects to glue together, null values are printed as 'null'
	 * @param separator The string to put between the parts
	 * @return The parts glued together
	 */
	public static String join(Iterable<?> parts, String separator) {
		if(parts == null) {
			return "";
		}
		StringBuilder result = new StringBuilder();
		boolean first = true;
		for(Object part : parts) {
			if(!first) {
				result.append(separator);
			}
			first = false;
			result.append(part);
		}
		return result.toString();
	}

	/**
	 * Create a map from a location, to save it in the config.
	 * @param location    The location to transform
	 * @param setPitchYaw true to save the pitch and yaw, otherwise false
	 * @return The map with the location values
	 */
	public static ConfigurationSection locationToConfig(Location location, boolean setPitchYaw) {
		if(location == null) {
			return null;
		}
		ConfigurationSection result = new YamlConfiguration();
		result.set("world", location.getWorld().getName());
		result.set("x", location.getX());
		result.set("y", location.getY());
		result.set("z", location.getZ());
		if(setPitchYaw) {
			result.set("yaw", Float.toString(location.getYaw()));
			result.set("pitch", Float.toString(location.getPitch()));
		}
		return result;
	}

	/**
	 * Create a map from a location, to save it in the config (without pitch and yaw).
	 * @param location The location to transform
	 * @return The map with the location values
	 */
	public static ConfigurationSection locationToConfig(Location location) {
		return locationToConfig(location, false);
	}

	/**
	 * Create a location from a map, reconstruction from the config values.
	 * @param config The config section to reconstruct from
	 * @return The location
	 */
	public static Location configToLocation(ConfigurationSection config) {
		if(config == null
				|| !config.isString("world")
				|| !config.isDouble("x")
				|| !config.isDouble("y")
				|| !config.isDouble("z")
				|| Bukkit.getWorld(config.getString("world")) == null) {
			return null;
		}
		Location result = new Location(
				Bukkit.getWorld(config.getString("world")),
				config.getDouble("x"),
				config.getDouble("y"),
				config.getDouble("z"));
		if(config.isString("yaw") && config.isString("pitch")) {
			result.setPitch(Float.parseFloat(config.getString("pitch")));
			result.setYaw(Float.parseFloat(config.getString("yaw")));
		}
		return result;
	}

	/**
	 * Create a comma-separated list.
	 * @param input Collection of object which should be concatenated with comma's in between (skipping null values)
	 * @return Innput object concatenated with comma's in between
	 */
	public static String createCommaSeparatedList(Collection<?> input) {
		StringBuilder result = new StringBuilder();
		boolean first = true;
		for(Object object : input) {
			if(object != null) {
				if(first) {
					first = false;
					result.append(object.toString());
				} else {
					result.append(", ").append(object.toString());
				}
			}
		}
		return result.toString();
	}

	/**
	 * Convert milliseconds to ticks.
	 * @param milliseconds Milliseconds to convert
	 * @return milliseconds divided by 50 (20 ticks per second)
	 */
	public static long millisToTicks(long milliseconds) {
		return milliseconds / 50;
	}

	/**
	 * Convert milliseconds to a human readable format.
	 * @param milliseconds The amount of milliseconds to convert
	 * @return A formatted string based on the language file
	 */
	public static String millisToHumanFormat(long milliseconds) {
		long timeLeft = milliseconds + 500;
		// To seconds
		timeLeft /= 1000;
		if(timeLeft <= 0) {
			return Message.fromKey("timeleft-ended").getPlain();
		} else if(timeLeft == 1) {
			return Message.fromKey("timeleft-second").replacements(timeLeft).getPlain();
		} else if(timeLeft <= 120) {
			return Message.fromKey("timeleft-seconds").replacements(timeLeft).getPlain();
		}
		// To minutes
		timeLeft /= 60;
		if(timeLeft <= 120) {
			return Message.fromKey("timeleft-minutes").replacements(timeLeft).getPlain();
		}
		// To hours
		timeLeft /= 60;
		if(timeLeft <= 48) {
			return Message.fromKey("timeleft-hours").replacements(timeLeft).getPlain();
		}
		// To days
		timeLeft /= 24;
		if(timeLeft <= 60) {
			return Message.fromKey("timeleft-days").replacements(timeLeft).getPlain();
		}
		// To months
		timeLeft /= 30;
		if(timeLeft <= 24) {
			return Message.fromKey("timeleft-months").replacements(timeLeft).getPlain();
		}
		// To years
		timeLeft /= 12;
		return Message.fromKey("timeleft-years").replacements(timeLeft).getPlain();
	}

	private static final BlockFace[] facings = {BlockFace.NORTH, BlockFace.NORTH_EAST, BlockFace.EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH, BlockFace.SOUTH_WEST, BlockFace.WEST, BlockFace.NORTH_WEST};

	/**
	 * Get the facing direction based on the yaw.
	 * @param yaw The horizontal angle that for example the player is looking
	 * @return The Block Face of the angle
	 */
	public static BlockFace yawToFacing(float yaw) {
		return facings[Math.round(yaw / 45f) & 0x7];
	}

	// ======================================================================
	// Methods to get WorldGuard or AreaShop regions by location or selection
	// ======================================================================

	/**
	 * Get all AreaShop regions intersecting with a WorldEdit selection.
	 * @param selection The selection to check
	 * @return A list with all the AreaShop regions intersecting with the selection
	 */
	public static List<GeneralRegion> getRegionsInSelection(WorldEditSelection selection) {
		ArrayList<GeneralRegion> result = new ArrayList<>();
		for(ProtectedRegion region : getWorldEditRegionsInSelection(selection)) {
			GeneralRegion asRegion = AreaShop.getInstance().getFileManager().getRegion(region.getId());
			if(asRegion != null) {
				result.add(asRegion);
			}
		}
		return result;
	}

	/**
	 * Get all AreaShop regions containing a location.
	 * @param location The location to check
	 * @return A list with all the AreaShop regions that contain the location
	 */
	public static List<GeneralRegion> getRegions(Location location) {
		return getRegionsInSelection(new WorldEditSelection(location.getWorld(), location, location));
	}

	/**
	 * Get all WorldGuard regions intersecting with a WorldEdit selection.
	 * @param selection The selection to check
	 * @return A list with all the WorldGuard regions intersecting with the selection
	 */
	public static List<ProtectedRegion> getWorldEditRegionsInSelection(WorldEditSelection selection) {
		// Get all regions inside or intersecting with the WorldEdit selection of the player
		World world = selection.getWorld();
		RegionManager regionManager = AreaShop.getInstance().getRegionManager(world);
		ArrayList<ProtectedRegion> result = new ArrayList<>();
		Location selectionMin = selection.getMinimumLocation();
		Location selectionMax = selection.getMaximumLocation();
		for(ProtectedRegion region : regionManager.getRegions().values()) {
			Vector regionMin = AreaShop.getInstance().getWorldGuardHandler().getMinimumPoint(region);
			Vector regionMax = AreaShop.getInstance().getWorldGuardHandler().getMaximumPoint(region);
			if(
					(      // x part, resolves to true if the selection and region overlap anywhere on the x-axis
							(regionMin.getBlockX() <= selectionMax.getBlockX() && regionMin.getBlockX() >= selectionMin.getBlockX())
									|| (regionMax.getBlockX() <= selectionMax.getBlockX() && regionMax.getBlockX() >= selectionMin.getBlockX())
									|| (selectionMin.getBlockX() >= regionMin.getBlockX() && selectionMin.getBlockX() <= regionMax.getBlockX())
									|| (selectionMax.getBlockX() >= regionMin.getBlockX() && selectionMax.getBlockX() <= regionMax.getBlockX())
					) && ( // Y part, resolves to true if the selection and region overlap anywhere on the y-axis
							(regionMin.getBlockY() <= selectionMax.getBlockY() && regionMin.getBlockY() >= selectionMin.getBlockY())
									|| (regionMax.getBlockY() <= selectionMax.getBlockY() && regionMax.getBlockY() >= selectionMin.getBlockY())
									|| (selectionMin.getBlockY() >= regionMin.getBlockY() && selectionMin.getBlockY() <= regionMax.getBlockY())
									|| (selectionMax.getBlockY() >= regionMin.getBlockY() && selectionMax.getBlockY() <= regionMax.getBlockY())
					) && ( // Z part, resolves to true if the selection and region overlap anywhere on the z-axis
							(regionMin.getBlockZ() <= selectionMax.getBlockZ() && regionMin.getBlockZ() >= selectionMin.getBlockZ())
									|| (regionMax.getBlockZ() <= selectionMax.getBlockZ() && regionMax.getBlockZ() >= selectionMin.getBlockZ())
									|| (selectionMin.getBlockZ() >= regionMin.getBlockZ() && selectionMin.getBlockZ() <= regionMax.getBlockZ())
									|| (selectionMax.getBlockZ() >= regionMin.getBlockZ() && selectionMax.getBlockZ() <= regionMax.getBlockZ())
					)
			) {
				result.add(region);
			}
		}
		return result;
	}

	/**
	 * Get a list of regions around a location.
	 * - Returns highest priority, child instead of parent regions
	 * @param location The location to check for regions
	 * @return empty list if no regions found, 1 member if 1 region is a priority, more if regions with the same priority
	 */
	public static List<ProtectedRegion> getImportantWorldEditRegions(Location location) {
		List<ProtectedRegion> result = new ArrayList<>();
		Set<ProtectedRegion> regions = AreaShop.getInstance().getWorldGuardHandler().getApplicableRegionsSet(location);
		if(regions != null) {
			boolean first = true;
			for(ProtectedRegion pr : regions) {
				if(first) {
					result.add(pr);
					first = false;
				} else {
					if(pr.getPriority() > result.get(0).getPriority()) {
						result.clear();
						result.add(pr);
					} else if(pr.getParent() != null && pr.getParent().equals(result.get(0))) {
						result.clear();
						result.add(pr);
					} else {
						result.add(pr);
					}
				}
			}
		}
		return result;
	}

	/**
	 * Get the most important rental AreaShop regions.
	 * - Returns highest priority, child instead of parent regions.
	 * @param location The location to check for regions
	 * @return empty list if no regions found, 1 member if 1 region is a priority, more if regions with the same priority
	 */
	public static List<RentRegion> getImportantRentRegions(Location location) {
		List<RentRegion> result = new ArrayList<>();
		for(GeneralRegion region : getImportantRegions(location, GeneralRegion.RegionType.RENT)) {
			result.add((RentRegion)region);
		}
		return result;
	}

	/**
	 * Get the most important buy AreaShop regions.
	 * - Returns highest priority, child instead of parent regions.
	 * @param location The location to check for regions
	 * @return empty list if no regions found, 1 member if 1 region is a priority, more if regions with the same priority
	 */
	public static List<BuyRegion> getImportantBuyRegions(Location location) {
		List<BuyRegion> result = new ArrayList<>();
		for(GeneralRegion region : getImportantRegions(location, GeneralRegion.RegionType.BUY)) {
			result.add((BuyRegion)region);
		}
		return result;
	}

	/**
	 * Get the most important AreaShop regions.
	 * - Returns highest priority, child instead of parent regions.
	 * @param location The location to check for regions
	 * @return empty list if no regions found, 1 member if 1 region is a priority, more if regions with the same priority
	 */
	public static List<GeneralRegion> getImportantRegions(Location location) {
		return getImportantRegions(location, null);
	}

	/**
	 * Get the most important AreaShop regions.
	 * - Returns highest priority, child instead of parent regions.
	 * @param location The location to check for regions
	 * @param type     The type of regions to look for, null for all
	 * @return empty list if no regions found, 1 member if 1 region is a priority, more if regions with the same priority
	 */
	public static List<GeneralRegion> getImportantRegions(Location location, GeneralRegion.RegionType type) {
		List<GeneralRegion> result = new ArrayList<>();
		Set<ProtectedRegion> regions = AreaShop.getInstance().getWorldGuardHandler().getApplicableRegionsSet(location);
		if(regions != null) {
			List<GeneralRegion> candidates = new ArrayList<>();
			for(ProtectedRegion pr : regions) {
				GeneralRegion region = AreaShop.getInstance().getFileManager().getRegion(pr.getId());
				if(region != null && (
						(type == GeneralRegion.RegionType.RENT && region instanceof RentRegion)
								|| (type == GeneralRegion.RegionType.BUY && region instanceof BuyRegion)
								|| type == null)) {
					candidates.add(region);
				}
			}
			boolean first = true;
			for(GeneralRegion region : candidates) {
				if(region == null) {
					AreaShop.debug("skipped null region");
					continue;
				}
				if(first) {
					result.add(region);
					first = false;
				} else {
					if(region.getRegion().getPriority() > result.get(0).getRegion().getPriority()) {
						result.clear();
						result.add(region);
					} else if(region.getRegion().getParent() != null && region.getRegion().getParent().equals(result.get(0).getRegion())) {
						result.clear();
						result.add(region);
					} else {
						result.add(region);
					}
				}
			}
		}
		return new ArrayList<>(result);
	}


	/**
	 * Convert color and formatting codes to the format the client understands.
	 *
	 * <p>Handles the classic {@code &a} codes as well as {@code &#FF00AA} hex colors.
	 *
	 * @param input Start string with color and formatting codes in it
	 * @return String with the color and formatting codes translated
	 */
	public static String applyColors(String input) {
		return Colors.translate(input);
	}


	/**
	 * Format the currency amount with the characters before and after.
	 * @param amount Amount of money to format
	 * @return Currency character format string
	 */
	public static String formatCurrency(double amount) {
		String before = config.getString("moneyCharacter");
		before = before.replace(AreaShop.currencyEuro, "€");
		String after = config.getString("moneyCharacterAfter");
		after = after.replace(AreaShop.currencyEuro, "€");
		String result;
		// Check for infinite and NaN
		if(Double.isInfinite(amount)) {
			result = "\u221E"; // Infinite symbol
		} else if(Double.isNaN(amount)) {
			result = "NaN";
		} else {
			BigDecimal bigDecimal = BigDecimal.valueOf(amount);
			boolean stripTrailingZeros = false;
			int fractionalNumber = config.getInt("fractionalNumbers");
			// Add metric suffix if necessary
			if(config.getDouble("metricSuffixesAbove") != -1) {
				String suffix = null;
				double divider = 1;
				for(Double number : suffixes.keySet()) {
					if(amount >= number && number > divider) {
						divider = number;
						suffix = suffixes.get(number);
					}
				}
				if(suffix != null) {
					bigDecimal = BigDecimal.valueOf(amount / divider);
					after = suffix + after;
					fractionalNumber = config.getInt("fractionalNumbersShort");
					stripTrailingZeros = true;
				}
			}

			// Round if necessary
			if(fractionalNumber >= 0) {
				bigDecimal = bigDecimal.setScale(fractionalNumber, RoundingMode.HALF_UP);
			}
			result = bigDecimal.toString();
			if(config.getBoolean("hideEmptyFractionalPart")) {
				// Strip zero fractional: 12.00 -> 12
				if(bigDecimal.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0 && result.contains(".")) {
					result = result.substring(0, result.indexOf('.'));
				}
				// Strip zeros from suffixed numbers: 1.20M -> 1.2M
				if(stripTrailingZeros && result.contains(".")) {
					result = result.replaceAll("0+$", "");
				}
			}
		}
		result = result.replace(".", config.getString("decimalMark"));
		Message resultMessage = Message.fromString(result);
		resultMessage.prepend(before);
		resultMessage.append(after);
		return resultMessage.getSingle();
	}


	/**
	 * Checks if the string is a correct time period.
	 * @param time String that has to be checked
	 * @return true if format is correct, false if not
	 */
	public static boolean checkTimeFormat(String time) {
		// Check if the string is not empty and check the length
		if(time == null || time.length() <= 1 || time.indexOf(' ') == -1 || time.indexOf(' ') >= (time.length() - 1)) {
			return false;
		}

		// Check if the suffix is one of these values
		String suffix = time.substring(time.indexOf(' ') + 1);
		if(!identifiers.contains(suffix)) {
			return false;
		}

		// check if the part before the space is a number
		String prefix = time.substring(0, (time.indexOf(' ')));
		return prefix.matches("\\d+");
	}

	/**
	 * Methode to tranlate a duration string to a millisecond value.
	 * @param duration The duration string
	 * @return The duration in milliseconds translated from the durationstring, or if it is invalid then 0
	 */
	public static long durationStringToLong(String duration) {
		if(duration == null) {
			return 0;
		} else if(duration.equalsIgnoreCase("disabled") || duration.equalsIgnoreCase("unlimited") || duration.isEmpty()) {
			return -1;
		} else if(duration.indexOf(' ') == -1) {
			return 0;
		}
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(0);

		String durationString = duration.substring(duration.indexOf(' ') + 1);
		int durationInt = 0;
		try {
			durationInt = Integer.parseInt(duration.substring(0, duration.indexOf(' ')));
		} catch(NumberFormatException exception) {
			// No Number found, add zero
		}

		if(seconds.contains(durationString)) {
			calendar.add(Calendar.SECOND, durationInt);
		} else if(minutes.contains(durationString)) {
			calendar.add(Calendar.MINUTE, durationInt);
		} else if(hours.contains(durationString)) {
			calendar.add(Calendar.HOUR, durationInt);
		} else if(days.contains(durationString)) {
			calendar.add(Calendar.DAY_OF_MONTH, durationInt);
		} else if(weeks.contains(durationString)) {
			calendar.add(Calendar.DAY_OF_MONTH, durationInt * 7);
		} else if(months.contains(durationString)) {
			calendar.add(Calendar.MONTH, durationInt);
		} else if(years.contains(durationString)) {
			calendar.add(Calendar.YEAR, durationInt);
		} else {
			AreaShop.warn("Unknown duration indicator:", durationString, "check if config.yml has the correct time indicators");
		}
		return calendar.getTimeInMillis();
	}

	// LEGACY TIME INPUT CONVERSION

	/**
	 * Get setting from config that could be only a number indicating seconds.
	 * or a string indicating a duration string
	 * @param path Path of the setting to read
	 * @return milliseconds that the setting indicates
	 */
	public static long getDurationFromSecondsOrString(String path) {
		if(config.isLong(path) || config.isInt(path)) {
			long setting = config.getLong(path);
			if(setting != -1) {
				setting *= 1000;
			}
			return setting;
		} else {
			return durationStringToLong(config.getString(path));
		}
	}

	/**
	 * Get setting from config that could be only a number indicating minutes.
	 * or a string indicating a duration string.
	 * @param path Path of the setting to read
	 * @return milliseconds that the setting indicates
	 */
	public static long getDurationFromMinutesOrString(String path) {
		if(config.isLong(path) || config.isInt(path)) {
			long setting = config.getLong(path);
			if(setting != -1) {
				setting *= 60 * 1000;
			}
			return setting;
		} else {
			return durationStringToLong(config.getString(path));
		}
	}

	/**
	 * Parse a time setting that could be minutes or a duration string.
	 * @param input The string to parse
	 * @return milliseconds that the string indicates
	 */
	public static long getDurationFromMinutesOrStringInput(String input) {
		long number;
		try {
			number = Long.parseLong(input);
			if(number != -1) {
				number *= 60 * 1000;
			}
			return number;
		} catch(NumberFormatException e) {
			return durationStringToLong(input);
		}
	}

	/**
	 * Parse a time setting that could be seconds or a duration string.
	 * @param input The string to parse
	 * @return seconds that the string indicates
	 */
	public static long getDurationFromSecondsOrStringInput(String input) {
		long number;
		try {
			number = Long.parseLong(input);
			if(number != -1) {
				number *= 1000;
			}
			return number;
		} catch(NumberFormatException e) {
			return durationStringToLong(input);
		}
	}

	/**
	 * Check if an input is numeric.
	 * @param input The input to check
	 * @return true if the input is numeric, otherwise false
	 */
	@SuppressWarnings("ResultOfMethodCallIgnored")
	public static boolean isNumeric(String input) {
		try {
			Integer.parseInt(input);
			return true;
		} catch(NumberFormatException ignored) {
			return false;
		}
	}

	/**
	 * Check if a string is a double.
	 * @param input The input
	 * @return true if the input is a double, otherwise false
	 */
	@SuppressWarnings("ResultOfMethodCallIgnored")
	public static boolean isDouble(String input) {
		try {
			Double.parseDouble(input);
			return true;
		} catch(NumberFormatException e) {
			return false;
		}
	}

	/** Used when a price expression cannot be evaluated, high enough that nobody buys by accident. */
	private static final double PRICE_FALLBACK = 99999999999.0;

	/**
	 * Evaluate string input to a number.
	 * @param input  The input string, a plain number or a math expression like {@code %volume% * 0.5}
	 * @param region The region to apply replacements for and use for logging
	 * @return double evaluated from the input, or a very high default when the input is not valid
	 */
	public static double evaluateToDouble(String input, GeneralRegion region) {
		// Replace variables
		String expression = Message.fromString(input).replacements(region).getPlain();

		// Check for simple number
		if(isDouble(expression)) {
			return Double.parseDouble(expression);
		}

		try {
			return Expression.evaluate(expression);
		} catch(Expression.ExpressionException e) {
			AreaShop.warn("Price of region", region.getName(), "is set with an invalid expression: '" + expression + "',", e.getMessage());
			return PRICE_FALLBACK;
		}
	}

	// NAME <-> UUID CONVERSION

	/**
	 * Conversion to name by uuid.
	 * @param uuid The uuid in string format
	 * @return the name of the player
	 */
	public static String toName(String uuid) {
		String result = "";
		if(uuid != null) {
			try {
				UUID parsed = UUID.fromString(uuid);
				result = toName(parsed);
			} catch(IllegalArgumentException e) {
				// Incorrect UUID
			}
		}
		return result;
	}

	/**
	 * Conversion to name by uuid object.
	 * @param uuid The uuid in string format
	 * @return the name of the player
	 */
	public static String toName(UUID uuid) {
		if(uuid == null) {
			return "";
		} else {
			String name = Bukkit.getOfflinePlayer(uuid).getName();
			if(name != null) {
				return name;
			}
			return "";
		}
	}

	/**
	 * Find a player by name, without inventing one that never played here.
	 *
	 * <p>{@link Bukkit#getOfflinePlayer(String)} hands back a player object for any name at all,
	 * with a made up id, which used to make a typo in a command silently add a player that does
	 * not exist. This only returns players the server actually knows.
	 *
	 * @param name The name of the player
	 * @return The player, or null when no player with that name has been on this server
	 */
	public static OfflinePlayer findOfflinePlayer(String name) {
		if(name == null || name.isBlank()) {
			return null;
		}

		Player online = Bukkit.getPlayerExact(name);
		if(online != null) {
			return online;
		}
		return Bukkit.getOfflinePlayerIfCached(name);
	}

	/**
	 * Conversion from name to uuid.
	 * @param name The name of the player
	 * @return The uuid of the player
	 */
	public static String toUniqueId(String name) {
		OfflinePlayer player = findOfflinePlayer(name);
		return player == null ? null : player.getUniqueId().toString();
	}

}















