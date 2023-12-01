package net.sneakycharactermanager.util;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import net.md_5.bungee.api.config.ServerInfo;
import net.sneakycharactermanager.bungee.SneakyCharacterManager;

public class PaperMessagingUtil {

    public static void sendByteArray(ServerInfo server, String subChannelName, Object... objects) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();

        out.writeUTF( subChannelName );

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

        server.sendData( "SneakyCharacterManager", out.toByteArray() );
    }

}
