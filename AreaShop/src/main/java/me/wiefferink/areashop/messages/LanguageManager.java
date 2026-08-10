package me.wiefferink.areashop.messages;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads the language files and hands out the messages in them.
 *
 * <p>The bundled language files are written to the language folder on every startup (they carry a
 * header telling owners to copy them before editing), any other file already in that folder is left
 * alone so custom translations survive an update.
 */
public class LanguageManager {

	private static LanguageManager instance;

	private final JavaPlugin plugin;
	private final File languageFolder;
	private final List<String> chatPrefix;

	private YamlConfiguration currentLanguage;
	private YamlConfiguration fallbackLanguage;

	/** Keys that could not be found, tracked to log about each of them only once. */
	private final Set<String> missingKeys = new LinkedHashSet<>();

	/**
	 * Construct a LanguageManager, which registers itself as the one {@link Message} uses.
	 * @param plugin     The plugin, used to read the bundled language files
	 * @param folder     Name of the folder inside the plugin folder to keep language files in
	 * @param language   Name of the language to use (file name without '.yml')
	 * @param fallback   Name of the language to use for messages missing from the current one
	 * @param chatPrefix The prefix to put in front of prefixed messages
	 */
	public LanguageManager(JavaPlugin plugin, String folder, String language, String fallback, List<String> chatPrefix) {
		this.plugin = plugin;
		this.languageFolder = new File(plugin.getDataFolder(), folder);
		this.chatPrefix = chatPrefix == null ? Collections.emptyList() : chatPrefix;

		saveBundledLanguages();

		this.fallbackLanguage = load(fallback);
		if(this.fallbackLanguage == null) {
			Log.error("Could not load the fallback language file '" + fallback + ".yml', messages will be empty");
			this.fallbackLanguage = new YamlConfiguration();
		}

		if(language == null || language.equals(fallback)) {
			this.currentLanguage = this.fallbackLanguage;
		} else {
			this.currentLanguage = load(language);
			if(this.currentLanguage == null) {
				Log.warn("Could not load language file '" + language + ".yml', falling back to '" + fallback + "'");
				this.currentLanguage = this.fallbackLanguage;
			}
		}

		instance = this;
	}

	/**
	 * Get the LanguageManager that is currently in use.
	 * @return The active LanguageManager, or null when the plugin has not started yet
	 */
	public static LanguageManager getInstance() {
		return instance;
	}

	/**
	 * Get the message belonging to a key.
	 * @param key Key of the message
	 * @return The lines of the message, or null when the key is not known in any language
	 */
	public List<String> getLang(String key) {
		if(key == null) {
			return null;
		}

		List<String> result = read(currentLanguage, key);
		if(result == null) {
			result = read(fallbackLanguage, key);
		}
		if(result == null && missingKeys.add(key)) {
			Log.warn("Message '" + key + "' is missing from the language files");
		}
		return result;
	}

	/**
	 * Get the chat prefix as configured in config.yml.
	 * @return The lines forming the chat prefix
	 */
	public List<String> getChatPrefix() {
		return chatPrefix;
	}

	/**
	 * Read one key from a language file, accepting both a plain string and a list of strings.
	 * @param language The language file to read from
	 * @param key      The key to read
	 * @return The lines of the message, or null when not present
	 */
	private List<String> read(YamlConfiguration language, String key) {
		if(language == null || !language.isSet(key)) {
			return null;
		}
		if(language.isList(key)) {
			return language.getStringList(key);
		}
		String single = language.getString(key);
		return single == null ? null : List.of(single);
	}

	/**
	 * Load a language file from the language folder.
	 * @param language Name of the language (file name without '.yml')
	 * @return The loaded file, or null when it does not exist or could not be read
	 */
	private YamlConfiguration load(String language) {
		File file = new File(languageFolder, language + ".yml");
		if(!file.exists()) {
			return null;
		}
		try(Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
			return YamlConfiguration.loadConfiguration(reader);
		} catch(IOException | RuntimeException e) {
			Log.error("Could not read language file '" + file.getAbsolutePath() + "': " + e.getMessage());
			return null;
		}
	}

	/**
	 * Write the language files bundled with the plugin to the language folder, overwriting
	 * previous copies of them so translation updates arrive with a plugin update.
	 */
	private void saveBundledLanguages() {
		if(!languageFolder.exists() && !languageFolder.mkdirs()) {
			Log.error("Could not create the language folder: " + languageFolder.getAbsolutePath());
			return;
		}

		for(String language : bundledLanguages()) {
			try(InputStream input = plugin.getResource("lang/" + language + ".yml")) {
				if(input == null) {
					continue;
				}
				Files.copy(input, new File(languageFolder, language + ".yml").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} catch(IOException e) {
				Log.error("Could not write language file '" + language + ".yml': " + e.getMessage());
			}
		}
	}

	/**
	 * Find the languages bundled inside the plugin jar.
	 * @return Names of the bundled languages (file names without '.yml')
	 */
	private List<String> bundledLanguages() {
		List<String> result = new ArrayList<>();
		try {
			URI source = plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
			Path path = Path.of(source);
			if(Files.isDirectory(path)) {
				// Running from an exploded classpath, mostly during development
				try(var stream = Files.list(path.resolve("lang"))) {
					stream.filter(entry -> entry.getFileName().toString().endsWith(".yml"))
							.forEach(entry -> result.add(stripExtension(entry.getFileName().toString())));
				}
				return result;
			}

			try(JarFile jar = new JarFile(path.toFile())) {
				Enumeration<JarEntry> entries = jar.entries();
				while(entries.hasMoreElements()) {
					String name = entries.nextElement().getName();
					if(name.startsWith("lang/") && name.endsWith(".yml") && name.indexOf('/', 5) == -1) {
						result.add(stripExtension(name.substring(5)));
					}
				}
			}
		} catch(Exception e) {
			Log.error("Could not determine the bundled language files: " + e.getMessage());
		}
		return result;
	}

	private static String stripExtension(String fileName) {
		return fileName.substring(0, fileName.length() - ".yml".length());
	}

	/**
	 * Read a resource bundled with the plugin as UTF-8.
	 * @param resource Path of the resource inside the jar
	 * @return Reader for the resource, or null when it does not exist
	 */
	public Reader getBundledResource(String resource) {
		InputStream input = plugin.getResource(resource);
		return input == null ? null : new InputStreamReader(input, StandardCharsets.UTF_8);
	}
}
