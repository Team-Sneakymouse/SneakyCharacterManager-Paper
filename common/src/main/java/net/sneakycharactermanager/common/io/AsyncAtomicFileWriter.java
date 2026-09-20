package net.sneakycharactermanager.common.io;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Writes immutable file snapshots on one background thread.
 *
 * <p>The single worker preserves submission order, including writes to the same
 * target. Each individual replacement retains the guarantees provided by
 * {@link AtomicFiles}.</p>
 */
public final class AsyncAtomicFileWriter {

    private final ExecutorService executor;

    public AsyncAtomicFileWriter(String threadName) {
        Objects.requireNonNull(threadName, "threadName");
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task, threadName);
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newSingleThreadExecutor(threadFactory);
    }

    public CompletableFuture<Void> writeUtf8(Path target, String content) {
        Objects.requireNonNull(content, "content");
        return write(target, content.getBytes(StandardCharsets.UTF_8));
    }

    public CompletableFuture<Void> write(Path target, byte[] content) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(content, "content");

        Path snapshotTarget = target.toAbsolutePath();
        byte[] snapshotContent = Arrays.copyOf(content, content.length);
        return submit(() -> AtomicFiles.write(snapshotTarget, snapshotContent));
    }

    /**
     * Reads, changes, and replaces a UTF-8 file in the same queue as ordinary
     * writes. The change therefore sees every snapshot submitted before it.
     */
    public CompletableFuture<Void> updateUtf8(Path target, Utf8Update update) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(update, "update");

        Path snapshotTarget = target.toAbsolutePath();
        return submit(() -> {
            String current = Files.exists(snapshotTarget) ? Files.readString(snapshotTarget) : "";
            String updated = Objects.requireNonNull(update.apply(current), "updated content");
            AtomicFiles.writeUtf8(snapshotTarget, updated);
        });
    }

    public CompletableFuture<Void> flush() {
        return submit(() -> {
        });
    }

    /**
     * Stops accepting writes and waits for already submitted snapshots.
     *
     * @return true when every submitted write finished before the timeout
     */
    public boolean shutdown(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        executor.shutdown();
        try {
            return executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private CompletableFuture<Void> submit(ThrowingTask task) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    task.run();
                    completion.complete(null);
                } catch (Throwable failure) {
                    completion.completeExceptionally(failure);
                }
            });
        } catch (RuntimeException failure) {
            completion.completeExceptionally(failure);
        }
        return completion;
    }

    @FunctionalInterface
    private interface ThrowingTask {
        void run() throws Exception;
    }

    @FunctionalInterface
    public interface Utf8Update {
        String apply(String current) throws Exception;
    }
}
