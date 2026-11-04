package net.sneakycharactermanager.paper.handlers.nametags;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

/** A plate whose construction fails part-way must not leave a display entity in the world. */
class NicknameEntityConstructionTest {

    private final Player owner = mock(Player.class);
    private final World world = mock(World.class);
    private final TextDisplay display = mock(TextDisplay.class);

    @SuppressWarnings("unchecked")
    private void spawnRunsConfigureFirst() {
        when(owner.getWorld()).thenReturn(world);
        when(owner.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(world.spawn(any(Location.class), eq(TextDisplay.class), any(Consumer.class))).thenAnswer(invocation -> {
            // Paper runs the consumer before the entity is added to the world.
            ((Consumer<TextDisplay>) invocation.getArgument(2)).accept(display);
            return display;
        });
    }

    @Test
    void failureAfterSpawnRemovesTheDisplay() {
        spawnRunsConfigureFirst();
        // The first packet build casts the display to CraftEntity; a mock is not one, so construction fails there.
        assertThrows(ClassCastException.class, () -> new NicknameEntity(owner));

        InOrder order = inOrder(display, owner);
        order.verify(display).setPersistent(false);
        order.verify(display).addScoreboardTag(NicknameEntity.SCOREBOARD_TAG);
        order.verify(owner).addPassenger(display);
        order.verify(display).remove();
    }

    @Test
    void mountFailureRemovesTheDisplayAndRethrows() {
        spawnRunsConfigureFirst();
        IllegalStateException mountFailure = new IllegalStateException("mount refused");
        when(owner.addPassenger(display)).thenThrow(mountFailure);

        assertSame(mountFailure, assertThrows(IllegalStateException.class, () -> new NicknameEntity(owner)));
        verify(display).remove();
    }

    @Test
    void cleanupFailureIsAttachedNotSwallowed() {
        spawnRunsConfigureFirst();
        IllegalStateException mountFailure = new IllegalStateException("mount refused");
        IllegalStateException removeFailure = new IllegalStateException("remove failed");
        when(owner.addPassenger(display)).thenThrow(mountFailure);
        doThrow(removeFailure).when(display).remove();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> new NicknameEntity(owner));
        assertSame(mountFailure, thrown);
        assertArrayEquals(new Throwable[] {removeFailure}, thrown.getSuppressed());
    }

    @Test
    void configureFailureLeavesNothingToRemove() {
        spawnRunsConfigureFirst();
        doThrow(new IllegalArgumentException("setter")).when(display).setLineWidth(anyInt());

        assertThrows(IllegalArgumentException.class, () -> new NicknameEntity(owner));
        verify(owner, never()).addPassenger(any());
        verify(display, never()).remove();
    }

    @Test
    void nicknameDestroysItsEntityWhenTheFirstTextBuildFails() {
        UUID id = UUID.randomUUID();
        when(owner.getUniqueId()).thenReturn(id);
        NicknameEntity entity = mock(NicknameEntity.class);
        IllegalStateException packetFailure = new IllegalStateException("packet");
        doThrow(packetFailure).when(entity).updatePackets("Name", null);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(owner);
            assertSame(packetFailure, assertThrows(IllegalStateException.class,
                    () -> new Nickname(owner, "Name", null, player -> entity)));
        }
        verify(entity).destroy();
    }
}
