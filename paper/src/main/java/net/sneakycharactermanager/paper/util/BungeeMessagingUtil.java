package net.sneakycharactermanager.paper.util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.Base64;
import java.security.PrivateKey;
import java.security.Signature;

import org.bukkit.entity.Player;

import net.sneakycharactermanager.paper.SneakyCharacterManager;

public class BungeeMessagingUtil {

	private static String signMessage(byte[] messageBytes, PrivateKey privateKey) throws Exception {
		Signature signature = Signature.getInstance("SHA256withRSA");
		signature.initSign(privateKey);
		signature.update(messageBytes);
		byte[] signedBytes = signature.sign();
		return Base64.getEncoder().encodeToString(signedBytes);
	}

	public static void sendByteArray(Player requester, String subChannelName, Object... objects) {
		try (ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();
			 DataOutputStream out = new DataOutputStream(byteArrayOutput)) {
	
			// Write subchannel name and a unique UUID
			out.writeUTF(subChannelName + "_UUID:" + UUID.randomUUID());
	
			// Serialize the objects into the byte array
			for (Object object : objects) {
				if (object instanceof Boolean) {
					out.writeBoolean((boolean) object);
				} else if (object instanceof Byte) {
					out.writeByte((byte) object);
				} else if (object instanceof Double) {
					out.writeDouble((double) object);
				} else if (object instanceof Float) {
					out.writeFloat((float) object);
				} else if (object instanceof Integer) {
					out.writeInt((int) object);
				} else if (object instanceof Long) {
					out.writeLong((long) object);
				} else if (object instanceof Short) {
					out.writeShort((short) object);
				} else if (object instanceof String) {
					out.writeUTF((String) object);
				} else if (object instanceof List && ((List<?>) object).isEmpty()) {
					out.writeInt(0);
				} else if (object instanceof List && ((List<?>) object).get(0) instanceof String) {
					writeStringList(out, (List<String>) object);
				} else {
					SneakyCharacterManager.getInstance().getLogger().severe("SneakyCharacterManager attempted to write an unidentified object to a ByteArray!");
					return;
				}
			}
	
			byte[] messageBytes = byteArrayOutput.toByteArray();
			
			String signature = signMessage(messageBytes, SneakyCharacterManager.getInstance().getPrivateKey());
	
			try (ByteArrayOutputStream finalOutput = new ByteArrayOutputStream();
				 DataOutputStream finalOut = new DataOutputStream(finalOutput)) {
	
				finalOut.writeUTF(signature);
	
				finalOut.write(messageBytes);
	
				if (requester == null) {
					SneakyCharacterManager.getInstance().getServer().sendPluginMessage(SneakyCharacterManager.getInstance(), "sneakymouse:" + SneakyCharacterManager.IDENTIFIER, finalOutput.toByteArray());
				} else {
					requester.sendPluginMessage(SneakyCharacterManager.getInstance(), "sneakymouse:" + SneakyCharacterManager.IDENTIFIER, finalOutput.toByteArray());
				}
			}
	
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			SneakyCharacterManager.getInstance().getLogger().severe("Failed to generate signature: " + e.getMessage());
		}
	}


    private static void writeStringList(DataOutputStream out, List<String> strings) throws IOException {
        out.writeInt(strings.size());
        for (String string : strings) {
            out.writeUTF(string);
        }
    }

}