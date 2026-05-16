package net.sneakycharactermanager.proxy.core;

import net.sneakycharactermanager.proxy.common.ProxyPlatform;
import net.sneakycharactermanager.proxy.common.ProxyServerConnection;
import net.sneakycharactermanager.proxy.common.ProxyCrypto;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.PrivateKey;
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
