package net.sneakycharactermanager.paper.handlers.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CharacterSlotsTest {

	@Test
	void parsesNumericSlotSuffix() {
		assertEquals(3, CharacterSlots.parseSlotValue("3"));
		assertEquals(0, CharacterSlots.parseSlotValue("0"));
	}

	@Test
	void ignoresWildcardsAndJunk() {
		assertNull(CharacterSlots.parseSlotValue("*"));
		assertNull(CharacterSlots.parseSlotValue(".*"));
		assertNull(CharacterSlots.parseSlotValue("-1"));
		assertNull(CharacterSlots.parseSlotValue("abc"));
	}
}
