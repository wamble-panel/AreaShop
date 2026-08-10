package me.wiefferink.areashop;

import me.wiefferink.areashop.messages.Colors;
import me.wiefferink.areashop.messages.Markup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for the color codes server owners can write in config.yml and the language files.
 */
class ColorsTest {

	@Test
	void translatesClassicCodes() {
		assertEquals("§aHello", Colors.translate("&aHello"));
		assertEquals("§aHi", Colors.translate("&AHi"), "codes should not be case sensitive");
		assertEquals("§l§nBoth", Colors.translate("&l&nBoth"));
	}

	@Test
	void translatesHexColors() {
		assertEquals("§x§f§f§0§0§a§aHi", Colors.translate("&#FF00AAHi"));
		assertEquals("§x§f§f§0§0§a§aHi", Colors.translate("&#ff00aaHi"));
		assertEquals("§x§f§f§0§0§a§aHi", Colors.translate("&#F0AHi"), "three digits are shorthand");
	}

	@Test
	void leavesLoneAmpersandAlone() {
		assertEquals("Tom & Jerry", Colors.translate("Tom & Jerry"));
		assertEquals("100&% sure", Colors.translate("100&% sure"));
	}

	@Test
	void handlesNull() {
		assertNull(Colors.translate(null));
		assertNull(Colors.strip(null));
	}

	@Test
	void stripsAllColors() {
		assertEquals("Green hex", Colors.strip("&aGreen &#FF00AAhex"));
	}

	@Test
	void hexSurvivesTheTripToAComponentAndBack() {
		assertEquals("§x§f§f§0§0§a§atest", Colors.toLegacy(Markup.toComponentOf("&#FF00AAtest")));
	}
}
