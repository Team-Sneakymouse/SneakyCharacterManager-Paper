package net.sneakycharactermanager.paper.util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.sneakycharactermanager.paper.SneakyCharacterManager;

public class BungeeMessagingUtil {

    public static void sendByteArray(Player requester, String subChannelName, Object... objects) {
        try (ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteArrayOutput)) {

            out.writeUTF(subChannelName + "_UUID:" + UUID.randomUUID());

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
                else if (object instanceof List && ((List<?>) object).size() > 0 && ((List<?>) object).get(0) instanceof String)
                    writeStringList(out, (List<String>) object);
                else {
                    SneakyCharacterManager.getInstance().getLogger().severe("SneakyCharacterManager attempted to write an unidentified object to a ByteArray!");
                    return;
                }
            }


            if(requester == null){
                SneakyCharacterManager.getInstance().getServer().sendPluginMessage(SneakyCharacterManager.getInstance(), "sneakymouse:" + SneakyCharacterManager.IDENTIFIER, byteArrayOutput.toByteArray());
            }else {
                requester.sendPluginMessage(SneakyCharacterManager.getInstance(), "sneakymouse:" + SneakyCharacterManager.IDENTIFIER, byteArrayOutput.toByteArray());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeStringList(DataOutputStream out, List<String> strings) throws IOException {
        out.writeInt(strings.size());
        for (String string : strings) {
            out.writeUTF(string);
        }
    }

}