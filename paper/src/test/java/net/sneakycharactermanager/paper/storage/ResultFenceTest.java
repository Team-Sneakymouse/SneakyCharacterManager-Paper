package net.sneakycharactermanager.paper.storage;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ResultFenceTest {
    @Test void newerSelectionRejectsOlderAndQuitRejectsPreviousLogin() {
        ResultFence fence = new ResultFence();
        Object session = new Object();
        var first = fence.capture("player", "selection", session);
        var second = fence.capture("player", "selection", session);
        assertFalse(fence.current(first, session));
        assertTrue(fence.current(second, session));
        assertFalse(fence.current(second, new Object()));
        fence.forget("player");
        assertFalse(fence.current(second, session));
    }
    @Test void disableInvalidatesEveryCompletionAndListsDoNotCancelSelection() {
        ResultFence fence = new ResultFence(); Object session = new Object();
        var load = fence.capture("p", "selection", session);
        fence.capture("p", "list", session);
        assertTrue(fence.current(load, session));
        fence.close(); assertFalse(fence.current(load, session));
    }
}
