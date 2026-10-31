package net.sneakycharactermanager.paper.storage;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Serializes local operations per owner without holding a thread or a JDBC connection.
 * Any failed/uncertain operation fences that owner until restart; never retry an unknown write.
 */
public final class OwnerQueue implements AutoCloseable {
    private final int capacity;
    private final Map<String, CompletableFuture<?>> tails = new HashMap<>();
    private final Set<String> quarantined = new HashSet<>();
    private int pending;
    private boolean closed;

    public OwnerQueue(int capacity) { this.capacity = capacity; }

    public synchronized <T> CompletableFuture<T> submit(String owner, Supplier<CompletableFuture<T>> work) {
        if (closed || pending >= capacity || quarantined.contains(owner))
            return CompletableFuture.failedFuture(new IllegalStateException("SCM storage unavailable; restart required after uncertain operation"));
        CompletableFuture<?> previous = tails.getOrDefault(owner, CompletableFuture.completedFuture(null));
        CompletableFuture<T> result = new CompletableFuture<>();
        tails.put(owner, result);
        pending++;
        previous.whenComplete((unused, priorFailure) -> {
            synchronized (this) {
                if (closed || quarantined.contains(owner) || priorFailure != null) {
                    finish(owner, result, null, new IllegalStateException("SCM owner fenced"));
                    return;
                }
            }
            try {
                work.get().whenComplete((value, error) -> finish(owner, result, value, error));
            } catch (Throwable error) { finish(owner, result, null, error); }
        });
        return result;
    }

    private synchronized <T> void finish(String owner, CompletableFuture<T> result, T value, Throwable error) {
        pending--;
        if (error != null) {
            quarantined.add(owner);
            if (quarantined.size() >= capacity) closed = true; // bound retained failure state
        }
        tails.remove(owner, result);
        if (error == null) result.complete(value); else result.completeExceptionally(error);
    }

    @Override public synchronized void close() { closed = true; }
}
