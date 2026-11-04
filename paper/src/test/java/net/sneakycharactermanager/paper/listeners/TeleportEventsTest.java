package net.sneakycharactermanager.paper.listeners;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.sneakycharactermanager.paper.api.nameplate.NameplateHandle;
import net.sneakycharactermanager.paper.api.nameplate.ReleaseReason;
import net.sneakycharactermanager.paper.handlers.nametags.NameplateFixture;
import net.sneakycharactermanager.paper.handlers.nametags.NameplateFixture.Plate;
import net.sneakycharactermanager.paper.listeners.TeleportEvents.Action;

class TeleportEventsTest {

	private NameplateFixture f;
	private final TeleportEvents listener = new TeleportEvents();
	private Player owner;

	@BeforeEach
	void setUp() {
		f = new NameplateFixture();
		owner = f.character("Owner");
		f.player("Viewer");
		f.nick(owner);
	}

	@AfterEach
	void tearDown() {
		f.close();
	}

	private void teleport(World to) {
		listener.onGamemodeChange(new PlayerTeleportEvent(owner, new Location(owner.getWorld(), 0, 64, 0), new Location(to, 5, 64, 5)));
	}

	private NameplateHandle attach(Entity anchor) {
		Plugin racials = mock(Plugin.class);
		when(racials.isEnabled()).thenReturn(true);
		return f.manager.attach(racials, owner, anchor).attachedHandle().orElseThrow();
	}

	@Test
	void decisionTable() {
		assertEquals(Action.NONE, TeleportEvents.decide(true, true, false, false), "ride seats keep their plate");
		assertEquals(Action.NONE, TeleportEvents.decide(true, false, true, true), "plate is on the anchor");
		assertEquals(Action.REBUILD, TeleportEvents.decide(true, false, false, false));
		assertEquals(Action.REBUILD, TeleportEvents.decide(true, false, true, false), "parked plate rides the player");
		assertEquals(Action.REBUILD, TeleportEvents.decide(false, false, false, false));
		assertEquals(Action.REBUILD, TeleportEvents.decide(false, true, false, false));
		assertEquals(Action.RELEASE_THEN_REBUILD, TeleportEvents.decide(false, false, true, true));
		assertEquals(Action.RELEASE_THEN_REBUILD, TeleportEvents.decide(false, false, true, false));
	}

	@Test
	void onlyNonCancelledTeleportsAtMonitor() throws NoSuchMethodException {
		EventHandler handler = TeleportEvents.class.getMethod("onGamemodeChange", PlayerTeleportEvent.class).getAnnotation(EventHandler.class);
		assertTrue(handler.ignoreCancelled(), "Bukkit skips the handler for cancelled teleports");
		assertEquals(EventPriority.MONITOR, handler.priority());
	}

	@Test
	void riderCarriedByAVehicleKeepsItsPlate() {
		Plate plate = f.plate(owner);
		when(owner.isInsideVehicle()).thenReturn(true);
		teleport(f.world);
		f.ticks(5);
		assertFalse(plate.removed);
		assertSame(plate, f.plate(owner));
	}

	@Test
	void plainTeleportRebuildsAfterTwoTicks() {
		Plate plate = f.plate(owner);
		teleport(f.world);
		assertTrue(plate.removed);
		f.tick();
		assertNull(f.manager.getNickname(owner));
		f.tick();
		assertNotSame(plate, f.plate(owner));
	}

	@Test
	void attachedSameWorldTeleportLeavesThePlateOnItsAnchor() {
		Entity shell = f.anchor("shell");
		NameplateHandle handle = attach(shell);
		Plate plate = f.plate(owner);

		teleport(f.world);
		f.ticks(5);
		assertFalse(plate.removed);
		assertSame(shell, plate.vehicle);
		assertTrue(handle.isActive());
	}

	@Test
	void crossWorldTeleportReleasesThenRebuildsOnTheOwner() {
		NameplateHandle handle = attach(f.anchor("shell"));
		Plate plate = f.plate(owner);

		teleport(f.otherWorld);
		assertEquals(Optional.of(ReleaseReason.OWNER_CHANGED_WORLD), handle.releaseReason());
		assertTrue(plate.removed);

		when(owner.getWorld()).thenReturn(f.otherWorld); // Paper moves the player after MONITOR
		f.ticks(2);
		assertSame(owner, f.plate(owner).vehicle);
		assertFalse(f.manager.isAttached(owner));
	}

	@Test
	void pendingRenickIsDroppedWhenSomethingElseRebuiltThePlate() {
		teleport(f.world);
		f.tick();
		f.nick(owner); // e.g. CharacterLoader finished meanwhile
		Plate loaded = f.plate(owner);
		f.ticks(5);
		assertSame(loaded, f.plate(owner));
		assertFalse(loaded.removed);
		assertEquals(2, f.plates.size());
		verify(loaded.entity, times(1)).updatePackets(any(), any()); // the stale renick never ran
	}

	@Test
	void pendingRenickIsDroppedAfterAnUnnick() {
		teleport(f.world);
		f.tick();
		when(owner.isDead()).thenReturn(true);
		f.manager.unnicknamePlayer(owner); // DeathListener
		when(owner.isDead()).thenReturn(false);
		f.ticks(5);
		assertNull(f.manager.getNickname(owner));
	}

	@Test
	void attachDuringAPendingRenickPlacesTheRebuiltPlateOnTheAnchor() {
		teleport(f.world);
		Entity shell = f.anchor("shell");
		NameplateHandle handle = attach(shell); // no plate exists right now
		f.ticks(2);
		assertSame(shell, f.plate(owner).vehicle);
		assertTrue(handle.isActive());
	}

	@Test
	void gamemodeRebuildReReadsTheCharacterAndSpectatorKeepsTheAttachment() {
		GamemodeEvents gamemodes = new GamemodeEvents();
		NameplateHandle handle = attach(f.anchor("shell"));
		Plate plate = f.plate(owner);

		gamemodes.onGamemodeChange(new PlayerGameModeChangeEvent(owner, GameMode.SPECTATOR));
		assertTrue(plate.removed);
		assertTrue(handle.isActive());

		gamemodes.onGamemodeChange(new PlayerGameModeChangeEvent(owner, GameMode.CREATIVE));
		when(f.characterOf(owner).getDisplayName()).thenReturn("Renamed");
		f.ticks(2);
		verify(f.plate(owner).entity).updatePackets("Renamed", null);
		assertTrue(handle.isActive());
	}
}
