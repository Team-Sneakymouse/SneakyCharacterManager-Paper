package net.sneakycharactermanager.common.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Replaces files without exposing partially written content.
 *
 * <p>This class does not order concurrent writes to the same target. Callers that
 * can write the same file concurrently must serialize both snapshot creation and
 * the call to this class. Otherwise, an older snapshot can atomically replace a
 * newer one.</p>
 *
 * <p>Atomic replacement is required. The write fails and leaves the old target in
 * place when the filesystem does not support an atomic move.</p>
 */
public final class AtomicFiles {

    interface CommitOperations {
        void move(Path source, Path target) throws IOException;
        void deleteIfExists(Path path) throws IOException;
    }

    private static final CommitOperations NIO_COMMIT_OPERATIONS = new CommitOperations() {
        @Override
        public void move(Path source, Path target) throws IOException {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }

        @Override
        public void deleteIfExists(Path path) throws IOException {
            Files.deleteIfExists(path);
        }
    };

    private AtomicFiles() {
    }

    public static void writeUtf8(Path target, String content) throws IOException {
        write(target, content.getBytes(StandardCharsets.UTF_8));
    }

    public static void write(Path target, byte[] content) throws IOException {
        write(target, content, NIO_COMMIT_OPERATIONS);
    }

    static void write(Path target, byte[] content, CommitOperations commitOperations) throws IOException {
        Path absoluteTarget = target.toAbsolutePath();
        Path parent = absoluteTarget.getParent();
        if (parent == null) {
            throw new IOException("Cannot determine parent directory for " + target);
        }

        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + absoluteTarget.getFileName() + ".", ".tmp");
        boolean committed = false;
        Throwable failure = null;

        try {
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }

            try {
                commitOperations.move(temporary, absoluteTarget);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("Filesystem does not support atomic replacement of " + absoluteTarget, e);
            }

            committed = true;
        } catch (IOException | RuntimeException | Error e) {
            failure = e;
            throw e;
        } finally {
            if (!committed) {
                try {
                    commitOperations.deleteIfExists(temporary);
                } catch (IOException | RuntimeException | Error cleanupFailure) {
                    if (failure != null) {
                        failure.addSuppressed(cleanupFailure);
                    } else {
                        throw cleanupFailure;
                    }
                }
            }
        }
    }
}
