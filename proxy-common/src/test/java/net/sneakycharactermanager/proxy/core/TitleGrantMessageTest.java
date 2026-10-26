package net.sneakycharactermanager.proxy.core;

import net.sneakycharactermanager.proxy.common.ProxyLogger;
import net.sneakycharactermanager.proxy.common.ProxyPlatform;
import net.sneakycharactermanager.proxy.common.ProxyServerConnection;
import net.sneakycharactermanager.proxy.common.YamlFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TitleGrantMessageTest {
    @TempDir File dataFolder;

    @Test
    void existingTitleSucceedsWithOriginalDisplayAndSyncsBeforeResult() throws Exception {
        ProxyLogger logger = logger();
        YamlFiles.save(new File(dataFolder, "titles.yml"), Map.of("master_fisherman", "Master Fisherman"), logger);
        List<DataInputStream> replies = request("master_fisherman", "MASTER FISHERMAN");

        assertEquals(2, replies.size());
        DataInputStream sync = replies.get(0);
        assertEquals("syncTitles", sync.readUTF());
        sync.readUTF();
        assertEquals(List.of("master_fisherman", "Master Fisherman"), ProxyMessageIO.readStringList(sync));
        DataInputStream result = replies.get(1);
        assertEquals("createTitleResult", result.readUTF());
        result.readUTF();
        result.readUTF();
        assertTrue(result.readBoolean());
        assertEquals("master_fisherman", result.readUTF());
        assertEquals("Master Fisherman", result.readUTF());
        assertEquals("", result.readUTF());
        assertEquals(Map.of("master_fisherman", "Master Fisherman"),
                YamlFiles.load(new File(dataFolder, "titles.yml"), logger));
    }

    @Test
    void newTitleIsPersistedAndSyncedBeforeSuccess() throws Exception {
        List<DataInputStream> replies = request("master_fisherman", " Master Fisherman ");
        assertEquals(2, replies.size());
        DataInputStream sync = replies.get(0);
        assertEquals("syncTitles", sync.readUTF());
        sync.readUTF();
        assertEquals(List.of("master_fisherman", "Master Fisherman"), ProxyMessageIO.readStringList(sync));
        DataInputStream result = replies.get(1);
        assertEquals("createTitleResult", result.readUTF());
        result.readUTF();
        result.readUTF();
        assertTrue(result.readBoolean());
        assertEquals("master_fisherman", result.readUTF());
        assertEquals("Master Fisherman", result.readUTF());
        assertEquals("", result.readUTF());
        assertEquals(Map.of("master_fisherman", "Master Fisherman"),
                YamlFiles.load(new File(dataFolder, "titles.yml"), logger()));
    }

    @Test
    void invalidTitleStillFailsWithoutPersisting() throws Exception {
        List<DataInputStream> replies = request("", "!!!");
        assertEquals(1, replies.size());
        DataInputStream result = replies.getFirst();
        assertEquals("createTitleResult", result.readUTF());
        result.readUTF();
        result.readUTF();
        assertFalse(result.readBoolean());
        assertEquals("", result.readUTF());
        assertEquals("!!!", result.readUTF());
        assertEquals("invalid", result.readUTF());
        assertFalse(new File(dataFolder, "titles.yml").exists());
    }

    private List<DataInputStream> request(String code, String display) throws Exception {
        ProxyLogger logger = logger();
        ProxyPlatform platform = new ProxyPlatform() {
            @Override public File dataFolder() { return dataFolder; }
            @Override public ProxyLogger logger() { return logger; }
            @Override public String playerName(UUID uuid) { return "Player"; }
        };
        ProxyKeys keys = ProxyKeys.loadOrGenerate(dataFolder, logger);
        List<DataInputStream> replies = new ArrayList<>();
        ProxyServerConnection server = new ProxyServerConnection() {
            @Override public void sendPluginMessage(byte[] data) {
                try {
                    DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
                    in.readUTF(); // signature envelope
                    replies.add(in);
                } catch (Exception e) { throw new AssertionError(e); }
            }
            @Override public String debugName() { return "test"; }
        };
        byte[] request = ProxyMessageIO.encodeSignedOutgoing(keys.privateKey(), logger,
                "createTitle_UUID:" + UUID.randomUUID(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), code, display);
        new ProxyCore(platform, keys).onPluginMessage(server, request);
        return replies;
    }

    private static ProxyLogger logger() {
        return new ProxyLogger() {
            @Override public void info(String message) {}
            @Override public void warning(String message) {}
            @Override public void severe(String message) { fail(message); }
            @Override public void severe(String message, Throwable throwable) { fail(message, throwable); }
        };
    }
}
