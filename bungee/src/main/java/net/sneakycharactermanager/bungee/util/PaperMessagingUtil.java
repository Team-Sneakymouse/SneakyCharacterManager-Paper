package net.sneakycharactermanager.bungee.util;

import java.util.List;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import net.md_5.bungee.api.config.ServerInfo;

import net.sneakycharactermanager.bungee.SneakyCharacterManager;
import net.sneakycharactermanager.bungee.Character;

public class PaperMessagingUtil {

    public static void sendByteArray(ServerInfo server, String subChannelName, Object... objects) {
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
            else if (object instanceof List && ((List<?>) object).get(0) instanceof Character)
                writeCharacterList(out, (List<Character>) object);
            else SneakyCharacterManager.getInstance().getLogger().severe("SneakyCharacterManager attempted to write an unidentified object to a ByteArray!");
        }

        server.sendData("sneakymouse:sneakycharactermanager", out.toByteArray());
    }

    // This function is used to serialize a List<Character>, in order to send over all the data required to build the character selection GUI
    private static void writeCharacterList(ByteArrayDataOutput out, List<Character> characters) {
        out.writeInt(characters.size());
        for (Character character : characters) {
            out.writeUTF(character.getUUID());
            out.writeUTF(character.getName());
            out.writeUTF(character.getSkin());
        }
    }

}
