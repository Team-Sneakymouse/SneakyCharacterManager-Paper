package net.sneakycharactermanager.paper.listeners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TeleportEventsTest {

	@Test
	void ridersCarriedByAVehicleKeepTheirNametag() {
		assertFalse(TeleportEvents.rebuildsNametag(true, true, false));
	}

	@Test
	void aPlateAnotherPluginKeepsIsLeftAlone() {
		assertFalse(TeleportEvents.rebuildsNametag(true, false, true));
		assertFalse(TeleportEvents.rebuildsNametag(false, false, true));
	}

	@Test
	void otherTeleportsRebuildIt() {
		assertTrue(TeleportEvents.rebuildsNametag(true, false, false));
		assertTrue(TeleportEvents.rebuildsNametag(false, false, false));
		assertTrue(TeleportEvents.rebuildsNametag(false, true, false));
	}

}
