package me.wiefferink.areashop.messages;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Converts the {@code [gold][bold]text[/bold]} markup used in the language files into
 * something the client understands.
 *
 * <p>Tags that are not recognized are left in the message untouched, which is what makes
 * things like {@code [AreaShop]}, {@code [group]} and {@code [page]} render as written.
 *
 * <p>Next to the named colors, a hex color can be used as a tag as well: {@code [#FF00AA]}.
 * The {@code &#FF00AA} notation works here too, it is handled by {@link Colors}.
 */
public final class Markup {

	/** Named colors, mapped to their legacy code. */
	private static final Map<String, Character> COLORS = Map.ofEntries(
			Map.entry("black", '0'),
			Map.entry("darkblue", '1'),
			Map.entry("darkgreen", '2'),
			Map.entry("darkaqua", '3'),
			Map.entry("darkred", '4'),
			Map.entry("darkpurple", '5'),
			Map.entry("gold", '6'),
			Map.entry("gray", '7'),
			Map.entry("grey", '7'),
			Map.entry("darkgray", '8'),
			Map.entry("darkgrey", '8'),
			Map.entry("blue", '9'),
			Map.entry("green", 'a'),
			Map.entry("aqua", 'b'),
			Map.entry("red", 'c'),
			Map.entry("lightpurple", 'd'),
			Map.entry("yellow", 'e'),
			Map.entry("white", 'f')
	);

	/** Named formats, mapped to their legacy code. */
	private static final Map<String, Character> FORMATS = Map.ofEntries(
			Map.entry("obfuscated", 'k'),
			Map.entry("magic", 'k'),
			Map.entry("bold", 'l'),
			Map.entry("strikethrough", 'm'),
			Map.entry("strike", 'm'),
			Map.entry("underline", 'n'),
			Map.entry("underlined", 'n'),
			Map.entry("italic", 'o')
	);

	private Markup() {
	}

	/**
	 * Check if a tag name is one AreaShop knows about.
	 * @param tag Tag name without brackets
	 * @return true when the tag will be interpreted instead of printed literally
	 */
	public static boolean isTag(String tag) {
		String name = tag.toLowerCase(Locale.ROOT);
		if(name.startsWith("/")) {
			name = name.substring(1);
		}
		return COLORS.containsKey(name)
				|| FORMATS.containsKey(name)
				|| "reset".equals(name)
				|| "break".equals(name)
				|| isHex(name);
	}

	private static boolean isHex(String name) {
		if(name.length() != 4 && name.length() != 7) {
			return false;
		}
		if(name.charAt(0) != '#') {
			return false;
		}
		for(int i = 1; i < name.length(); i++) {
			if(Character.digit(name.charAt(i), 16) == -1) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Convert markup into a string with {@code §} codes.
	 *
	 * <p>Because {@code §} codes cannot switch off a single format, closing tags are implemented
	 * by resetting and re-applying whatever is still active. That keeps {@code [gold][bold]x[/bold]y}
	 * rendering {@code y} as gold and no longer bold, as written.
	 *
	 * @param input Text with markup, may be null
	 * @return Text with {@code §} codes, or an empty string when the input was null
	 */
	public static String toLegacy(String input) {
		if(input == null) {
			return "";
		}

		// Handles '&a' and '&#FF00AA' written directly in the text
		String text = Colors.translate(input);

		StringBuilder result = new StringBuilder(text.length());
		// Current color as a full '§'-prefixed sequence (a hex color is more than one code)
		String color = "";
		// Active formats, insertion ordered so re-applying them is stable
		Set<Character> formats = new LinkedHashSet<>();

		int index = 0;
		while(index < text.length()) {
			char current = text.charAt(index);

			// Keep track of codes that were written directly, so closing tags behave predictably
			if(current == Colors.COLOR_CHAR && index + 1 < text.length()) {
				char code = Character.toLowerCase(text.charAt(index + 1));
				if(code == 'x' && index + 13 < text.length()) {
					color = text.substring(index, index + 14);
					formats.clear();
					result.append(color);
					index += 14;
					continue;
				}
				if(code == 'r') {
					color = "";
					formats.clear();
				} else if(FORMATS.containsValue(code)) {
					formats.add(code);
				} else {
					color = String.valueOf(Colors.COLOR_CHAR) + code;
					formats.clear();
				}
				result.append(Colors.COLOR_CHAR).append(code);
				index += 2;
				continue;
			}

			if(current != '[') {
				result.append(current);
				index++;
				continue;
			}

			int end = text.indexOf(']', index);
			if(end == -1) {
				result.append(current);
				index++;
				continue;
			}

			String tag = text.substring(index + 1, end);
			if(!isTag(tag)) {
				// Not ours, print it as written
				result.append(current);
				index++;
				continue;
			}

			String name = tag.toLowerCase(Locale.ROOT);
			boolean closing = name.startsWith("/");
			if(closing) {
				name = name.substring(1);
			}

			if("break".equals(name)) {
				result.append('\n');
			} else if("reset".equals(name)) {
				color = "";
				formats.clear();
				result.append(Colors.COLOR_CHAR).append('r');
			} else if(FORMATS.containsKey(name)) {
				char code = FORMATS.get(name);
				if(closing) {
					formats.remove(code);
					result.append(reapply(color, formats));
				} else {
					formats.add(code);
					result.append(Colors.COLOR_CHAR).append(code);
				}
			} else {
				// A color, either named or hex
				String newColor;
				if(isHex(name)) {
					String hex = name.substring(1);
					if(hex.length() == 3) {
						hex = new String(new char[] {hex.charAt(0), hex.charAt(0), hex.charAt(1), hex.charAt(1), hex.charAt(2), hex.charAt(2)});
					}
					newColor = Colors.hexToLegacy(hex);
				} else {
					newColor = String.valueOf(Colors.COLOR_CHAR) + COLORS.get(name);
				}

				if(closing) {
					color = "";
					result.append(reapply(color, formats));
				} else {
					// A color code clears formatting, just like it does in vanilla
					color = newColor;
					formats.clear();
					result.append(newColor);
				}
			}

			index = end + 1;
		}

		return result.toString();
	}

	/**
	 * Convert markup straight into a component.
	 * @param input Text with markup, may be null
	 * @return Component representing the text
	 */
	public static net.kyori.adventure.text.Component toComponentOf(String input) {
		return Colors.toComponent(toLegacy(input));
	}

	/**
	 * Build the codes needed to get back to the given state after a reset.
	 * @param color   The color to re-apply, empty for none
	 * @param formats The formats to re-apply
	 * @return Codes starting with a reset
	 */
	private static String reapply(String color, Set<Character> formats) {
		StringBuilder result = new StringBuilder();
		result.append(Colors.COLOR_CHAR).append('r');
		result.append(color);
		for(char format : formats) {
			result.append(Colors.COLOR_CHAR).append(format);
		}
		return result.toString();
	}
}
