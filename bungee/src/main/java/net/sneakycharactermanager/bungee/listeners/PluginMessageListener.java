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
import net.sneakycharactermanager.bungee.util.PaperMessagingUtil;

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
                break;
            case "rebuildCharacterMap" :
                playerUUID = in.readUTF();
                playerData = PlayerData.get(playerUUID);
                playerData.rebuildCharacterMap(serverInfo);
                break;
            case "characterSelectionGUI" :
                playerUUID = in.readUTF();
                playerData = PlayerData.get(playerUUID);
                playerData.sendCharacterSelectionGui(serverInfo);;
                break;
            default:
                SneakyCharacterManager.getInstance().getLogger().severe("SneakyCharacterManager received a packet but the subchannel was unknown: " + subChannel);
                break;
        }
    }
}