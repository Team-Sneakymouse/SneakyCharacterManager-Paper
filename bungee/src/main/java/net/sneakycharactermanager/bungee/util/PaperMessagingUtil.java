package net.sneakycharactermanager.bungee.util;

import java.util.List;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.sneakycharactermanager.bungee.Character;
import net.sneakycharactermanager.bungee.Gender;
import net.sneakycharactermanager.bungee.SneakyCharacterManager;

public class PaperMessagingUtil {

	private static String signMessage(byte[] messageBytes, PrivateKey privateKey) throws Exception {
		Signature signature = Signature.getInstance("SHA256withRSA");
		signature.initSign(privateKey);
		signature.update(messageBytes);
		byte[] signedBytes = signature.sign();
		return Base64.getEncoder().encodeToString(signedBytes);
	}

	public static void sendByteArray(ServerInfo server, String subChannelName, Object... objects) {
		PrivateKey privateKey = SneakyCharacterManager.getInstance().getPrivateKey();

		if (privateKey == null)
			return;

		ByteArrayDataOutput out = ByteStreams.newDataOutput();

		out.writeUTF(subChannelName);

		for (Object object : objects) {
			if (object.getClass() == Boolean.class)
				out.writeBoolean((boolean) object);
			else if (object.getClass() == Byte.class || object.getClass() == byte.class)
				out.writeByte((int) object);
			else if (object.getClass() == Double.class || object.getClass() == double.class)
				out.writeDouble((double) object);
			else if (object.getClass() == Float.class || object.getClass() == float.class)
				out.writeFloat((float) object);
			else if (object.getClass() == Integer.class || object.getClass() == int.class)
				out.writeInt((int) object);
			else if (object.getClass() == Long.class || object.getClass() == long.class)
				out.writeLong((long) object);
			else if (object.getClass() == Short.class || object.getClass() == short.class)
				out.writeShort((int) object);
			else if (object.getClass() == String.class)
				out.writeUTF((String) object);
			else if (object.getClass() == Character.class)
				writeCharacter(out, (Character) object);
			else if (object instanceof List && ((List<?>) object).isEmpty())
				out.writeInt(0);
			else if (object instanceof List && ((List<?>) object).get(0) instanceof Character)
				writeCharacterList(out, (List<Character>) object);
			else if (object instanceof List && ((List<?>) object).get(0) instanceof String)
				writeStringList(out, (List<String>) object);
			else {
				SneakyCharacterManager.getInstance().getLogger()
						.severe("SneakyCharacterManager attempted to write an unidentified object to a ByteArray!");
				return;
			}
		}

		try {
			byte[] messageBytes = out.toByteArray();
			String signature = signMessage(messageBytes, privateKey);

			ByteArrayDataOutput finalOut = ByteStreams.newDataOutput();
			finalOut.writeUTF(signature);
			finalOut.write(messageBytes);

			server.sendData("sneakymouse:" + SneakyCharacterManager.IDENTIFIER, finalOut.toByteArray());
		} catch (Exception e) {
			SneakyCharacterManager.getInstance().getLogger().severe("Failed to sign the message: " + e.getMessage());
		}
	}

	public static void sendByteArray(ProxiedPlayer player, String subChannelName, Object... objects) {
		PrivateKey privateKey = SneakyCharacterManager.getInstance().getPrivateKey();

		if (privateKey == null)
			return;

		ByteArrayDataOutput out = ByteStreams.newDataOutput();

		out.writeUTF(subChannelName);

		for (Object object : objects) {
			if (object.getClass() == Boolean.class)
				out.writeBoolean((boolean) object);
			else if (object.getClass() == Byte.class || object.getClass() == byte.class)
				out.writeByte((int) object);
			else if (object.getClass() == Double.class || object.getClass() == double.class)
				out.writeDouble((double) object);
			else if (object.getClass() == Float.class || object.getClass() == float.class)
				out.writeFloat((float) object);
			else if (object.getClass() == Integer.class || object.getClass() == int.class)
				out.writeInt((int) object);
			else if (object.getClass() == Long.class || object.getClass() == long.class)
				out.writeLong((long) object);
			else if (object.getClass() == Short.class || object.getClass() == short.class)
				out.writeShort((int) object);
			else if (object.getClass() == String.class)
				out.writeUTF((String) object);
			else if (object.getClass() == Character.class)
				writeCharacter(out, (Character) object);
			else if (object instanceof List && ((List<?>) object).isEmpty())
				out.writeInt(0);
			else if (object instanceof List && ((List<?>) object).get(0) instanceof Character)
				writeCharacterList(out, (List<Character>) object);
			else if (object instanceof List && ((List<?>) object).get(0) instanceof String)
				writeStringList(out, (List<String>) object);
			else {
				SneakyCharacterManager.getInstance().getLogger()
						.severe("SneakyCharacterManager attempted to write an unidentified object to a ByteArray!");
				return;
			}
		}

		try {
			byte[] messageBytes = out.toByteArray();
			String signature = signMessage(messageBytes, privateKey);

			ByteArrayDataOutput finalOut = ByteStreams.newDataOutput();
			finalOut.writeUTF(signature);
			finalOut.write(messageBytes);

			player.sendData("sneakymouse:" + SneakyCharacterManager.IDENTIFIER, finalOut.toByteArray());
		} catch (Exception e) {
			SneakyCharacterManager.getInstance().getLogger().severe("Failed to sign the message: " + e.getMessage());
		}
	}

	// This function is used to serialize a List<Character>, in order to send over
	// all the data required to build the character selection GUI
	private static void writeCharacterList(ByteArrayDataOutput out, List<Character> characters) {
		out.writeInt(characters.size());
		for (Character character : characters) {
			writeCharacter(out, character);
		}
	}

	private static void writeCharacter(ByteArrayDataOutput out, Character character) {
		out.writeUTF(character.getUUID());
		out.writeUTF(character.getName());
		out.writeUTF(character.getSkin());
		out.writeUTF(character.getSkinUUID());
		out.writeBoolean(character.isSlim());
		out.writeUTF(character.getTags());
		out.writeUTF(Gender.toConfigKeyNullable(character.getGender()));
	}

	private static void writeStringList(ByteArrayDataOutput out, List<String> strings) {
		out.writeInt(strings.size());
		for (String string : strings) {
			out.writeUTF(string);
		}
	}

}
