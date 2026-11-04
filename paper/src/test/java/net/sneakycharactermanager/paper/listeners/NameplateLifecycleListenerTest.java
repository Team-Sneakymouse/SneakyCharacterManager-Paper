package net.sneakycharactermanager.paper.listeners;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;

import net.sneakycharactermanager.paper.api.nameplate.NameplateHandle;
import net.sneakycharactermanager.paper.api.nameplate.ReleaseReason;
import net.sneakycharactermanager.paper.handlers.nametags.NameplateFixture;
import net.sneakycharactermanager.paper.handlers.nametags.NameplateFixture.Plate;
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;

/** Terminal releases through the real listeners: each returns the plate and leaves the handle inert. */
class NameplateLifecycleListenerTest {

    private NameplateFixture f;
    private NameplateLifecycleListener listener;
    private Player owner;
    private Player viewer;
    private Plugin racials;

    @BeforeEach
    void setUp() {
        f = new NameplateFixture();
        listener = new NameplateLifecycleListener(f.plugin);
        owner = f.character("Owner");
        viewer = f.player("Viewer");
        racials = plugin("SneakyRacials");
        f.nick(owner);
    }

    @AfterEach
    void tearDown() {
        f.close();
    }

    private static Plugin plugin(String name) {
        Plugin plugin = mock(Plugin.class, name);
        when(plugin.isEnabled()).thenReturn(true);
        return plugin;
    }

    private NameplateHandle attach(Plugin consumer, Player player, Entity anchor) {
        return f.manager.attach(consumer, player, anchor).attachedHandle().orElseThrow();
    }

    @Test
    void disablingAConsumerReleasesOnlyItsAttachments() {
        Player second = f.character("Second");
        f.nick(second);
        Plugin other = plugin("Other");
        Entity shell = f.anchor("shell");
        Entity ogre = f.anchor("ogre");
        NameplateHandle mine = attach(racials, owner, shell);
        mine.setBlank(true);
        NameplateHandle theirs = attach(other, second, ogre);
        f.plate(owner).trackers.add(viewer);

        listener.onPluginDisable(new PluginDisableEvent(racials));
        assertEquals(Optional.of(ReleaseReason.CONSUMER_DISABLED), mine.releaseReason());
        assertSame(owner, f.plate(owner).vehicle);
        verify(f.plate(owner).entity).sendCharacter(viewer);
        assertTrue(theirs.isActive());
        assertSame(ogre, f.plate(second).vehicle);

        listener.onPluginDisable(new PluginDisableEvent(f.plugin)); // SCM's own disable is handled elsewhere
        assertTrue(theirs.isActive());
    }

    @Test
    void removedAnchorEndsTheAttachmentNowAndReturnsThePlateNextTick() {
        Entity shell = f.anchor("shell");
        NameplateHandle handle = attach(racials, owner, shell);
        Plate plate = f.plate(owner);

        listener.onEntityRemove(new EntityRemoveFromWorldEvent(f.anchor("unrelated"), f.world));
        assertTrue(handle.isActive());

        listener.onEntityRemove(new EntityRemoveFromWorldEvent(shell, f.world));
        assertEquals(Optional.of(ReleaseReason.ANCHOR_INVALID), handle.releaseReason());
        assertSame(shell, plate.vehicle, "no remount inside the removal callback");

        f.tick();
        assertSame(owner, plate.vehicle);
    }

    @Test
    void worldChangeBackstopReleasesAndRebuildsInTheNewWorld() {
        NameplateHandle handle = attach(racials, owner, f.anchor("shell"));
        Plate plate = f.plate(owner);

        when(owner.getWorld()).thenReturn(f.otherWorld);
        listener.onWorldChange(new PlayerChangedWorldEvent(owner, f.world));
        assertEquals(Optional.of(ReleaseReason.OWNER_CHANGED_WORLD), handle.releaseReason());
        assertTrue(plate.removed, "a plate left in the old world is rebuilt, not dragged across worlds");

        f.ticks(2);
        assertSame(owner, f.plate(owner).vehicle);
        assertNotSame(plate, f.plate(owner));
    }

    @Test
    void quitReleasesTheOwnerAndForgetsThemAsAViewer() {
        Player second = f.character("Second");
        f.nick(second);
        f.hide(owner, second);
        f.manager.reconcile(second);
        assertTrue(f.hiddenFrom(second).contains(owner.getUniqueId()));

        NameplateHandle handle = attach(racials, owner, f.anchor("shell"));
        Plate plate = f.plate(owner);
        f.plugin.skinQueue = mock(SkinQueue.class);
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(owner);

        new ConnectionEventListeners().onPlayerLeave(quit);
        f.quit(owner);
        assertEquals(Optional.of(ReleaseReason.OWNER_QUIT), handle.releaseReason());
        assertTrue(plate.removed);
        assertFalse(f.hiddenFrom(second).contains(owner.getUniqueId()),
                "a rejoining viewer starts visible in Paper, so SCM must forget it hid the plate");
        f.ticks(10);
        assertNull(f.manager.getNickname(owner));
    }
}
