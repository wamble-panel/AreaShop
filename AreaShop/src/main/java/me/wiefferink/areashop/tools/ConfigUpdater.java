package me.wiefferink.areashop.tools;

import me.wiefferink.areashop.AreaShop;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adds settings that a plugin update brings along to the files a server already has.
 *
 * <p>AreaShop reads the files it ships with as defaults, so a new setting already works without the
 * server owner doing anything. What they would not get is any way to <em>see</em> it: their own
 * config.yml would still be the one from the version they installed first, with no mention of
 * anything added since.
 *
 * <p>Only sections that are missing are appended, with the comments that explain them. Everything
 * already in the file is left exactly as it is, and a copy is kept before anything is written.
 */
public final class ConfigUpdater {

	/** The setting that records which version of the plugin a file was written for. */
	private static final String VERSION_KEY = "version";

	private ConfigUpdater() {
	}

	/**
	 * Add the sections that are missing from a file the server has.
	 * @param plugin   The plugin, used to read the file it ships with
	 * @param target   The file on the server
	 * @param resource Name of the file inside the plugin
	 * @return The names of the sections that were added, empty when the file was already up to date
	 */
	public static List<String> update(JavaPlugin plugin, Path target, String resource) {
		if(!Files.exists(target)) {
			// A file that does not exist yet is written in full elsewhere, nothing to add to
			return List.of();
		}

		List<String> bundled = readResource(plugin, resource);
		if(bundled.isEmpty()) {
			return List.of();
		}

		List<String> current;
		Set<String> present;
		try {
			current = Files.readAllLines(target, StandardCharsets.UTF_8);
			present = YamlConfiguration.loadConfiguration(target.toFile()).getKeys(false);
		} catch(IOException | RuntimeException e) {
			AreaShop.warn("Could not read", target.getFileName().toString(), "to check it for new settings:", e.getMessage());
			return List.of();
		}

		// A file that could not be parsed reads as empty, and everything would look missing
		if(present.isEmpty()) {
			AreaShop.warn("Did not check", target.getFileName().toString(), "for new settings because it is empty or has errors in it");
			return List.of();
		}

		Map<String, List<String>> missing = ConfigSections.missing(ConfigSections.read(bundled), present);

		// The version the file was written for, so an owner can see their file was brought along
		String version = ConfigSections.getScalar(bundled, VERSION_KEY);
		boolean staleVersion = version != null && !version.equals(ConfigSections.getScalar(current, VERSION_KEY));

		if(missing.isEmpty() && !staleVersion) {
			return List.of();
		}

		if(!backup(target)) {
			return List.of();
		}

		List<String> updated = ConfigSections.append(current, missing);
		if(staleVersion) {
			updated = ConfigSections.setScalar(updated, VERSION_KEY, version);
		}

		try {
			Files.write(target, updated, StandardCharsets.UTF_8);
		} catch(IOException e) {
			AreaShop.warn("Could not write the new settings to", target.getFileName().toString() + ":", e.getMessage());
			return List.of();
		}

		if(missing.isEmpty()) {
			AreaShop.info("Noted in " + target.getFileName() + " that it is up to date with version " + version);
		} else {
			AreaShop.info("Added " + missing.size() + " new setting(s) to " + target.getFileName() + ": "
					+ Utils.createCommaSeparatedList(missing.keySet()));
		}
		return new ArrayList<>(missing.keySet());
	}

	/**
	 * Read the file that ships with the plugin.
	 * @param plugin   The plugin to read from
	 * @param resource Name of the file inside the plugin
	 * @return The lines of the file, empty when it could not be read
	 */
	private static List<String> readResource(JavaPlugin plugin, String resource) {
		List<String> result = new ArrayList<>();
		try(InputStream input = plugin.getResource(resource)) {
			if(input == null) {
				return result;
			}
			try(BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
				String line;
				while((line = reader.readLine()) != null) {
					result.add(line);
				}
			}
		} catch(IOException e) {
			AreaShop.warn("Could not read the bundled", resource + ":", e.getMessage());
		}
		return result;
	}

	/**
	 * Keep a copy of a file before changing it.
	 * @param target The file to copy
	 * @return true when the copy was made
	 */
	private static boolean backup(Path target) {
		String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
		Path backup = target.resolveSibling(target.getFileName() + ".backup-" + stamp);
		try {
			Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
			AreaShop.info("Kept a copy of your old " + target.getFileName() + " as " + backup.getFileName());
			return true;
		} catch(IOException e) {
			AreaShop.warn("Could not back up", target.getFileName().toString(),
					"so it was left alone, the new settings still work through their default values:", e.getMessage());
			return false;
		}
	}
}
