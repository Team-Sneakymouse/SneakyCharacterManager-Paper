package net.sneakycharactermanager.proxy.core;

import net.sneakycharactermanager.proxy.common.ProxyLogger;
import net.sneakycharactermanager.proxy.common.ProxyPlatform;
import net.sneakycharactermanager.proxy.common.ProxyServerConnection;
import net.sneakycharactermanager.proxy.common.YamlFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Character IDs are global: a template save may not reuse a player's character ID. */
class TemplateCharacterIdTest {
    @TempDir File dataFolder;
    static final String OWNER = "11111111-1111-1111-1111-111111111111";
    static final String TAKEN = "22222222-2222-2222-2222-222222222222";
    final List<String> warnings = new ArrayList<>();
    final List<DataInputStream> replies = new ArrayList<>();
    ProxyCore core;
    ProxyKeys keys;
    ProxyLogger logger;

    @BeforeEach void setUp() {
        logger = new ProxyLogger() {
            @Override public void info(String message) {}
            @Override public void warning(String message) { warnings.add(message); }
            @Override public void severe(String message) { fail(message); }
            @Override public void severe(String message, Throwable throwable) { fail(message, throwable); }
        };
        ProxyPlatform platform = new ProxyPlatform() {
            @Override public File dataFolder() { return dataFolder; }
            @Override public ProxyLogger logger() { return logger; }
            @Override public String playerName(UUID uuid) { return "Player"; }
        };
        keys = ProxyKeys.loadOrGenerate(dataFolder, logger);
        core = new ProxyCore(platform, keys);
        File characters = new File(dataFolder, "characterdata");
        Map<String, Object> player = new LinkedHashMap<>();
        player.put("lastPlayedCharacter", TAKEN);
        player.put(TAKEN, new LinkedHashMap<>(Map.of("enabled", true, "name", "Mouse")));
        YamlFiles.save(new File(characters, OWNER + ".yml"), player, logger);
    }

    private void save(String id, String name, Object... trailing) {
        Object[] fields = new Object[5 + trailing.length];
        System.arraycopy(new Object[]{id, name, "http://example.test/skin", "", false}, 0, fields, 0, 5);
        System.arraycopy(trailing, 0, fields, 5, trailing.length);
        byte[] request = ProxyMessageIO.encodeSignedOutgoing(keys.privateKey(), logger,
                "saveTemplateChar_UUID:" + UUID.randomUUID(), fields);
        core.onPluginMessage(new ProxyServerConnection() {
            @Override public void sendPluginMessage(byte[] data) {
                try {
                    DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
                    in.readUTF(); // signature envelope
                    replies.add(in);
                } catch (Exception e) { throw new AssertionError(e); }
            }
            @Override public String debugName() { return "test"; }
        }, request);
    }

    private Map<String, Object> templates() {
        return YamlFiles.load(new File(dataFolder, "characterdata/template.yml"), logger);
    }

    @Test void templateTakingAPlayersIdIsRefusedAndAdminIsTold() throws Exception {
        save(TAKEN, "Copy", "admin-uuid");
        assertFalse(templates().containsKey(TAKEN));
        assertEquals(1, replies.size());
        DataInputStream reply = replies.getFirst();
        assertEquals("saveTemplateCharRejected", reply.readUTF());
        assertEquals("admin-uuid", reply.readUTF());
        assertEquals(TAKEN, reply.readUTF());
        assertEquals(OWNER, reply.readUTF());
        assertTrue(warnings.stream().anyMatch(w -> w.contains(TAKEN) && w.contains(OWNER)));
    }

    @Test void oldPaperPayloadWithoutAdminIsStillCheckedAndAccepted() throws Exception {
        save(TAKEN, "Copy");
        assertFalse(templates().containsKey(TAKEN));
        DataInputStream reply = replies.getFirst();
        assertEquals("saveTemplateCharRejected", reply.readUTF());
        assertEquals("", reply.readUTF());

        save("guard", "Guard");
        assertTrue(templates().containsKey("guard"));
    }

    @Test void templateMayOverwriteItsOwnId() {
        save("guard", "Guard", "admin-uuid");
        save("guard", "Guard 2", "admin-uuid");
        assertTrue(replies.isEmpty());
        assertEquals("Guard 2", ((Map<?, ?>) templates().get("guard")).get("name"));
    }

    @Test void ownersOfCharacterIgnoresTheLastPlayedPointer() {
        PlayerDataRepository repository = new PlayerDataRepository(new ProxyPlatform() {
            @Override public File dataFolder() { return dataFolder; }
            @Override public ProxyLogger logger() { return logger; }
            @Override public String playerName(UUID uuid) { return "Player"; }
        }, null);
        assertEquals(List.of(OWNER), repository.ownersOfCharacter(TAKEN));
        assertEquals(List.of(), repository.ownersOfCharacter("lastPlayedCharacter"));
        assertEquals(List.of(), repository.ownersOfCharacter("missing"));
    }
}
