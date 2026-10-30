package net.sneakycharactermanager.paper.storage;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class OwnerQueueTest {
    @Test void sameOwnerWaitsButDifferentOwnersProceed() {
        OwnerQueue queue = new OwnerQueue(4);
        CompletableFuture<String> first = new CompletableFuture<>();
        AtomicInteger calls = new AtomicInteger();
        queue.submit("a", () -> first);
        CompletableFuture<String> second = queue.submit("a", () -> {
            calls.incrementAndGet(); return CompletableFuture.completedFuture("second");
        });
        assertEquals(0, calls.get());
        assertEquals("other", queue.submit("b", () -> CompletableFuture.completedFuture("other")).join());
        first.complete("first");
        assertEquals("second", second.join());
    }
    @Test void uncertainFailureQuarantinesOwnerInsteadOfRetryingCreation() {
        OwnerQueue queue = new OwnerQueue(4);
        CompletableFuture<String> first = new CompletableFuture<>();
        queue.submit("a", () -> first);
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<String> second = queue.submit("a", () -> { calls.incrementAndGet(); return first; });
        first.completeExceptionally(new TimeoutException());
        assertThrows(CompletionException.class, second::join);
        assertEquals(0, calls.get());
        assertThrows(CompletionException.class, () -> queue.submit("a", () -> first).join());
    }
    @Test void closeAndCapacityRejectNewWork() {
        OwnerQueue queue = new OwnerQueue(1);
        queue.submit("a", CompletableFuture::new);
        assertThrows(CompletionException.class, () -> queue.submit("b", CompletableFuture::new).join());
        queue.close();
        assertThrows(CompletionException.class, () -> queue.submit("c", CompletableFuture::new).join());
    }
}
