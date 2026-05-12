package net.sneakycharactermanager.proxy.core;

import net.sneakycharactermanager.proxy.common.ProxyCrypto;
import net.sneakycharactermanager.proxy.common.ProxyLogger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

public final class ProxyMessageIO {
    private ProxyMessageIO() {}

    /**
     * Proxy receives from Paper (incoming). Format: [signatureUTF][messageBytes...]
     * messageBytes begins with: [channelDataUTF] where channelDataUTF is subChannel + \"_UUID:\" + uuid
     */
    public static IncomingVerified decodeAndVerifyIncoming(byte[] raw, PublicKey publicKey, ProxyLogger logger) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
            String receivedSignature = in.readUTF();

            int remainingBytes = raw.length - (receivedSignature.getBytes(StandardCharsets.UTF_8).length + 2);
            if (remainingBytes < 0) return null;

            byte[] messageBytes = new byte[remainingBytes];
            in.readFully(messageBytes);

            boolean ok = ProxyCrypto.verify(messageBytes, receivedSignature, publicKey);
            if (!ok) {
                logger.severe("Received a message with an invalid signature! Ignoring.");
                return null;
            }

            DataInputStream msg = new DataInputStream(new ByteArrayInputStream(messageBytes));
            String channelData = msg.readUTF();
            String[] parts = channelData.split("_UUID:");
            if (parts.length != 2) return null;
            return new IncomingVerified(parts[0], parts[1], msg);
        } catch (Exception e) {
            logger.severe("Failed to decode incoming plugin message", e);
            return null;
        }
    }

    public record IncomingVerified(String subChannel, String requestUuid, DataInputStream payload) {}

    /**
     * Proxy sends to Paper (outgoing). Format: [signatureUTF][messageBytes...]
     * messageBytes begins with: [subChannelUTF] (no _UUID suffix on outgoing proxy messages).
     */
    public static byte[] encodeSignedOutgoing(PrivateKey privateKey, ProxyLogger logger, String subChannel, Object... objects) {
        try {
            byte[] messageBytes = encodeUnsignedOutgoing(subChannel, objects);
            String signature = ProxyCrypto.sign(messageBytes, privateKey);

            ByteArrayOutputStream finalOut = new ByteArrayOutputStream();
            DataOutputStream dout = new DataOutputStream(finalOut);
            dout.writeUTF(signature);
            dout.write(messageBytes);
            dout.flush();
            return finalOut.toByteArray();
        } catch (Exception e) {
            logger.severe("Failed to sign the message: " + e.getMessage(), e);
            return null;
        }
    }

    static byte[] encodeUnsignedOutgoing(String subChannel, Object... objects) throws IOException {
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
            else if (object instanceof List<?> list && list.isEmpty()) dout.writeInt(0);
            else if (object instanceof List<?> list && list.get(0) instanceof String) writeStringList(dout, castStringList(list));
            else throw new IOException("Unknown outgoing object type: " + object.getClass());
        }

        dout.flush();
        return out.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static List<String> castStringList(List<?> list) {
        return (List<String>) list;
    }

    private static void writeStringList(DataOutputStream out, List<String> strings) throws IOException {
        out.writeInt(strings.size());
        for (String string : strings) out.writeUTF(string);
    }

    public static List<String> readStringList(DataInputStream in) throws IOException {
        int size = in.readInt();
        List<String> strings = new ArrayList<>();
        while (strings.size() < size) strings.add(in.readUTF());
        return strings;
    }
}

