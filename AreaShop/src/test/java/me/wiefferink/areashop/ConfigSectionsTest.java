package me.wiefferink.areashop;

import me.wiefferink.areashop.tools.ConfigSections;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for adding the settings of a plugin update to a config file a server already has.
 *
 * <p>Getting this wrong would damage files that server owners have edited, so the rule it has to
 * keep is checked from both sides: everything that was there before is still there afterwards, and
 * everything that was missing has arrived.
 */
class ConfigSectionsTest {

	private static final List<String> BUNDLED = List.of(
			"# The prefix in front of messages.",
			"chatPrefix: '[AreaShop]'",
			"",
			"# The language to use.",
			"language: EN",
			"",
			"# Tags that put a sign in AreaShop.",
			"signTags:",
			"  rent: '[asrent]'",
			"  buy: '[asbuy]'",
			"",
			"# Letting players rent space out.",
			"sublet:",
			"  enabled: true",
			"  # How many may rent at once.",
			"  maxTenants: 0",
			"",
			"# Whether to send stats.",
			"sendStats: true"
	);

	@Test
	void splitsIntoSectionsWithTheirComments() {
		Map<String, List<String>> sections = ConfigSections.read(BUNDLED);

		assertEquals(List.of("chatPrefix", "language", "signTags", "sublet", "sendStats"),
				List.copyOf(sections.keySet()), "every top level key becomes a section, in order");

		assertEquals(List.of("# The language to use.", "language: EN"), sections.get("language"),
				"the comment above a key belongs to it");

		assertTrue(sections.get("signTags").contains("  rent: '[asrent]'"),
				"indented lines stay with the key above them");
		assertTrue(sections.get("sublet").contains("  # How many may rent at once."),
				"comments inside a section stay inside it");
	}

	@Test
	void findsOnlyTheSectionsThatAreMissing() {
		Map<String, List<String>> sections = ConfigSections.read(BUNDLED);
		Map<String, List<String>> missing = ConfigSections.missing(sections, Set.of("chatPrefix", "language", "signTags", "sendStats"));

		assertEquals(Set.of("sublet"), missing.keySet());
	}

	@Test
	void findsNothingWhenTheFileIsUpToDate() {
		Map<String, List<String>> sections = ConfigSections.read(BUNDLED);
		assertTrue(ConfigSections.missing(sections, sections.keySet()).isEmpty());
	}

	@Test
	void keepsEverythingTheServerAlreadyHad() {
		List<String> existing = List.of(
				"# My own note about this.",
				"chatPrefix: '&aMyServer'",
				"language: NL"
		);
		Map<String, List<String>> missing = ConfigSections.missing(ConfigSections.read(BUNDLED), Set.of("chatPrefix", "language"));
		List<String> merged = ConfigSections.append(existing, missing);

		for(String line : existing) {
			assertTrue(merged.contains(line), "line was lost: " + line);
		}
		assertEquals(existing, merged.subList(0, existing.size()), "what was there stays at the top, unchanged");
		assertTrue(merged.contains("sublet:"), "the missing section was added");
		assertTrue(merged.contains("# Letting players rent space out."), "its comment came along");
		assertTrue(merged.stream().anyMatch(line -> line.contains("Added by a plugin update")),
				"what was added is marked as such");
	}

	@Test
	void changesNothingWhenThereIsNothingToAdd() {
		List<String> existing = List.of("chatPrefix: '&aMyServer'", "language: NL");
		assertEquals(existing, ConfigSections.append(existing, Map.of()));
	}

	@Test
	void addingTwiceDoesNotAddTwice() {
		Map<String, List<String>> sections = ConfigSections.read(BUNDLED);
		List<String> once = ConfigSections.append(List.of("chatPrefix: 'x'"), ConfigSections.missing(sections, Set.of("chatPrefix")));

		// The second run sees the sections that the first one added, so it finds nothing to do
		Map<String, List<String>> stillMissing = ConfigSections.missing(sections, ConfigSections.read(once).keySet());
		assertTrue(stillMissing.isEmpty(), "a second startup should not append the same settings again");
	}

	@Test
	void writesTheVersionOfTheFile() {
		List<String> lines = List.of("chatPrefix: 'x'", "version: 2.6.0", "debug: false");
		List<String> updated = ConfigSections.setScalar(lines, "version", "2.7.0");

		assertEquals(List.of("chatPrefix: 'x'", "version: 2.7.0", "debug: false"), updated);
	}

	@Test
	void leavesTheFileAloneWhenItHasNoVersion() {
		List<String> lines = List.of("chatPrefix: 'x'", "debug: false");
		assertEquals(lines, ConfigSections.setScalar(lines, "version", "2.7.0"));
	}

	@Test
	void readsTheVersionOfTheFile() {
		assertEquals("2.6.0", ConfigSections.getScalar(List.of("version: 2.6.0"), "version"));
		assertEquals("2.6.0", ConfigSections.getScalar(List.of("version: '2.6.0'"), "version"));
		assertEquals("2.6.0", ConfigSections.getScalar(List.of("version: \"2.6.0\""), "version"));
		assertNull(ConfigSections.getScalar(List.of("debug: false"), "version"), "a file without it reads as nothing");
	}
}
