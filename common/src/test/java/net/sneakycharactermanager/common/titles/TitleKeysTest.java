package net.sneakycharactermanager.common.titles;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TitleKeysTest {

	@Test
	void normalizesSpacesHyphensAndCase() {
		assertEquals("master_fisherman", TitleKeys.normalize("  Master Fisherman  "));
		assertEquals("master_fisherman", TitleKeys.normalize("Master-Fisherman"));
	}

	@Test
	void stripsInvalidCharactersAndCollapsesUnderscores() {
		assertEquals("a_bc", TitleKeys.normalize("a__b!!c"));
		assertEquals("title1", TitleKeys.normalize("___Title1___"));
	}

	@Test
	void emptyAfterCleanup() {
		assertEquals("", TitleKeys.normalize("!!!"));
		assertEquals("", TitleKeys.normalize("   "));
		assertEquals("", TitleKeys.normalize(null));
	}
}
