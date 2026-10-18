package net.sneakycharactermanager.paper.handlers.titles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TitleKeyNormalizeTest {

	@Test
	void normalizesSpacesHyphensAndCase() {
		assertEquals("master_fisherman", TitleRegistry.normalizeTitleKey("  Master Fisherman  "));
		assertEquals("master_fisherman", TitleRegistry.normalizeTitleKey("Master-Fisherman"));
	}

	@Test
	void stripsInvalidCharactersAndCollapsesUnderscores() {
		assertEquals("a_bc", TitleRegistry.normalizeTitleKey("a__b!!c"));
		assertEquals("title1", TitleRegistry.normalizeTitleKey("___Title1___"));
	}

	@Test
	void emptyAfterCleanup() {
		assertEquals("", TitleRegistry.normalizeTitleKey("!!!"));
		assertEquals("", TitleRegistry.normalizeTitleKey("   "));
		assertEquals("", TitleRegistry.normalizeTitleKey(null));
	}

	@Test
	void permissionNodeUsesNormalizedKey() {
		assertEquals("sneakycharacters.title.master_fisherman",
				TitleRegistry.permissionNode("Master Fisherman"));
		assertTrue(TitleRegistry.permissionNode("x").startsWith(TitleRegistry.TITLE_PERMISSION_PREFIX));
	}
}
