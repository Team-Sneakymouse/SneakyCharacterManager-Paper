package net.sneakycharactermanager.bungee.listeners;

import java.util.UUID;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.Connection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.sneakycharactermanager.bungee.Character;
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
                playerData.updateCharacterList(serverInfo);
                break;
            case "characterSelectionGUI" :
                playerUUID = in.readUTF();
                playerData = PlayerData.get(playerUUID);
                playerData.sendCharacterSelectionGui(serverInfo);;
                break;
            case "selectCharacter" :
                playerUUID = in.readUTF();
                playerData = PlayerData.get(playerUUID);
                playerData.loadCharacter(serverInfo, in.readUTF());
                break;
            case "selectCharacterByName" :
                playerUUID = in.readUTF();
                playerData = PlayerData.get(playerUUID);
                playerData.loadCharacterByName(serverInfo, in.readUTF());
                break;
            case "updateCharacter" :
                playerUUID = in.readUTF();
                playerData = PlayerData.get(playerUUID);
                String lastPlayed = playerData.getLastPlayedCharacter();
                int type = in.readInt();
                switch (type){
                    case 1: //Updating Skin
                        playerData.setCharacterSkin(lastPlayed, in.readUTF(), in.readBoolean());
                        break;
                    case 2: //Updating Name
                        playerData.setCharacterName(lastPlayed, in.readUTF());
                        playerData.updateCharacterList(serverInfo);
                        break;
                }
                break;
            case "defaultSkin":
                playerUUID = in.readUTF();
                String characterUUID = in.readUTF();
                String url = in.readUTF();
                boolean slim = in.readBoolean();
                playerData = PlayerData.get(playerUUID);
                playerData.setCharacterSkin(characterUUID, url, slim);
                break;
            case "createNewCharacter" :
                playerUUID = in.readUTF();
                playerData = PlayerData.get(playerUUID);
                playerData.loadCharacter(serverInfo, playerData.createNewCharacter(ProxyServer.getInstance().getPlayer(UUID.fromString(playerUUID)).getName()));
                break;
            case "deleteCharacter" :
                playerUUID = in.readUTF();
                characterUUID = in.readUTF();
                playerData = PlayerData.get(playerUUID);

                Character character = playerData.getCharacter(characterUUID);

                if (character == null) {
                    SneakyCharacterManager.getInstance().getLogger().severe("SneakyCharacterManager tried to delete a character that does not exist.");
                    return;
                }

                playerData.setCharacterEnabled(characterUUID, false);
                PaperMessagingUtil.sendByteArray(serverInfo, "deleteConfirmed", playerUUID, character.getName(), characterUUID);
                playerData.updateCharacterList(serverInfo);
                break;
            default:
                SneakyCharacterManager.getInstance().getLogger().severe("SneakyCharacterManager received a packet but the subchannel was unknown: " + subChannel);
                break;
        }
    }
}