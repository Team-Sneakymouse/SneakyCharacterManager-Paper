package net.sneakycharactermanager.paper.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.sneakycharactermanager.paper.SneakyCharacterManager;

/**
 * Legacy-mode request/reply for string lists. The proxy answers with one or more {@code listChunk} messages (see
 * {@code ProxyMessenger#sendChunked}) because a single plugin message is size-limited. Chunks are placed by offset, so
 * they may arrive in any order and over different player connections.
 */
public final class ProxyListReplies {
    static final long TIMEOUT_SECONDS = 5;
    private static final Map<String, Pending> pending = new ConcurrentHashMap<>();

    private ProxyListReplies() {}

    private static final class Pending {
        final CompletableFuture<List<String>> future = new CompletableFuture<>();
        String[] items;
        int received;
    }

    /**
     * Sends {@code subChannel} with {@code args} followed by a fresh request id and returns the reassembled reply.
     * Callable from any thread. Fails right away when no player is online to carry the message, and with a
     * {@link java.util.concurrent.TimeoutException} when no complete reply arrives in time.
     */
    public static CompletableFuture<List<String>> request(String subChannel, Object... args) {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<List<String>> reply = expect(requestId);
        Object[] payload = Arrays.copyOf(args, args.length + 1);
        payload[args.length] = requestId;
        Runnable send = () -> {
            Player carrier = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
            if (carrier == null) {
                fail(requestId, new IllegalStateException("No player online to carry the proxy request"));
                return;
            }
            ProxyMessagingUtil.sendByteArray(carrier, subChannel, payload);
        };
        SneakyCharacterManager plugin = SneakyCharacterManager.getInstance();
        if (plugin == null || !plugin.isEnabled()) fail(requestId, new IllegalStateException("SneakyCharacterManager is disabled"));
        else if (Bukkit.isPrimaryThread()) send.run();
        else Bukkit.getScheduler().runTask(plugin, send);
        return reply;
    }

    /** Registers a pending reply; the returned future is a copy, so callers cannot complete the shared state. */
    static CompletableFuture<List<String>> expect(String requestId) {
        Pending entry = new Pending();
        pending.put(requestId, entry);
        entry.future.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((items, error) -> pending.remove(requestId, entry));
        return entry.future.copy();
    }

    static void fail(String requestId, Throwable error) {
        Pending entry = pending.get(requestId);
        if (entry != null) entry.future.completeExceptionally(error);
    }

    /** One {@code listChunk}. Unknown or expired request ids are ignored. */
    public static void accept(String requestId, int total, int offset, List<String> chunk) {
        Pending entry = pending.get(requestId);
        if (entry == null) return;
        synchronized (entry) {
            if (entry.future.isDone()) return;
            if (entry.items == null) entry.items = new String[Math.max(total, 0)];
            if (total != entry.items.length || offset < 0 || offset + chunk.size() > total) {
                entry.future.completeExceptionally(new IllegalStateException(
                        "Inconsistent list chunk: total " + total + ", offset " + offset + ", size " + chunk.size()));
                return;
            }
            for (int i = 0; i < chunk.size(); i++) {
                if (entry.items[offset + i] == null) entry.received++;
                entry.items[offset + i] = chunk.get(i);
            }
            if (entry.received == entry.items.length) entry.future.complete(new ArrayList<>(Arrays.asList(entry.items)));
        }
    }
}
