package net.sneakycharactermanager.paper.util;

import net.sneakycharactermanager.paper.SneakyCharacterManager;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.bukkit.Bukkit;

public class BungeeMessagingUtil {

    //TODO: Currently unused. Consider deleting.
    public static void sendByteArrayDelayed(int delayTicks, String subChannelName, Object... objects) {
        Bukkit.getScheduler().runTaskLater(SneakyCharacterManager.getInstance(), () -> {
            sendByteArray(subChannelName, objects);
        }, delayTicks);
    }

    public static void sendByteArray(String subChannelName, Object... objects) {
        try (ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteArrayOutput)) {

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
                else SneakyCharacterManager.getInstance().getLogger().severe("SneakyCharacterManager attempted to write an unidentified object to a ByteArray!");
            }

            SneakyCharacterManager.getInstance().getServer().sendPluginMessage(SneakyCharacterManager.getInstance(), "sneakymouse:sneakycharactermanager", byteArrayOutput.toByteArray());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}