package net.sneakycharactermanager.proxy.core;

import net.sneakycharactermanager.proxy.common.ProxyPlatform;
import net.sneakycharactermanager.proxy.common.ProxyServerConnection;
import net.sneakycharactermanager.proxy.common.ProxyCrypto;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;

public final class ProxyMessenger {

    private final ProxyPlatform platform;
    private final PrivateKey privateKey;
    private final UniformSkinCache uniformSkinCache;
    private final GlobalSkinCache globalSkinCache;

    public ProxyMessenger(ProxyPlatform platform, PrivateKey privateKey, UniformSkinCache uniformSkinCache, GlobalSkinCache globalSkinCache) {
        this.platform = platform;
        this.privateKey = privateKey;
        this.uniformSkinCache = uniformSkinCache;
        this.globalSkinCache = globalSkinCache;
    }

    public void send(ProxyServerConnection server, String subChannel, Object... objects) {
        byte[] signed = encodeSigned(subChannel, objects);
        if (signed == null) return;
        server.sendPluginMessage(signed);
    }

    /**
     * Payload budget per {@code listChunk}. Messages to a backend server are capped at 32767 bytes; this leaves room
     * for the signature and header fields.
     */
    static final int CHUNK_BUDGET_BYTES = 16 * 1024;
    /** Largest plugin message a backend server accepts from the proxy. */
    static final int MAX_MESSAGE_BYTES = 32767;

    /**
     * Sends {@code items} as one or more {@code listChunk(requestId, total, offset, items)} messages. Paper reassembles
     * them by offset, so chunks may arrive over different player connections in any order. An empty list is one
     * chunk with total 0.
     * <p>Every chunk is encoded before any is sent. If one item is over {@link #CHUNK_BUDGET_BYTES} or a signed chunk
     * would exceed {@link #MAX_MESSAGE_BYTES}, the reply could never be delivered complete, so a single
     * {@code listError(requestId, reason)} goes out instead and Paper fails the request right away.
     */
    public void sendChunked(ProxyServerConnection server, String requestId, List<String> items) {
        String error = null;
        for (String item : items) {
            if (utfLength(item) > CHUNK_BUDGET_BYTES) {
                error = "Reply field of " + utfLength(item) + " bytes exceeds the " + CHUNK_BUDGET_BYTES + "-byte limit";
                break;
            }
        }
        List<byte[]> messages = new ArrayList<>();
        int offset = 0;
        for (List<String> chunk : error == null ? chunks(items, CHUNK_BUDGET_BYTES) : List.<List<String>>of()) {
            byte[] signed = encodeSigned("listChunk", requestId, items.size(), offset, chunk);
            if (signed == null) return; // already logged; Paper times out
            if (signed.length > MAX_MESSAGE_BYTES) {
                error = "Reply chunk of " + signed.length + " bytes exceeds the " + MAX_MESSAGE_BYTES + "-byte message limit";
                break;
            }
            messages.add(signed);
            offset += chunk.size();
        }
        if (error != null) {
            platform.logger().warning("Refused list reply " + requestId + ": " + error);
            send(server, "listError", requestId, error);
            return;
        }
        for (byte[] message : messages) server.sendPluginMessage(message);
    }

    /** Splits by encoded size. Callers must reject items over budget first; such an item would be placed alone. */
    static List<List<String>> chunks(List<String> items, int budgetBytes) {
        List<List<String>> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int size = 0;
        for (String item : items) {
            int length = utfLength(item);
            if (!current.isEmpty() && size + length > budgetBytes) {
                chunks.add(current);
                current = new ArrayList<>();
                size = 0;
            }
            current.add(item);
            size += length;
        }
        if (!current.isEmpty() || chunks.isEmpty()) chunks.add(current);
        return chunks;
    }

    /** Bytes {@link DataOutputStream#writeUTF} uses for {@code s}, including its 2-byte length prefix. */
    static int utfLength(String s) {
        int length = 2;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            length += c >= 0x0001 && c <= 0x007F ? 1 : c <= 0x07FF ? 2 : 3;
        }
        return length;
    }

    private byte[] encodeSigned(String subChannel, Object... objects) {
        try {
            byte[] messageBytes = encodeUnsigned(subChannel, objects);
            String signature = ProxyCrypto.sign(messageBytes, privateKey);

            ByteArrayOutputStream finalOut = new ByteArrayOutputStream();
            DataOutputStream dout = new DataOutputStream(finalOut);
            dout.writeUTF(signature);
            dout.write(messageBytes);
            dout.flush();
            return finalOut.toByteArray();
        } catch (Exception e) {
            platform.logger().severe("Failed to encode message for subchannel " + subChannel, e);
            return null;
        }
    }

    private byte[] encodeUnsigned(String subChannel, Object... objects) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(out);

        dout.writeUTF(subChannel);
        for (Object object : objects) {
            if (object instanceof Boolean b) dout.writeBoolean(b);
            else if (object instanceof Byte b) dout.writeByte(b);
            else if (object instanceof Double d) dout.writeDouble(d);
            else if (object instanceof Float f) dout.writeFloat(f);
            else if (object instanceof Integer i) dout.writeInt(i);
            else if (object instanceof Long l) dout.writeLong(l);
            else if (object instanceof Short s) dout.writeShort(s);
            else if (object instanceof String s) dout.writeUTF(s);
            else if (object instanceof CharacterData c) writeCharacter(dout, c);
            else if (object instanceof List<?> list && list.isEmpty()) dout.writeInt(0);
            else if (object instanceof List<?> list && list.get(0) instanceof CharacterData) writeCharacterList(dout, castCharacterList(list));
            else if (object instanceof List<?> list && list.get(0) instanceof String) writeStringList(dout, castStringList(list));
            else throw new IOException("Unknown outgoing object type: " + object.getClass());
        }

        dout.flush();
        return out.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static List<CharacterData> castCharacterList(List<?> list) {
        return (List<CharacterData>) list;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castStringList(List<?> list) {
        return (List<String>) list;
    }

    private void writeCharacterList(DataOutputStream out, List<CharacterData> characters) throws IOException {
        out.writeInt(characters.size());
        for (CharacterData character : characters) writeCharacter(out, character);
    }

    private void writeCharacter(DataOutputStream out, CharacterData character) throws IOException {
        CharacterSkinWire.Resolved resolved = CharacterSkinWire.resolve(character, globalSkinCache, platform.logger());
        String baseUrl = CharacterSkinWire.baseSkinUrlForUniforms(resolved, character);

        out.writeUTF(character.uuid());
        out.writeUTF(character.name());
        out.writeUTF(resolved.skin());
        out.writeUTF(resolved.skinUUID());
        out.writeUTF(resolved.texture() != null ? resolved.texture() : "");
        out.writeUTF(resolved.signature() != null ? resolved.signature() : "");
        out.writeBoolean(character.slim());
        out.writeUTF(character.tags());
        out.writeUTF(Gender.toConfigKeyNullable(character.gender()));
        out.writeUTF(character.title() == null ? "" : character.title());

        List<UniformSkinCache.Variant> variants = uniformSkinCache.getVariants(baseUrl);
        out.writeInt(variants.size());
        for (UniformSkinCache.Variant v : variants) {
            out.writeUTF(v.uniformHash);
            out.writeUTF(v.skinUUID);
            out.writeUTF(v.textureUrl);
            out.writeUTF(v.texture);
            out.writeUTF(v.signature);
        }
    }

    private void writeStringList(DataOutputStream out, List<String> strings) throws IOException {
        out.writeInt(strings.size());
        for (String string : strings) out.writeUTF(string);
    }
}
