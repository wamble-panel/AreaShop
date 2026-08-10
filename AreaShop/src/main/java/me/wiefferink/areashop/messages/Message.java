package me.wiefferink.areashop.messages;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.ConsoleCommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A message on its way to a player or the console.
 *
 * <p>A message is a list of lines. A line is either a piece of text, or an instruction that adds
 * interactivity to the text before it:
 * <pre>
 * command:
 *   - "[bold]%0%[/bold]"
 *   - "    hover: %lang:action|Use %1%|%"
 *   - "    command: %1%"
 * </pre>
 *
 * <p>Text can contain:
 * <ul>
 *     <li>Markup: {@code [gold]}, {@code [bold]}, {@code [/bold]}, {@code [#FF00AA]}, {@code [break]}</li>
 *     <li>Color codes: {@code &a} and {@code &#FF00AA}</li>
 *     <li>Numbered variables: {@code %0%}, {@code %1%}, filled in by {@link #replacements(Object...)}</li>
 *     <li>Named variables: {@code %region%}, filled in by a {@link ReplacementProvider} replacement</li>
 *     <li>References to other messages: {@code %lang:key%} and {@code %lang:key|argument|%}</li>
 * </ul>
 */
public class Message {

	public static final String VARIABLE_START = "%";
	public static final String VARIABLE_END = "%";

	/** Prefix marking a variable as a reference to another message. */
	private static final String LANGUAGE_PREFIX = "lang:";

	/** Key of the message that holds the chat prefix. */
	private static final String CHAT_PREFIX_KEY = "prefix";

	/** Separates the arguments of a message reference. */
	private static final char ARGUMENT_SEPARATOR = '|';

	/** Guards against a message that (indirectly) refers to itself. */
	private static final int MAXIMUM_DEPTH = 10;

	/** An instruction that adds interactivity to the text before it. */
	private static final Pattern INSTRUCTION = Pattern.compile("^\\s+(hover|command|suggest|link|insert):\\s?(.*)$");

	/** Characters a variable name can be built from, anything else means it is not a variable. */
	private static final Pattern VARIABLE_NAME = Pattern.compile("[a-zA-Z0-9_.\\-]+");

	/**
	 * The message, where each part is either a raw line (String) or lines that have already
	 * been processed (List of String).
	 */
	private final List<Object> parts = new ArrayList<>();

	/** Set from the 'useFancyMessages' config option. */
	private static boolean fancyMessages = true;

	/** Set from the 'useColorsInConsole' config option. */
	private static boolean colorsInConsole = false;

	private Object[] replacements = new Object[0];
	private boolean languageReplacements = true;
	private boolean usePrefix = false;

	private Message() {
	}

	/**
	 * Set if messages may use hover text and clickable parts.
	 * @param fancyMessages false to send messages as plain text
	 */
	public static void setFancyMessages(boolean fancyMessages) {
		Message.fancyMessages = fancyMessages;
	}

	/**
	 * Set if messages sent to the console keep their colors.
	 * @param colorsInConsole true to keep colors in console and log files
	 */
	public static void setColorsInConsole(boolean colorsInConsole) {
		Message.colorsInConsole = colorsInConsole;
	}

	// ---------------------------------------------------------------------------------------------
	// Construction
	// ---------------------------------------------------------------------------------------------

	/**
	 * Start with an empty message.
	 * @return An empty message
	 */
	public static Message empty() {
		return new Message();
	}

	/**
	 * Start with the message belonging to a key in the language file.
	 * @param key Key of the message
	 * @return The message, empty when the key is not known
	 */
	public static Message fromKey(String key) {
		Message result = new Message();
		LanguageManager languageManager = LanguageManager.getInstance();
		if(languageManager == null) {
			Log.warn("Tried to use message '" + key + "' before the language files were loaded");
			return result;
		}
		if(CHAT_PREFIX_KEY.equals(key)) {
			result.parts.addAll(languageManager.getChatPrefix());
			return result;
		}
		List<String> lines = languageManager.getLang(key);
		if(lines != null) {
			result.parts.addAll(lines);
		}
		return result;
	}

	/**
	 * Start with a piece of text.
	 * @param text The text, may be null
	 * @return The message
	 */
	public static Message fromString(String text) {
		Message result = new Message();
		if(text != null) {
			result.parts.add(text);
		}
		return result;
	}

	/**
	 * Start with a list of lines.
	 * @param lines The lines, may be null
	 * @return The message
	 */
	public static Message fromList(List<String> lines) {
		Message result = new Message();
		if(lines != null) {
			result.parts.addAll(lines);
		}
		return result;
	}

	// ---------------------------------------------------------------------------------------------
	// Building
	// ---------------------------------------------------------------------------------------------

	/**
	 * Set the values to fill in for the numbered variables.
	 *
	 * <p>A replacement that implements {@link ReplacementProvider} also fills in named variables
	 * like {@code %region%}, a replacement that is a {@link Message} is inserted with its own
	 * interactivity intact.
	 *
	 * @param replacements The values to use
	 * @return this
	 */
	public Message replacements(Object... replacements) {
		this.replacements = replacements == null ? new Object[0] : replacements;
		return this;
	}

	/**
	 * Do not resolve {@code %lang:key%} references in this message.
	 *
	 * <p>Used for text that is not meant for a player, like commands to execute, where a stray
	 * percent sign should stay exactly as it is.
	 *
	 * @return this
	 */
	public Message noLanguageReplacements() {
		this.languageReplacements = false;
		return this;
	}

	/**
	 * Put the chat prefix in front of this message.
	 * @return this
	 */
	public Message prefix() {
		return prefix(true);
	}

	/**
	 * Set if the chat prefix should be put in front of this message.
	 * @param usePrefix true to add the prefix
	 * @return this
	 */
	public Message prefix(boolean usePrefix) {
		this.usePrefix = usePrefix;
		return this;
	}

	/**
	 * Add text to the end of this message.
	 * @param text The text to add
	 * @return this
	 */
	public Message append(String text) {
		if(text != null) {
			parts.add(text);
		}
		return this;
	}

	/**
	 * Add another message to the end of this message, keeping its replacements and interactivity.
	 * @param message The message to add
	 * @return this
	 */
	public Message append(Message message) {
		if(message != null) {
			parts.add(message.get());
		}
		return this;
	}

	/**
	 * Add text to the start of this message.
	 * @param text The text to add
	 * @return this
	 */
	public Message prepend(String text) {
		if(text != null) {
			parts.add(0, text);
		}
		return this;
	}

	/**
	 * Add another message to the start of this message, keeping its replacements and interactivity.
	 * @param message The message to add
	 * @return this
	 */
	public Message prepend(Message message) {
		if(message != null) {
			parts.add(0, message.get());
		}
		return this;
	}

	// ---------------------------------------------------------------------------------------------
	// Output
	// ---------------------------------------------------------------------------------------------

	/**
	 * Check if this message would produce anything at all.
	 * @return true when there is nothing to send
	 */
	public boolean isEmpty() {
		return parts.isEmpty();
	}

	/**
	 * Get the lines of this message with all variables filled in, markup still intact.
	 * @return The lines of this message
	 */
	public List<String> get() {
		List<String> result = new ArrayList<>();
		if(usePrefix) {
			result.addAll(fromKey(CHAT_PREFIX_KEY).processAll(0));
		}
		result.addAll(processAll(0));
		return result;
	}

	/**
	 * Get this message as readable text, without any formatting.
	 * @return The message as plain text
	 */
	public String getPlain() {
		return Colors.toPlain(toComponent());
	}

	/**
	 * Get this message as a single string with {@code §} color codes, hex colors included.
	 *
	 * <p>Interactivity is lost, this is meant for places that only take a string, like a command
	 * to run or a value to write back into a config file.
	 *
	 * @return The message as one string
	 */
	public String getSingle() {
		return Colors.toLegacy(toComponent());
	}

	/**
	 * Build this message into a component.
	 * @return The message as a component
	 */
	public Component toComponent() {
		TextComponent.Builder result = Component.text();
		Component segment = null;
		List<String> hovers = new ArrayList<>();
		ClickEvent<?> click = null;
		String insertion = null;

		for(String line : get()) {
			Matcher matcher = INSTRUCTION.matcher(line);
			if(matcher.matches() && segment != null) {
				String instruction = matcher.group(1).toLowerCase(Locale.ROOT);
				String value = matcher.group(2);
				switch(instruction) {
					case "hover" -> hovers.add(value);
					case "command" -> click = ClickEvent.runCommand(Colors.toPlain(Markup.toComponentOf(value)));
					case "suggest" -> click = ClickEvent.suggestCommand(Colors.toPlain(Markup.toComponentOf(value)));
					case "link" -> click = ClickEvent.openUrl(Colors.toPlain(Markup.toComponentOf(value)));
					case "insert" -> insertion = Colors.toPlain(Markup.toComponentOf(value));
					default -> Log.warn("Unknown message instruction: " + instruction);
				}
				continue;
			}

			// A new piece of text, so the previous one is finished
			if(segment != null) {
				result.append(decorate(segment, hovers, click, insertion));
				hovers.clear();
				click = null;
				insertion = null;
			}
			segment = Markup.toComponentOf(line);
		}

		if(segment != null) {
			result.append(decorate(segment, hovers, click, insertion));
		}
		return result.build();
	}

	/**
	 * Attach the collected interactivity to a piece of text.
	 * @param segment   The text
	 * @param hovers    Lines to show when hovering over the text
	 * @param click     What to do when the text is clicked, may be null
	 * @param insertion Text to insert in the chat box on shift-click, may be null
	 * @return The text with interactivity attached
	 */
	private Component decorate(Component segment, List<String> hovers, ClickEvent<?> click, String insertion) {
		Component result = segment;
		if(!fancyMessages) {
			// Server owner asked for plain messages, so only the text survives
			return result;
		}
		if(!hovers.isEmpty()) {
			TextComponent.Builder hover = Component.text();
			for(int i = 0; i < hovers.size(); i++) {
				if(i > 0) {
					hover.append(Component.newline());
				}
				hover.append(Markup.toComponentOf(hovers.get(i)));
			}
			result = result.hoverEvent(hover.build());
		}
		if(click != null) {
			result = result.clickEvent(click);
		}
		if(insertion != null) {
			result = result.insertion(insertion);
		}
		return result;
	}

	/**
	 * Send this message to a player or the console.
	 * @param target The receiver, nothing happens when this is null
	 */
	public void send(Object target) {
		if(target == null || isEmpty()) {
			return;
		}
		if(target instanceof Audience audience) {
			Component component = toComponent();
			if(Colors.toPlain(component).isEmpty()) {
				return;
			}
			if(!colorsInConsole && target instanceof ConsoleCommandSender) {
				component = Component.text(Colors.toPlain(component));
			}
			audience.sendMessage(component);
			return;
		}
		Log.warn("Cannot send a message to a " + target.getClass().getName());
	}

	@Override
	public String toString() {
		return getSingle();
	}

	// ---------------------------------------------------------------------------------------------
	// Processing
	// ---------------------------------------------------------------------------------------------

	/**
	 * Fill in all variables of one line, which can result in more lines when a message reference
	 * or a message replacement is inserted.
	 * @param line  The line to process
	 * @param depth How many message references deep we are
	 * @return The resulting lines
	 */
	private List<String> processLine(String line, int depth) {
		List<List<String>> inserted = new ArrayList<>();
		String withVariables = fillVariables(line, inserted, depth);
		return splice(withVariables, inserted, depth);
	}

	/** Marks the place where a whole message has to be inserted, cannot occur in real text. */
	private static final char MARKER = '\u0000';

	/**
	 * Build the marker for a message that has to be inserted later.
	 * @param index Position in the list of messages waiting to be inserted
	 * @return The marker to put in the text
	 */
	private static String marker(int index) {
		return MARKER + Integer.toString(index) + MARKER;
	}

	/**
	 * Replace {@code %0%} and {@code %region%} style variables by their value.
	 *
	 * <p>References to other messages ({@code %lang:key%}) are left alone here, so that variables
	 * used as their arguments are resolved first. Replacements that are a whole message are put
	 * aside and replaced by a marker, they are inserted in {@link #splice}.
	 *
	 * @param line     The line to process
	 * @param inserted Collects the messages that have to be inserted later
	 * @param depth    How many message references deep we are
	 * @return The line with variables filled in
	 */
	private String fillVariables(String line, List<List<String>> inserted, int depth) {
		StringBuilder result = new StringBuilder(line.length());
		int index = 0;
		while(index < line.length()) {
			char current = line.charAt(index);
			if(current != '%') {
				result.append(current);
				index++;
				continue;
			}

			int end = line.indexOf('%', index + 1);
			if(end == -1) {
				result.append(current);
				index++;
				continue;
			}

			String name = line.substring(index + 1, end);
			// Leave message references for the next step, their arguments are resolved on the way
			if(name.startsWith(LANGUAGE_PREFIX) || !VARIABLE_NAME.matcher(name).matches()) {
				result.append(current);
				index++;
				continue;
			}

			Object value = resolveVariable(name);
			if(value == null) {
				// Not a variable we know, leave it as written
				result.append(current);
				index++;
				continue;
			}

			if(value instanceof Message message) {
				inserted.add(depth >= MAXIMUM_DEPTH ? List.of() : message.get());
				result.append(marker(inserted.size() - 1));
			} else {
				result.append(value);
			}
			index = end + 1;
		}
		return result.toString();
	}

	/**
	 * Find the value of a numbered or named variable.
	 * @param name Name of the variable
	 * @return The value, or null when nothing provides this variable
	 */
	private Object resolveVariable(String name) {
		// Numbered variables refer to the replacements by position
		if(isNumber(name)) {
			int position = Integer.parseInt(name);
			if(position >= 0 && position < replacements.length) {
				Object replacement = replacements[position];
				return replacement == null ? "" : replacement;
			}
			return null;
		}

		// Named variables are provided by the replacements that can do so
		for(Object replacement : replacements) {
			if(replacement instanceof ReplacementProvider provider) {
				Object value = provider.provideReplacement(name);
				if(value != null) {
					return value;
				}
			}
		}
		return null;
	}

	private static boolean isNumber(String input) {
		if(input.isEmpty()) {
			return false;
		}
		for(int i = 0; i < input.length(); i++) {
			if(!Character.isDigit(input.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Insert referenced messages and message replacements, splitting the line into segments
	 * around them so their interactivity keeps working.
	 * @param line     The line with variables already filled in
	 * @param inserted The messages collected by {@link #fillVariables}
	 * @param depth    How many message references deep we are
	 * @return The resulting lines
	 */
	private List<String> splice(String line, List<List<String>> inserted, int depth) {
		List<String> result = new ArrayList<>();
		StringBuilder current = new StringBuilder(line.length());

		int index = 0;
		while(index < line.length()) {
			char character = line.charAt(index);

			if(character == MARKER) {
				int end = line.indexOf(MARKER, index + 1);
				if(end == -1) {
					index++;
					continue;
				}
				int position = Integer.parseInt(line.substring(index + 1, end));
				addSegment(result, current);
				result.addAll(inserted.get(position));
				index = end + 1;
				continue;
			}

			if(languageReplacements && line.startsWith(VARIABLE_START + LANGUAGE_PREFIX, index)) {
				Reference reference = readReference(line, index);
				if(reference != null) {
					addSegment(result, current);
					if(depth < MAXIMUM_DEPTH) {
						Message referenced = fromKey(reference.key)
								.replacements(buildArguments(reference.arguments, inserted, depth));
						referenced.languageReplacements = languageReplacements;
						result.addAll(referenced.processAll(depth + 1));
					} else {
						Log.warn("Message '" + reference.key + "' refers to itself, stopped resolving it");
					}
					index = reference.end;
					continue;
				}
			}

			current.append(character);
			index++;
		}

		if(current.length() > 0 || result.isEmpty()) {
			result.add(current.toString());
		}
		return result;
	}

	/**
	 * Process all parts of this message at a given depth.
	 * @param depth How many message references deep we are
	 * @return The resulting lines
	 */
	private List<String> processAll(int depth) {
		List<String> result = new ArrayList<>();
		for(Object part : parts) {
			if(part instanceof List<?> processed) {
				for(Object partLine : processed) {
					result.add(String.valueOf(partLine));
				}
				continue;
			}

			String line = String.valueOf(part);
			Matcher instruction = INSTRUCTION.matcher(line);
			if(instruction.matches()) {
				// The value of an instruction has to stay one piece of text, a referenced message
				// used as hover text cannot bring its own hover text along
				result.add("    " + instruction.group(1) + ": " + flatten(processLine(instruction.group(2), depth)));
			} else {
				result.addAll(processLine(line, depth));
			}
		}
		return result;
	}

	/**
	 * Squash lines into one piece of text, dropping interactivity.
	 * @param lines The lines to squash
	 * @return The text of all lines glued together
	 */
	private static String flatten(List<String> lines) {
		StringBuilder result = new StringBuilder();
		for(String line : lines) {
			if(!INSTRUCTION.matcher(line).matches()) {
				result.append(line);
			}
		}
		return result.toString();
	}

	/**
	 * Resolve the arguments of a message reference in the context of this message.
	 * @param arguments The arguments as written
	 * @param depth     How many message references deep we are
	 * @return The values to use as replacements of the referenced message
	 */
	private Object[] buildArguments(List<String> arguments, List<List<String>> inserted, int depth) {
		List<Object> result = new ArrayList<>(arguments.size() + replacements.length);
		for(String argument : arguments) {
			// Variables in the arguments are already filled in, only references are left to resolve
			result.add(flatten(splice(argument, inserted, depth + 1)));
		}
		// Keep the providers around, so a referenced message can use '%region%' as well
		for(Object replacement : replacements) {
			if(replacement instanceof ReplacementProvider) {
				result.add(replacement);
			}
		}
		return result.toArray();
	}

	/**
	 * A parsed {@code %lang:key|argument|%} reference.
	 */
	private record Reference(String key, List<String> arguments, int end) {
	}

	/**
	 * Read a message reference starting at the given position.
	 * @param line  The line to read from
	 * @param start Position of the opening percent sign
	 * @return The parsed reference, or null when it is not a valid one
	 */
	private static Reference readReference(String line, int start) {
		int keyStart = start + VARIABLE_START.length() + LANGUAGE_PREFIX.length();
		int index = keyStart;
		while(index < line.length() && line.charAt(index) != '%' && line.charAt(index) != ARGUMENT_SEPARATOR) {
			index++;
		}
		if(index >= line.length()) {
			return null;
		}

		String key = line.substring(keyStart, index);
		if(key.isEmpty()) {
			return null;
		}

		// '%lang:key%', without arguments
		if(line.charAt(index) == '%') {
			return new Reference(key, List.of(), index + 1);
		}

		// '%lang:key|argument|argument|%', arguments run until the closing '|%'
		int end = line.indexOf(ARGUMENT_SEPARATOR + VARIABLE_END, index);
		if(end == -1) {
			return null;
		}
		List<String> arguments = Arrays.asList(line.substring(index + 1, end).split("\\" + ARGUMENT_SEPARATOR, -1));
		return new Reference(key, arguments, end + 2);
	}

	/**
	 * Add the text built so far as a segment, if there is any.
	 * @param result  The lines built so far
	 * @param current The text built so far, reset by this method
	 */
	private static void addSegment(List<String> result, StringBuilder current) {
		if(current.length() > 0) {
			result.add(current.toString());
			current.setLength(0);
		}
	}
}
