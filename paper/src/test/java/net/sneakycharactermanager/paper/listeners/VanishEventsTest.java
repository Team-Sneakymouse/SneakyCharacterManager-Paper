package net.sneakycharactermanager.paper.listeners;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerHideEntityEvent;
import org.bukkit.event.player.PlayerShowEntityEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import net.sneakycharactermanager.paper.api.nameplate.NameplateHandle;
import net.sneakycharactermanager.paper.handlers.nametags.NameplateFixture;
import net.sneakycharactermanager.paper.handlers.nametags.NameplateFixture.Plate;
import net.sneakycharactermanager.paper.handlers.nametags.NametagManager;

/** VanishEvents with the real manager: Paper has already updated canSee when these events fire. */
class VanishEventsTest {

    private NameplateFixture f;
    private final VanishEvents listener = new VanishEvents();
    private Player owner;
    private Player a;
    private Player b;

    @BeforeEach
    void setUp() {
        f = new NameplateFixture();
        owner = f.character("Owner");
        a = f.player("A");
        b = f.player("B");
        f.nick(owner);
    }

    @AfterEach
    void tearDown() {
        f.close();
    }

    private void hide(Player viewer, Entity target) {
        f.hide(viewer, target);
        listener.onVanish(new PlayerHideEntityEvent(viewer, target));
    }

    private void show(Player viewer, Entity target) {
        f.show(viewer, target);
        listener.onVanish(new PlayerShowEntityEvent(viewer, target));
    }

    private NameplateHandle attach(Entity anchor) {
        Plugin racials = mock(Plugin.class);
        when(racials.isEnabled()).thenReturn(true);
        return f.manager.attach(racials, owner, anchor).attachedHandle().orElseThrow();
    }

    @Test
    void hidingAnOwnerFromOneViewerHidesThePlateOnlyFromThatViewer() {
        Plate plate = f.plate(owner);
        hide(a, owner);

        verify(a).hideEntity(f.plugin, plate.display);
        verify(b, never()).hideEntity(any(Plugin.class), any(Entity.class));
        assertFalse(plate.removed, "the plate is not destroyed for everyone");
        assertSame(plate, f.plate(owner));
    }

    @Test
    void showingItAgainShowsThePlateAndSendsTextOnlyToThatViewer() {
        Plate plate = f.plate(owner);
        hide(a, owner);
        show(a, owner);

        verify(a).showEntity(f.plugin, plate.display);
        verify(plate.entity).sendCharacter(a);
        verify(plate.entity, never()).sendCharacter(b);
        verify(b, never()).showEntity(any(Plugin.class), any(Entity.class));
        assertEquals(1, f.pendingTasks(), "only the nick's own text refresh; no rebuild is scheduled");
    }

    @Test
    void showThenRehideInTheSameTickLeavesThePlateHidden() {
        Plate plate = f.plate(owner);
        hide(a, owner);
        show(a, owner);
        hide(a, owner);

        InOrder order = inOrder(a);
        order.verify(a).hideEntity(f.plugin, plate.display);
        order.verify(a).showEntity(f.plugin, plate.display);
        order.verify(a).hideEntity(f.plugin, plate.display);
        assertTrue(f.hiddenFrom(owner).contains(a.getUniqueId()));
        f.ticks(5);
        assertSame(plate, f.plate(owner));
    }

    @Test
    void anAttachedPlateFollowsTheAnchorNotTheHiddenOwner() {
        Entity shell = f.anchor("shell");
        attach(shell);
        Plate plate = f.plate(owner);

        hide(a, owner);
        verify(a, never()).hideEntity(f.plugin, plate.display);

        hide(a, shell);
        verify(a).hideEntity(f.plugin, plate.display);
        show(a, shell);
        verify(a).showEntity(f.plugin, plate.display);
    }

    @Test
    void genuineVanishRemovesThePlateForEveryoneAndUnvanishRebuildsIt() {
        Entity shell = f.anchor("shell");
        NameplateHandle handle = attach(shell);
        Plate plate = f.plate(owner);

        f.vanish(owner, "\u2726");
        hide(a, owner);
        hide(b, owner);
        assertTrue(plate.removed);
        assertNull(f.manager.getNickname(owner));
        assertTrue(handle.isActive());

        f.vanish(owner, "");
        show(a, owner);
        show(b, owner);
        f.ticks(2); // NametagManager.RENICK_DELAY_TICKS
        assertEquals(2, f.plates.size(), "many show events build one plate");
        assertSame(shell, f.plate(owner).vehicle);
    }

    @Test
    void unresolvedVanishPlaceholderOnlyHidesPerViewer() {
        Plate plate = f.plate(owner);
        f.vanish(owner, "%cmi_user_vanished_symbol%"); // PlaceholderAPI without the CMI expansion
        hide(a, owner);
        assertFalse(plate.removed);
        verify(a).hideEntity(f.plugin, plate.display);
    }

    @Test
    void scmsOwnPlateHideEventsAreIgnored() {
        Plate plate = f.plate(owner);
        hide(a, owner); // SCM hides the display -> Paper fires a hide event for the display itself
        listener.onVanish(new PlayerHideEntityEvent(a, plate.display));
        verify(a, times(1)).hideEntity(f.plugin, plate.display);
        assertFalse(plate.removed);
    }

    @Test
    void handlersMonitorOnly() throws NoSuchMethodException {
        for (Class<?> event : new Class<?>[] {PlayerHideEntityEvent.class, PlayerShowEntityEvent.class}) {
            Method method = VanishEvents.class.getMethod("onVanish", event);
            assertEquals(org.bukkit.event.EventPriority.MONITOR, method.getAnnotation(EventHandler.class).priority());
        }
    }
}
