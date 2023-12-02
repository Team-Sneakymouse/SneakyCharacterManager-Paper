package net.sneakycharactermanager.paper.util;

import net.sneakycharactermanager.paper.SneakyCharacterManager;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class BungeeMessagingUtil {

    public static void sendByteArray(String subChannelName, Object... objects) {
        try (ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteArrayOutput)) {

            out.writeUTF(subChannelName);

            for (Object object : objects) {
                if (object instanceof Boolean o)
                    out.writeBoolean(o);
                else if (object instanceof Byte o)
                    out.writeByte(o);
                else if (object instanceof Double o)
                    out.writeDouble(o);
                else if (object instanceof Float o)
                    out.writeFloat(o);
                else if (object instanceof Integer o)
                    out.writeInt(o);
                else if (object instanceof Long o)
                    out.writeLong(o);
                else if (object instanceof Short o)
                    out.writeShort(o);
                else if (object instanceof String o)
                    out.writeUTF(o);
                else SneakyCharacterManager.getInstance().getLogger().severe( "SneakyCharacterManager attempted to write an unidentified object to a ByteArray!" );
            }

            SneakyCharacterManager.getInstance().getServer().sendPluginMessage(SneakyCharacterManager.getInstance(), "sneakymouse:sneakycharactermanager", byteArrayOutput.toByteArray());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}