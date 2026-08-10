package me.wiefferink.areashop;

import me.wiefferink.areashop.messages.Colors;
import me.wiefferink.areashop.messages.Markup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the {@code [gold][bold]text[/bold]} markup used in the language files.
 */
class MarkupTest {

	@Test
	void appliesNamedColors() {
		assertEquals("§6hi", Markup.toLegacy("[gold]hi"));
		assertEquals("§7hi", Markup.toLegacy("[grey]hi"), "grey and gray are the same color");
		assertEquals("§7hi", Markup.toLegacy("[gray]hi"));
	}

	@Test
	void closingAFormatKeepsTheColor() {
		assertEquals("§6§lX§r§6Y", Markup.toLegacy("[gold][bold]X[/bold]Y"));
	}

	@Test
	void leavesUnknownTagsAsWritten() {
		assertEquals("[AreaShop] hi", Markup.toLegacy("[AreaShop] hi"));
		assertEquals("[group] [page]", Markup.toLegacy("[group] [page]"));
		assertEquals("[gold", Markup.toLegacy("[gold"), "an unclosed bracket is just text");
	}

	@Test
	void breakStartsANewLine() {
		assertEquals("a\nb", Markup.toLegacy("a[break]b"));
	}

	@Test
	void resetClearsEverything() {
		assertEquals("§6a§rb", Markup.toLegacy("[gold]a[reset]b"));
	}

	@Test
	void supportsHexColorsAsATag() {
		assertEquals("§x§f§f§0§0§a§ahi", Markup.toLegacy("[#FF00AA]hi"));
		assertEquals("§x§f§f§0§0§a§ahi", Markup.toLegacy("[#F0A]hi"));
	}

	@Test
	void mixesTagsAndColorCodes() {
		assertEquals("§l§x§f§f§0§0§a§ahi", Markup.toLegacy("[bold]&#FF00AAhi"));
	}

	@Test
	void handlesNull() {
		assertEquals("", Markup.toLegacy(null));
	}

	@Test
	void producesReadablePlainText() {
		assertEquals("hi there", Colors.toPlain(Markup.toComponentOf("[gold][bold]hi[/bold] there")));
	}
}
