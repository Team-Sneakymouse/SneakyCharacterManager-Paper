package net.sneakycharactermanager.paper.storage;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RevisionFenceTest {
    @Test void reorderedMainThreadDeliveryCannotOverwriteNewerOwnerSnapshot() {
        RevisionFence fence = new RevisionFence();
        fence.begin("owner"); fence.begin("owner");
        assertTrue(fence.accept("owner", 8)); fence.finish("owner");
        assertFalse(fence.accept("owner", 7)); fence.finish("owner");
        // Retain no per-owner state after every scheduled completion has been drained.
        assertEquals(0, fence.size());
    }
    @Test void equalRevisionCanPopulateTwoDifferentMenus() {
        RevisionFence fence = new RevisionFence(); fence.begin("owner"); fence.begin("owner");
        assertTrue(fence.accept("owner", 8)); assertTrue(fence.accept("owner", 8));
    }
}
