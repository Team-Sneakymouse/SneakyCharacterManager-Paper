package net.sneakycharactermanager.paper.handlers.nametags;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import net.sneakycharactermanager.paper.api.nameplate.ReleaseReason;

class PlateAttachmentRegistryTest {

    private final PlateAttachmentRegistry registry = new PlateAttachmentRegistry();
    private final PlateAttachment.Operations operations = mock(PlateAttachment.Operations.class);
    private final Plugin racials = mock(Plugin.class);
    private final Plugin other = mock(Plugin.class);
    private final UUID owner = UUID.randomUUID();

    private static Entity entity() {
        Entity entity = mock(Entity.class);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        return entity;
    }

    @Test
    void oneAttachmentPerOwnerIndexedByAnchor() {
        Entity shell = entity();
        PlateAttachment attachment = registry.add(owner, racials, shell, operations);

        assertSame(attachment, registry.get(owner));
        assertTrue(registry.isCurrent(attachment));
        assertTrue(attachment.isActive());
        assertEquals(Optional.empty(), attachment.releaseReason());
        assertEquals(List.of(owner), registry.ownersOfAnchor(shell.getUniqueId()));
        assertThrows(IllegalStateException.class, () -> registry.add(owner, other, entity(), operations));
    }

    @ParameterizedTest
    @EnumSource(ReleaseReason.class)
    void everyReleaseIsTerminalAndRecordsItsReason(ReleaseReason reason) {
        Entity shell = entity();
        PlateAttachment attachment = registry.add(owner, racials, shell, operations);

        assertSame(attachment, registry.remove(owner, reason));
        assertFalse(attachment.isActive());
        assertEquals(Optional.of(reason), attachment.releaseReason());
        assertFalse(registry.isCurrent(attachment));
        assertNull(registry.get(owner));
        assertEquals(List.of(), registry.ownersOfAnchor(shell.getUniqueId()));
        assertNull(registry.remove(owner, ReleaseReason.CLOSED), "double release is a no-op");
        assertEquals(Optional.of(reason), attachment.releaseReason(), "first reason sticks");
    }

    @Test
    void staleHandleFromAnEarlierAttachmentIsNeverCurrent() {
        PlateAttachment first = registry.add(owner, racials, entity(), operations);
        registry.remove(owner, ReleaseReason.CLOSED);
        PlateAttachment second = registry.add(owner, racials, entity(), operations);

        assertNotSame(first, second);
        assertFalse(registry.isCurrent(first));
        assertTrue(registry.isCurrent(second));
    }

    @Test
    void movingAndParkingKeepTheAnchorIndexExact() {
        Entity shell = entity();
        Entity ogre = entity();
        PlateAttachment attachment = registry.add(owner, racials, shell, operations);

        registry.anchorTo(attachment, ogre);
        assertEquals(List.of(), registry.ownersOfAnchor(shell.getUniqueId()));
        assertEquals(List.of(owner), registry.ownersOfAnchor(ogre.getUniqueId()));

        registry.park(attachment);
        assertTrue(attachment.parked());
        assertNull(attachment.anchor());
        assertEquals(List.of(), registry.ownersOfAnchor(ogre.getUniqueId()));

        registry.anchorTo(attachment, shell);
        assertFalse(attachment.parked());
        assertEquals(List.of(owner), registry.ownersOfAnchor(shell.getUniqueId()));
    }

    @Test
    void sharedAnchorTracksEachOwner() {
        Entity ogre = entity();
        UUID second = UUID.randomUUID();
        registry.add(owner, racials, ogre, operations);
        registry.add(second, racials, ogre, operations);

        assertEquals(2, registry.ownersOfAnchor(ogre.getUniqueId()).size());
        registry.remove(owner, ReleaseReason.CLOSED);
        assertEquals(List.of(second), registry.ownersOfAnchor(ogre.getUniqueId()));
    }

    @Test
    void consumerLookupAndReleaseAll() {
        UUID second = UUID.randomUUID();
        PlateAttachment mine = registry.add(owner, racials, entity(), operations);
        PlateAttachment theirs = registry.add(second, other, entity(), operations);

        assertEquals(List.of(owner), registry.ownersAttachedBy(racials));
        assertEquals(List.of(second), registry.ownersAttachedBy(other));

        assertEquals(2, registry.removeAll(ReleaseReason.SCM_DISABLED).size());
        assertTrue(registry.isEmpty());
        assertEquals(Optional.of(ReleaseReason.SCM_DISABLED), mine.releaseReason());
        assertEquals(Optional.of(ReleaseReason.SCM_DISABLED), theirs.releaseReason());
    }

    @Test
    void handleForwardsToOperationsAndCopiesExclusions() {
        Entity ogre = entity();
        PlateAttachment attachment = registry.add(owner, racials, ogre, operations);
        attachment.moveTo(ogre);
        attachment.park();
        attachment.setBlank(true);
        attachment.setExcludedViewers(List.of(owner));
        attachment.close();
        verify(operations).moveTo(attachment, ogre);
        verify(operations).park(attachment);
        verify(operations).setBlank(attachment, true);
        verify(operations).setExcludedViewers(attachment, List.of(owner));
        verify(operations).close(attachment);

        java.util.HashSet<UUID> excluded = new java.util.HashSet<>(List.of(owner));
        attachment.excluded(excluded);
        excluded.clear();
        assertTrue(attachment.excludes(owner), "exclusions are copied");
    }

    @Test
    void visibilityRules() {
        Player viewer = mock(Player.class);
        Player plateOwner = mock(Player.class);
        UUID viewerId = UUID.randomUUID();
        when(viewer.getUniqueId()).thenReturn(viewerId);
        Entity shell = entity();

        // 4. plate on owner: follows the viewer's visibility of the owner
        when(viewer.canSee(plateOwner)).thenReturn(true);
        assertTrue(PlateVisibility.visibleTo(viewer, plateOwner, null));
        when(viewer.canSee(plateOwner)).thenReturn(false);
        assertFalse(PlateVisibility.visibleTo(viewer, plateOwner, null));

        // 3. plate on an anchor: follows the anchor, not the (hidden) owner
        PlateAttachment attachment = registry.add(owner, racials, shell, operations);
        when(viewer.canSee(shell)).thenReturn(true);
        assertTrue(PlateVisibility.visibleTo(viewer, plateOwner, attachment));
        when(viewer.canSee(shell)).thenReturn(false);
        assertFalse(PlateVisibility.visibleTo(viewer, plateOwner, attachment));

        // 1. exclusion beats a visible anchor
        when(viewer.canSee(shell)).thenReturn(true);
        attachment.excluded(java.util.Set.of(viewerId));
        assertFalse(PlateVisibility.visibleTo(viewer, plateOwner, attachment));

        // 2. parked hides even from viewers who can see the owner
        attachment.excluded(java.util.Set.of());
        registry.park(attachment);
        when(viewer.canSee(plateOwner)).thenReturn(true);
        assertFalse(PlateVisibility.visibleTo(viewer, plateOwner, attachment));
    }
}
