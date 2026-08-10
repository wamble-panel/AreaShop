package me.wiefferink.areashop.messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translation of color codes written by server owners into something the client understands.
 *
 * <p>Two notations are accepted everywhere AreaShop reads text (config.yml, default.yml,
 * language files, sign profiles and command input):
 * <ul>
 *     <li>The classic single character codes: {@code &a}, {@code &l}, {@code &r}, ...</li>
 *     <li>Full RGB hex colors: {@code &#FF00AA} (case insensitive, and {@code &#f0a} shorthand)</li>
 * </ul>
 */
public final class Colors {

	/** The character Minecraft itself uses to mark up text. */
	public static final char COLOR_CHAR = '§';

	/** The character server owners use to mark up text. */
	public static final char ALTERNATE_COLOR_CHAR = '&';

	/** Characters that are valid directly after a color character. */
	private static final String LEGACY_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

	/**
	 * Matches {@code &#FF00AA} and the {@code &#F0A} shorthand.
	 */
	private static final Pattern HEX_PATTERN = Pattern.compile("[&§]#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})");

	/**
	 * Serializer that understands both the {@code §x§f§f§0§0§0§0} hex notation and plain codes.
	 */
	private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
			.character(COLOR_CHAR)
			.hexColors()
			.useUnusualXRepeatedCharacterHexFormat()
			.build();

	private Colors() {
	}

	/**
	 * Expand a hex color to the notation the legacy serializer and the vanilla client understand.
	 * @param hex Six hex digits, without a leading '#'
	 * @return The {@code §x§r§r§g§g§b§b} representation
	 */
	public static String hexToLegacy(String hex) {
		StringBuilder result = new StringBuilder(14);
		result.append(COLOR_CHAR).append('x');
		for(int i = 0; i < hex.length(); i++) {
			result.append(COLOR_CHAR).append(Character.toLowerCase(hex.charAt(i)));
		}
		return result.toString();
	}

	/**
	 * Expand the three digit hex shorthand to the regular six digit notation.
	 * @param hex Three or six hex digits
	 * @return Six hex digits
	 */
	private static String expandShorthand(String hex) {
		if(hex.length() != 3) {
			return hex;
		}
		return new String(new char[] {
				hex.charAt(0), hex.charAt(0),
				hex.charAt(1), hex.charAt(1),
				hex.charAt(2), hex.charAt(2)
		});
	}

	/**
	 * Translate all color codes an owner can write into the codes the client understands.
	 *
	 * <p>Handles {@code &#RRGGBB} hex colors first, so that the {@code &} of a hex color is never
	 * mistaken for a legacy code. A {@code &} that is not followed by a valid code is left alone,
	 * which keeps things like {@code Tom & Jerry} readable.
	 *
	 * @param input Text possibly containing color codes, may be null
	 * @return Text with all codes translated, or null when the input was null
	 */
	public static String translate(String input) {
		if(input == null) {
			return null;
		}

		// Hex colors first: '&#FF00AA' would otherwise be seen as the (invalid) legacy code '&#'
		StringBuilder hexTranslated = new StringBuilder(input.length());
		Matcher matcher = HEX_PATTERN.matcher(input);
		int last = 0;
		while(matcher.find()) {
			hexTranslated.append(input, last, matcher.start());
			hexTranslated.append(hexToLegacy(expandShorthand(matcher.group(1))));
			last = matcher.end();
		}
		hexTranslated.append(input, last, input.length());

		// Regular codes
		char[] result = hexTranslated.toString().toCharArray();
		for(int i = 0; i < result.length - 1; i++) {
			if(result[i] == ALTERNATE_COLOR_CHAR && LEGACY_CODES.indexOf(result[i + 1]) > -1) {
				result[i] = COLOR_CHAR;
				result[i + 1] = Character.toLowerCase(result[i + 1]);
			}
		}
		return new String(result);
	}

	/**
	 * Turn a string that already contains {@code §} codes into a component.
	 * @param legacy Text with {@code §} color codes
	 * @return Component representing the given text
	 */
	public static Component toComponent(String legacy) {
		return LEGACY_SERIALIZER.deserialize(legacy == null ? "" : legacy);
	}

	/**
	 * Turn a component back into a string with {@code §} codes, keeping hex colors intact.
	 * @param component Component to serialize
	 * @return Text with {@code §} color codes
	 */
	public static String toLegacy(Component component) {
		return LEGACY_SERIALIZER.serialize(component);
	}

	/**
	 * Turn a component into readable text without any formatting.
	 * @param component Component to serialize
	 * @return Text without any color codes
	 */
	public static String toPlain(Component component) {
		return PlainTextComponentSerializer.plainText().serialize(component);
	}

	/**
	 * Remove all color codes from a string.
	 * @param input Text possibly containing color codes, may be null
	 * @return Text without color codes, or null when the input was null
	 */
	public static String strip(String input) {
		if(input == null) {
			return null;
		}
		return toPlain(toComponent(translate(input)));
	}
}
