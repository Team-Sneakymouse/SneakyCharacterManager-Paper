package net.sneakycharactermanager.paper.handlers.skins;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SkinQueueCooldownTest {
    @Test
    void schedulerSkipsCoolingEntryAndRetainsDeadlineAcrossDispatches() {
        SkinQueue.Cooldowns<String> state = new SkinQueue.Cooldowns<>();
        state.add("blocked");
        state.add("ready");
        assertTrue(state.claim("blocked", 1000));
        assertFalse(state.claim("blocked", 1000)); // no overlapping HTTP work
        state.rateLimited("blocked", "30", 0, 1000);
        state.release("blocked");
        assertEquals("ready", RateLimitBackoff.firstReady(List.of("blocked", "ready"), e -> state.ready(e, 2000)));
        assertFalse(state.claim("blocked", 30999));
        assertTrue(state.claim("blocked", 31000));
        state.rateLimited("blocked", null, 0, 31000);
        state.release("blocked");
        assertFalse(state.ready("blocked", 40999)); // second hit: 10 seconds
        assertTrue(state.ready("blocked", 41000));
        state.succeeded("blocked");
        state.rateLimited("blocked", null, 0, 41000);
        assertTrue(state.ready("blocked", 46000)); // success reset the hit count
    }

    @Test
    void concurrentWorkersCannotClaimTheSameEntry() throws Exception {
        SkinQueue.Cooldowns<String> state = new SkinQueue.Cooldowns<>();
        state.add("entry");
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Boolean> claim = () -> {
                start.await();
                return state.claim("entry", 1000);
            };
            var first = workers.submit(claim);
            var second = workers.submit(claim);
            start.countDown();
            assertNotEquals(first.get(5, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(5, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    @Test
    void flushClearsMembershipAndMakesLateWorkerReportsInert() {
        SkinQueue.Cooldowns<String> state = new SkinQueue.Cooldowns<>();
        var queue = java.util.Map.of(0, new java.util.ArrayList<>(List.of("waiting")),
                4, new java.util.ArrayList<>(List.of("inFlight")));
        state.add("waiting");
        state.add("inFlight");
        state.claim("inFlight", 1000);
        assertEquals(2, state.flush(queue));
        assertTrue(queue.values().stream().allMatch(List::isEmpty));
        state.rateLimited("inFlight", "86400", 0, 1000);
        state.succeeded("waiting");
        state.release("inFlight");
        assertFalse(state.ready("waiting", Long.MAX_VALUE));
        assertFalse(state.claim("inFlight", Long.MAX_VALUE));
        state.add("inFlight");
        assertTrue(state.claim("inFlight", 1000));
        assertEquals(0, state.flush(queue));
        assertFalse(state.ready("inFlight", Long.MAX_VALUE));
    }

    @Test
    void removalMakesLateWorkerReportsInert() {
        SkinQueue.Cooldowns<String> state = new SkinQueue.Cooldowns<>();
        state.add("entry");
        state.claim("entry", 1000);
        state.remove("entry");
        state.rateLimited("entry", "86400", 0, 1000);
        state.release("entry");
        assertFalse(state.ready("entry", Long.MAX_VALUE));
        state.add("entry");
        assertTrue(state.ready("entry", 1000));
    }
}
