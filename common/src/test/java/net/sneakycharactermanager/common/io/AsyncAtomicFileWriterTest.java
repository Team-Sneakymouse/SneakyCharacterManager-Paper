package net.sneakycharactermanager.common.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncAtomicFileWriterTest {

    @TempDir
    Path directory;

    @Test
    void writesSnapshotAndCompletes() throws Exception {
        AsyncAtomicFileWriter writer = new AsyncAtomicFileWriter("atomic-writer-test");
        Path target = directory.resolve("character.yml");

        writer.writeUtf8(target, "name: Ada\n").get(5, TimeUnit.SECONDS);

        assertEquals("name: Ada\n", Files.readString(target));
        assertTrue(writer.shutdown(Duration.ofSeconds(5)));
    }

    @Test
    void laterSnapshotsCannotBeOverwrittenByEarlierSnapshots() throws Exception {
        AsyncAtomicFileWriter writer = new AsyncAtomicFileWriter("atomic-writer-order-test");
        Path target = directory.resolve("character.yml");

        writer.writeUtf8(target, "first");
        writer.writeUtf8(target, "second");
        writer.flush().get(5, TimeUnit.SECONDS);

        assertEquals("second", Files.readString(target));
        assertTrue(writer.shutdown(Duration.ofSeconds(5)));
    }

    @Test
    void snapshotsMutableByteArraysAtSubmissionTime() throws Exception {
        AsyncAtomicFileWriter writer = new AsyncAtomicFileWriter("atomic-writer-snapshot-test");
        Path blocker = directory.resolve("blocker.bin");
        Path target = directory.resolve("keys.bin");
        byte[] content = new byte[]{1, 2, 3};

        writer.write(blocker, new byte[4 * 1024 * 1024]);
        writer.write(target, content);
        content[0] = 9;
        writer.flush().get(5, TimeUnit.SECONDS);

        assertEquals(1, Files.readAllBytes(target)[0]);
        assertTrue(writer.shutdown(Duration.ofSeconds(5)));
    }

    @Test
    void updateSeesEarlierQueuedSnapshot() throws Exception {
        AsyncAtomicFileWriter writer = new AsyncAtomicFileWriter("atomic-writer-update-test");
        Path target = directory.resolve("character.yml");

        writer.writeUtf8(target, "name: Ada");
        writer.updateUtf8(target, current -> current + "\nactive: true\n")
                .get(5, TimeUnit.SECONDS);

        assertEquals("name: Ada\nactive: true\n", Files.readString(target));
        assertTrue(writer.shutdown(Duration.ofSeconds(5)));
    }
}
