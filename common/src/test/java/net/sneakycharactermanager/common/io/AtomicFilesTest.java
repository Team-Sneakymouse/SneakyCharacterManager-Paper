package net.sneakycharactermanager.common.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicFilesTest {

    @TempDir
    Path directory;

    @Test
    void writeUtf8ReplacesExistingContentWithoutLosingUnicode() throws Exception {
        Path target = directory.resolve("character.yml");
        Files.writeString(target, "old content");

        AtomicFiles.writeUtf8(target, "name: Grunhild\nlocation: Österreich\n");

        assertEquals("name: Grunhild\nlocation: Österreich\n", Files.readString(target));
    }

    @Test
    void failedReplacementLeavesTheExistingTargetUntouchedAndRemovesTheTemporaryFile() throws Exception {
        Path target = Files.createDirectory(directory.resolve("character.yml"));
        Path marker = target.resolve("old-content");
        Files.writeString(marker, "preserve me");

        assertThrows(IOException.class, () -> AtomicFiles.writeUtf8(target, "new content"));

        assertEquals("preserve me", Files.readString(marker));
        try (var files = Files.list(directory)) {
            assertEquals(1L, files.count());
        }
    }

    @Test
    void writePreservesBinaryContent() throws Exception {
        Path target = directory.resolve("keys.ser");
        byte[] serializedKeys = new byte[]{0, 1, 2, 127, (byte) 128, (byte) 255};

        AtomicFiles.write(target, serializedKeys);

        assertArrayEquals(serializedKeys, Files.readAllBytes(target));
    }

    @Test
    void concurrentReadersSeeOnlyCompleteVersions() throws Exception {
        Path target = directory.resolve("character.yml");
        byte[] firstVersion = new byte[64 * 1024];
        byte[] secondVersion = new byte[64 * 1024];
        Arrays.fill(firstVersion, (byte) 'A');
        Arrays.fill(secondVersion, (byte) 'B');
        AtomicFiles.write(target, firstVersion);

        CyclicBarrier iteration = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var writer = executor.submit(() -> {
                for (int i = 0; i < 100; i++) {
                    iteration.await();
                    AtomicFiles.write(target, i % 2 == 0 ? secondVersion : firstVersion);
                    iteration.await();
                }
                return null;
            });
            var reader = executor.submit(() -> {
                for (int i = 0; i < 100; i++) {
                    iteration.await();
                    byte[] observed = Files.readAllBytes(target);
                    assertTrue(
                            Arrays.equals(firstVersion, observed) || Arrays.equals(secondVersion, observed),
                            "reader observed a partial file"
                    );
                    iteration.await();
                }
                return null;
            });

            writer.get(15, TimeUnit.SECONDS);
            reader.get(15, TimeUnit.SECONDS);
        }
    }

    @Test
    void unsupportedAtomicMovePreservesTheExistingFileAndCleansUp() throws Exception {
        Path target = directory.resolve("character.yml");
        Files.writeString(target, "old content");
        AtomicFiles.CommitOperations unsupportedMove = new AtomicFiles.CommitOperations() {
            @Override
            public void move(Path source, Path destination) throws IOException {
                throw new AtomicMoveNotSupportedException(source.toString(), destination.toString(), "not supported");
            }

            @Override
            public void deleteIfExists(Path path) throws IOException {
                Files.deleteIfExists(path);
            }
        };

        IOException failure = assertThrows(
                IOException.class,
                () -> AtomicFiles.write(target, "new content".getBytes(StandardCharsets.UTF_8), unsupportedMove)
        );

        assertInstanceOf(AtomicMoveNotSupportedException.class, failure.getCause());
        assertEquals("old content", Files.readString(target));
        try (var files = Files.list(directory)) {
            assertEquals(1L, files.count());
        }
    }

    @Test
    void cleanupFailureIsSuppressedWithoutReplacingTheCommitFailure() throws Exception {
        Path target = directory.resolve("character.yml");
        Files.writeString(target, "old content");
        IOException commitFailure = new IOException("commit failed");
        IOException cleanupFailure = new IOException("cleanup failed");
        AtomicFiles.CommitOperations failingOperations = new AtomicFiles.CommitOperations() {
            @Override
            public void move(Path source, Path destination) throws IOException {
                throw commitFailure;
            }

            @Override
            public void deleteIfExists(Path path) throws IOException {
                throw cleanupFailure;
            }
        };

        IOException observed = assertThrows(
                IOException.class,
                () -> AtomicFiles.write(target, new byte[]{1, 2, 3}, failingOperations)
        );

        assertSame(commitFailure, observed);
        assertArrayEquals(new Throwable[]{cleanupFailure}, observed.getSuppressed());
        assertEquals("old content", Files.readString(target));
    }
}
