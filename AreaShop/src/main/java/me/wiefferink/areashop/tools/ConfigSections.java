package me.wiefferink.areashop.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Splits a configuration file into its top level sections and puts missing ones back.
 *
 * <p>Works on the lines of a file rather than on parsed YAML, because the comments are the point:
 * a setting that arrives without the paragraph explaining it is not much use to a server owner.
 * Kept apart from the reading and writing of files so it can be tested on its own.
 */
public final class ConfigSections {

	/** Matches a line that starts a top level section, which is a key with nothing in front of it. */
	private static final String TOP_LEVEL_KEY = "^([A-Za-z0-9_\\-]+):.*$";

	/** Header put above the settings an update brings along. */
	private static final List<String> ADDED_HEADER = List.of(
			"# ┌──────────────────────────────────────────────────────────────────────────────────────┐",
			"# │ Added by a plugin update. Everything above was left exactly as you had it.           │",
			"# └──────────────────────────────────────────────────────────────────────────────────────┘"
	);

	private ConfigSections() {
	}

	/**
	 * Split a file into its top level sections, keeping the comments that belong to each.
	 *
	 * <p>The comment lines and blank lines directly above a key are taken to describe it, which is
	 * how every file AreaShop ships is written.
	 *
	 * @param lines The lines of the file
	 * @return The sections, by key, in the order they appear
	 */
	public static Map<String, List<String>> read(List<String> lines) {
		Map<String, List<String>> result = new LinkedHashMap<>();

		String key = null;
		List<String> section = new ArrayList<>();
		List<String> pending = new ArrayList<>();

		for(String line : lines) {
			if(line.matches(TOP_LEVEL_KEY)) {
				if(key != null) {
					result.put(key, trimBlanks(section));
				}
				key = line.substring(0, line.indexOf(':'));
				section = new ArrayList<>(pending);
				section.add(line);
				pending.clear();
				continue;
			}

			// Comments and blank lines are held back until we know which key they belong to
			if(line.isBlank() || line.stripLeading().startsWith("#")) {
				pending.add(line);
				continue;
			}

			// An indented line continues the section above it, so the held back lines are part of it
			if(key != null) {
				section.addAll(pending);
				section.add(line);
			}
			pending.clear();
		}

		if(key != null) {
			result.put(key, trimBlanks(section));
		}
		return result;
	}

	/**
	 * Find the sections of a newer file that an older one does not have.
	 * @param bundled The sections of the file that ships with the plugin
	 * @param present The top level keys the server already has
	 * @return The sections that are missing, in the order they appear in the bundled file
	 */
	public static Map<String, List<String>> missing(Map<String, List<String>> bundled, Set<String> present) {
		Map<String, List<String>> result = new LinkedHashMap<>();
		for(Map.Entry<String, List<String>> section : bundled.entrySet()) {
			if(!present.contains(section.getKey())) {
				result.put(section.getKey(), section.getValue());
			}
		}
		return result;
	}

	/**
	 * Put missing sections at the end of a file.
	 *
	 * <p>Nothing that is already there is changed, moved or removed, so a server owner keeps their
	 * settings, their comments and their ordering.
	 *
	 * @param current The lines the server has now
	 * @param missing The sections to add
	 * @return The lines of the file to write
	 */
	public static List<String> append(List<String> current, Map<String, List<String>> missing) {
		List<String> result = new ArrayList<>(current);
		if(missing.isEmpty()) {
			return result;
		}

		while(!result.isEmpty() && result.get(result.size() - 1).isBlank()) {
			result.remove(result.size() - 1);
		}

		result.add("");
		result.add("");
		result.addAll(ADDED_HEADER);
		for(List<String> section : missing.values()) {
			result.add("");
			result.addAll(section);
		}
		result.add("");
		return result;
	}

	/**
	 * Drop the blank lines around a section.
	 *
	 * <p>Appending puts its own blank line between sections, so carrying these along would only
	 * pile up empty lines in the file.
	 *
	 * @param section The lines of the section
	 * @return The section without blank lines at either end
	 */
	private static List<String> trimBlanks(List<String> section) {
		List<String> result = new ArrayList<>(section);
		while(!result.isEmpty() && result.get(result.size() - 1).isBlank()) {
			result.remove(result.size() - 1);
		}
		while(!result.isEmpty() && result.get(0).isBlank()) {
			result.remove(0);
		}
		return result;
	}
}
