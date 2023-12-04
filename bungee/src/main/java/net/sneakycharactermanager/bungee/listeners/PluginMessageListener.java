package net.sneakycharactermanager.bungee.listeners;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.Connection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.sneakycharactermanager.bungee.PlayerData;
import net.sneakycharactermanager.bungee.SneakyCharacterManager;

public class PluginMessageListener implements Listener {

    @EventHandler
    public void on(PluginMessageEvent event)
    {
        if (!event.getTag().equalsIgnoreCase("sneakymouse:sneakycharactermanager"))
        {
            return;
        }

        Connection connection = event.getSender();
        ServerInfo serverInfo = null;
        if (connection instanceof ProxiedPlayer proxiedPlayer) {
            serverInfo = proxiedPlayer.getServer().getInfo();
        } else if (connection instanceof Server server) {
            serverInfo = server.getInfo();
        }

        ByteArrayDataInput in = ByteStreams.newDataInput( event.getData() );
        String subChannel = in.readUTF();

        switch (subChannel) {
            case "playerJoin" :
                String playerUUID = in.readUTF();
                PlayerData playerData = PlayerData.get(playerUUID);
                playerData.loadLastPlayedCharacter(serverInfo);

                // Get player's bungee character data

                // If bungee character data doesn't exist yet: Make bungee character data, then send packet to backend to make a first character

                // If bungee character data does exist:
                // Grab UUID of last played character
                // Grab matching skin and nickname data
                // Pack these in a standard "load character packet", and send them to the backend

                // In either case: If the backend's "deleteCharacterDataOnServerStart" config is false and the player did not yet have a folder in "characterdata", then the character that they end up on should copy the player's minecraft inventory
                // This is handled at the backend.

                //PaperMessagingUtil.sendByteArray(serverInfo, "loadCharacter", playerUUID);
                break;
            default:
                SneakyCharacterManager.getInstance().getLogger().severe("SneakyCharacterManager received a packet but the subchannel was unknown: " + subChannel);
                break;
        }
    }
}