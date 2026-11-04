package net.sneakycharactermanager.paper.handlers.nametags;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.sneakycharactermanager.paper.api.nameplate.NameplateHandle;
import net.sneakycharactermanager.paper.api.nameplate.ReleaseReason;
import net.sneakycharactermanager.paper.handlers.nametags.NameplateFixture.Plate;

/** The real refresher run against the real manager. */
class NameTagRefresherTest {

    private NameplateFixture f;
    private NameTagRefresher refresher;
    private Player owner;
    private Player a;
    private Plugin racials;

    @BeforeEach
    void setUp() {
        f = new NameplateFixture();
        refresher = new NameTagRefresher(f.manager);
        owner = f.character("Owner");
        a = f.player("A");
        racials = mock(Plugin.class);
        when(racials.isEnabled()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        f.close();
    }

    private NameplateHandle attach(Entity anchor) {
        return f.manager.attach(racials, owner, anchor).attachedHandle().orElseThrow();
    }

    @Test
    void sendsTextOncePerDisplayAndAgainForAReplacementDisplay() {
        f.nick(owner);
        Plate first = f.plate(owner);
        first.trackers.add(a);

        refresher.run();
        refresher.run();
        verify(first.entity, times(1)).sendCharacter(a);

        f.manager.unnicknamePlayer(owner);
        f.nick(owner);
        Plate second = f.plate(owner);
        second.trackers.add(a);
        refresher.run();
        verify(second.entity, times(1)).sendCharacter(a);
        assertEquals(second.displayId, refresher.seen(owner.getUniqueId()).displayId());
    }

    @Test
    void forgetsOwnersWhoLeft() {
        f.nick(owner);
        refresher.run();
        assertEquals(1, refresher.seenSize());

        f.quit(owner);
        refresher.run();
        assertEquals(0, refresher.seenSize());
    }

    @Test
    void vanishRemovesAnAttachedPlateAndUnvanishRebuildsItOnTheAnchor() {
        f.nick(owner);
        Entity shell = f.anchor("shell");
        NameplateHandle handle = attach(shell);
        Plate plate = f.plate(owner);

        f.vanish(owner, "\u2726");
        refresher.run();
        assertTrue(plate.removed, "vanish removes the plate for everyone");
        assertNull(f.manager.getNickname(owner));
        assertTrue(handle.isActive(), "vanish is not a release");
        assertTrue(f.manager.isAwaitingRestore(owner.getUniqueId()));

        refresher.run();
        assertNull(f.manager.getNickname(owner), "still vanished");

        f.vanish(owner, "");
        refresher.run();
        assertSame(shell, f.plate(owner).vehicle);
        assertFalse(f.manager.isAwaitingRestore(owner.getUniqueId()));
    }

    @Test
    void unresolvedVanishPlaceholderIsNotAVanish() {
        f.nick(owner);
        Plate plate = f.plate(owner);
        f.vanish(owner, NametagManager.VANISH_PLACEHOLDER);
        refresher.run();
        assertFalse(plate.removed);
        assertFalse(f.manager.isSuppressed(owner));
    }

    @Test
    void suppressionEndingWithoutACharacterDoesNotRebuild() {
        f.nick(owner);
        f.vanish(owner, "x");
        refresher.run();
        f.removeCharacter(owner);
        f.vanish(owner, "");
        refresher.run();
        assertNull(f.manager.getNickname(owner));
        assertFalse(f.manager.isAwaitingRestore(owner.getUniqueId()));
    }

    @Test
    void ownersWithoutASuppressedPlateAreNotNicked() {
        // e.g. no character loaded yet, or removed by /chardisable: the refresher never builds those
        f.removeCharacter(owner);
        refresher.run();
        assertNull(f.manager.getNickname(owner));
        assertTrue(f.plates.isEmpty());
    }

    @Test
    void invalidAnchorEndsTheAttachmentAndKnockedOffPlatesAreRemounted() {
        f.nick(owner);
        Entity shell = f.anchor("shell");
        NameplateHandle handle = attach(shell);
        Plate plate = f.plate(owner);
        f.ticks(5); // drain the nick/attach text refreshes before anyone tracks the plate
        plate.trackers.add(a);

        plate.vehicle = null; // ejected by something
        refresher.run();
        assertSame(shell, plate.vehicle);
        verify(plate.entity, times(1)).sendCharacter(a);

        handle.setBlank(true);
        verify(plate.entity).sendOff(a);
        when(shell.isValid()).thenReturn(false);
        refresher.run(); // a is already in the refresher's seen set, so only the release can resend
        assertEquals(Optional.of(ReleaseReason.ANCHOR_INVALID), handle.releaseReason());
        assertSame(owner, plate.vehicle);
        f.tick();
        verify(plate.entity, times(2)).sendCharacter(a); // the attachment's blank text is replaced
    }

    @Test
    void removedDisplayIsRebuilt() {
        f.nick(owner);
        Plate plate = f.plate(owner);
        plate.removed = true; // e.g. /kill @e[type=text_display]

        refresher.run();
        assertNull(f.manager.getNickname(owner));
        f.ticks((int) NametagManager.RENICK_DELAY_TICKS);
        assertNotSame(plate, f.plate(owner));
        assertFalse(f.plate(owner).removed);
    }
}
