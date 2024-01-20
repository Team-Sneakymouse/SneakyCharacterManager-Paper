package net.sneakycharactermanager.bungee.listeners;

import java.util.ArrayList;
import java.util.List;
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

    private List<String> handledRequests;
    public PluginMessageListener(){
        handledRequests = new ArrayList<>();
    }

    @EventHandler
    public void on(PluginMessageEvent event)
    {
        if (!event.getTag().equalsIgnoreCase("sneakymouse:" + SneakyCharacterManager.IDENTIFIER))
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

        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String channelData = in.readUTF();

        String[] _data = channelData.split("_UUID:");

        String subChannel = _data[0];
        String uuid = _data[1];

        if(handledRequests.contains(uuid)){
            SneakyCharacterManager.getInstance().getLogger().warning("Recieved duplicated message! Ignoring");
            return;
        }

        handledRequests.add(uuid);

        switch (subChannel) {
            case "playerJoin" :
                String playerUUID = in.readUTF();
                PlayerData playerData = PlayerData.get(playerUUID);
                playerData.loadLastPlayedCharacter(serverInfo);
                break;
            case "playerQuit" :
                playerUUID = in.readUTF();
                PlayerData.remove(playerUUID);
                break;
            case "characterSelectionGUI" :
                playerUUID = in.readUTF();
                playerData = PlayerData.get(playerUUID);
                String requesterUUID = in.readUTF();
                playerData.sendEnabledCharacters(serverInfo, subChannel, requesterUUID);
                break;
            case "preloadSkins" :
                playerUUID = in.readUTF();
                playerData = PlayerData.get(playerUUID);
                playerData.sendEnabledCharacters(serverInfo, subChannel, playerUUID);
                break;
            case "selectCharacter" :
                playerUUID = in.readUTF();
                playerData = PlayerData.get(playerUUID);
                playerData.loadCharacter(serverInfo, in.readUTF(), false);
                break;
            case "tempCharacter" :
                requesterUUID = in.readUTF();
                playerUUID = in.readUTF();
                playerData = PlayerData.get(playerUUID);
                playerData.loadTempCharacter(serverInfo, requesterUUID, in.readUTF());
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
                switch (type) {
                    case 1: //Updating Skin
                        playerData.setCharacterSkin(lastPlayed, in.readUTF(), in.readBoolean());
                        break;
                    case 2: //Updating Name
                        playerData.setCharacterName(lastPlayed, in.readUTF());
                        playerData.updateCharacterList(serverInfo);
                        break;
                    case 3: //Updating enabled
                        // This case is here for consistency only. A player should never be able to change the enabled state of their current character, so it does nothing.
                        break;
                    case 4: //Updating Tags
                        playerData.setCharacterTags(lastPlayed, readStringList(in));
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
                characterUUID = playerData.createNewCharacter(ProxyServer.getInstance().getPlayer(UUID.fromString(playerUUID)).getName());
                if (characterUUID != null) playerData.loadCharacter(serverInfo, characterUUID, true);
                break;
            case "deleteCharacter" :
                playerUUID = in.readUTF();
                characterUUID = in.readUTF();
                playerData = PlayerData.get(playerUUID);

                Character character = playerData.getCharacter(characterUUID);

                playerData.setCharacterEnabled(characterUUID, false);
                PaperMessagingUtil.sendByteArray(serverInfo, "deleteConfirmed", playerUUID, character.getName(), characterUUID);
                playerData.updateCharacterList(serverInfo);
                break;
            default:
                SneakyCharacterManager.getInstance().getLogger().severe("SneakyCharacterManager received a packet but the subchannel was unknown: " + subChannel);
                break;
        }
    }

    public static List<String> readStringList(ByteArrayDataInput in) {
        int size = in.readInt();

        List<String> strings = new ArrayList<>();
        while (strings.size() < size) {
            strings.add(in.readUTF());
        }

        return strings;
    }
    
}