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
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Proxy side of the character directory and chunked list replies. */
class DirectoryLookupTest {
    @TempDir File dataFolder;
    static final String OWNER = "11111111-1111-1111-1111-111111111111";
    static final String OTHER = "33333333-3333-3333-3333-333333333333";
    static final String FIRST = "aaaaaaaa-0000-0000-0000-000000000001";
    static final String DELETED = "aaaaaaaa-0000-0000-0000-000000000002";
    static final String SECOND = "aaaaaaaa-0000-0000-0000-000000000003";
    final List<byte[]> sent = new ArrayList<>();
    ProxyLogger logger;
    ProxyKeys keys;
    ProxyCore core;

    @BeforeEach void setUp() {
        logger = new ProxyLogger() {
            @Override public void info(String message) {}
            @Override public void warning(String message) {}
            @Override public void severe(String message) { fail(message); }
            @Override public void severe(String message, Throwable throwable) { fail(message, throwable); }
        };
        keys = ProxyKeys.loadOrGenerate(dataFolder, logger);
        core = new ProxyCore(platform(), keys, Runnable::run);
        writeAccount(OWNER, FIRST, Map.of("name", "&aMouse", "enabled", true),
                DELETED, Map.of("name", "Gone", "enabled", false),
                SECOND, Map.of("name", "<gold>Rat</gold>"));
        writeAccount("template", "guard", Map.of("name", "Guard"));
    }

    private ProxyPlatform platform() {
        return new ProxyPlatform() {
            @Override public File dataFolder() { return dataFolder; }
            @Override public ProxyLogger logger() { return logger; }
            @Override public String playerName(UUID uuid) { return "Player"; }
        };
    }

    private void writeAccount(String owner, Object... idsAndSections) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("lastPlayedCharacter", idsAndSections[0]);
        for (int i = 0; i < idsAndSections.length; i += 2) root.put((String) idsAndSections[i], new LinkedHashMap<>((Map<?, ?>) idsAndSections[i + 1]));
        YamlFiles.save(new File(dataFolder, "characterdata/" + owner + ".yml"), root, logger);
    }

    private void request(String subChannel, Object... fields) {
        byte[] message = ProxyMessageIO.encodeSignedOutgoing(keys.privateKey(), logger, subChannel + "_UUID:" + UUID.randomUUID(), fields);
        core.onPluginMessage(new ProxyServerConnection() {
            @Override public void sendPluginMessage(byte[] data) { sent.add(data); }
            @Override public String debugName() { return "test"; }
        }, message);
    }

    record Chunk(String requestId, int total, int offset, List<String> items) {}

    private List<Chunk> chunks() throws Exception {
        List<Chunk> chunks = new ArrayList<>();
        for (byte[] data : sent) {
            assertTrue(data.length <= ProxyMessenger.MAX_MESSAGE_BYTES, "signed message of " + data.length + " bytes");
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            in.readUTF(); // signature envelope
            assertEquals("listChunk", in.readUTF());
            chunks.add(new Chunk(in.readUTF(), in.readInt(), in.readInt(), ProxyMessageIO.readStringList(in)));
        }
        return chunks;
    }

    @Test void ownerLookupListsEveryCharacterInFileOrderWithEnabledFlag() throws Exception {
        request("directoryOwner", OWNER, "req-1");
        List<Chunk> chunks = chunks();
        assertEquals(1, chunks.size());
        assertEquals(new Chunk("req-1", 12, 0, List.of(
                FIRST, OWNER, "&aMouse", "true",
                DELETED, OWNER, "Gone", "false",
                SECOND, OWNER, "<gold>Rat</gold>", "true")), chunks.getFirst());
    }

    @Test void unknownTemplateAndNonUuidOwnersAreEmptyAndCreateNoFiles() throws Exception {
        for (String owner : List.of(OTHER, "template", "../characterdata/" + OWNER, " " + OWNER)) request("directoryOwner", owner, "r");
        for (Chunk chunk : chunks()) assertEquals(new Chunk("r", 0, 0, List.of()), chunk);
        assertFalse(new File(dataFolder, "characterdata/" + OTHER + ".yml").exists());
    }

    @Test void characterLookupFindsAnyAccountAndSkipsTemplates() throws Exception {
        request("directoryCharacter", SECOND, "a");
        request("directoryCharacter", "guard", "b");
        request("directoryCharacter", "lastPlayedCharacter", "c");
        List<Chunk> chunks = chunks();
        assertEquals(List.of(SECOND, OWNER, "<gold>Rat</gold>", "true"), chunks.get(0).items());
        assertEquals(List.of(), chunks.get(1).items());
        assertEquals(List.of(), chunks.get(2).items());
    }

    @Test void characterLookupReturnsEveryHolderOfADuplicateSoPaperCanRefuse() throws Exception {
        writeAccount(OTHER, SECOND, Map.of("name", "Copy"));
        request("directoryCharacter", SECOND, "a");
        assertEquals(8, chunks().getFirst().items().size());
    }

    @Test void searchWithRequestIdIsChunkedAndOldPayloadGetsTheOriginalReply() throws Exception {
        request("getAllCharacters", OWNER, "mouse", "req");
        assertEquals(new Chunk("req", 1, 0, List.of(OWNER + "$&aMouse")), chunks().getFirst());

        sent.clear();
        request("getAllCharacters", OWNER, "mouse");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(sent.getFirst()));
        in.readUTF();
        assertEquals("getAllCharacters", in.readUTF());
        assertEquals(OWNER, in.readUTF());
        assertEquals(List.of(OWNER + "$&aMouse"), ProxyMessageIO.readStringList(in));
    }

    @Test void largeReplySplitsIntoChunksThatEachFitAPluginMessage() throws Exception {
        Object[] many = new Object[2 * 2000];
        for (int i = 0; i < 2000; i++) {
            many[2 * i] = new UUID(0, i).toString();
            many[2 * i + 1] = Map.of("name", "Character with a fairly long display name number " + i + " ✦");
        }
        writeAccount(OTHER, many);
        request("directoryOwner", OTHER, "big");
        List<Chunk> chunks = chunks();
        assertTrue(chunks.size() > 1);
        List<String> joined = new ArrayList<>();
        int offset = 0;
        for (Chunk chunk : chunks) {
            assertEquals(8000, chunk.total());
            assertEquals(offset, chunk.offset());
            offset += chunk.items().size();
            joined.addAll(chunk.items());
        }
        assertEquals(8000, joined.size());
        assertEquals("Character with a fairly long display name number 1999 ✦", joined.get(7998));
        for (byte[] data : sent) assertTrue(data.length < 32767, "chunk of " + data.length + " bytes");
    }

    @Test void oversizedFieldIsRefusedWithAnErrorInsteadOfAnUndeliverableChunk() throws Exception {
        // 33,000 bytes: over the message limit on its own. 17,000: fits a message but not the chunk budget.
        for (int size : List.of(33_000, 17_000)) {
            sent.clear();
            writeAccount(OTHER, FIRST, Map.of("name", "x".repeat(size)), SECOND, Map.of("name", "Fine"));
            request("directoryOwner", OTHER, "huge");
            assertEquals(1, sent.size(), "only the error is sent, no partial chunks");
            assertTrue(sent.getFirst().length <= ProxyMessenger.MAX_MESSAGE_BYTES);
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(sent.getFirst()));
            in.readUTF(); // signature envelope
            assertEquals("listError", in.readUTF());
            assertEquals("huge", in.readUTF());
            assertTrue(in.readUTF().contains("exceeds"));
        }
    }

    @Test void fieldExactlyAtTheBudgetIsStillDelivered() throws Exception {
        String name = "x".repeat(ProxyMessenger.CHUNK_BUDGET_BYTES - 2);
        writeAccount(OTHER, FIRST, Map.of("name", name));
        request("directoryOwner", OTHER, "edge");
        List<String> joined = new ArrayList<>();
        for (Chunk chunk : chunks()) joined.addAll(chunk.items());
        assertEquals(List.of(FIRST, OTHER, name, "true"), joined);
    }

    @Test void utfLengthMatchesWriteUtf() throws Exception {
        for (String s : List.of("", "abc", "\u0000", "é", "✦", "😀 mixed ascii")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            new DataOutputStream(bytes).writeUTF(s);
            assertEquals(bytes.size(), ProxyMessenger.utfLength(s), s);
        }
    }

    @Test void chunksKeepOrderAndNeverDropAnOversizedItem() {
        assertEquals(List.of(List.of()), ProxyMessenger.chunks(List.of(), 10));
        assertEquals(List.of(List.of("aaaa", "bb"), List.of("cccccccccc"), List.of("d")),
                ProxyMessenger.chunks(List.of("aaaa", "bb", "cccccccccc", "d"), 10));
    }
}
