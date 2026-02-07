package net.sneakycharactermanager.bungee.listeners;

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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public class PluginMessageListener implements Listener {

    private final List<String> handledRequests;

    public PluginMessageListener() {
        handledRequests = new ArrayList<>();
    }

    public boolean verifySignature(byte[] messageBytes, String receivedSignature) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(SneakyCharacterManager.getInstance().getPublicKey());
            signature.update(messageBytes);
            byte[] decodedSignature = Base64.getDecoder().decode(receivedSignature);
            return signature.verify(decodedSignature);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @EventHandler
    public void on(PluginMessageEvent event) {
        if (!event.getTag().equalsIgnoreCase("sneakymouse:" + SneakyCharacterManager.IDENTIFIER)) {
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

        String receivedSignature = in.readUTF();

        byte[] messageBytes = new byte[event.getData().length - receivedSignature.getBytes(StandardCharsets.UTF_8).length - 2];
        in.readFully(messageBytes);

        if (!verifySignature(messageBytes, receivedSignature)) {
            SneakyCharacterManager.getInstance().getLogger().severe("Received a message with an invalid signature! Ignoring.");
            return;
        }

        ByteArrayDataInput messageIn = ByteStreams.newDataInput(messageBytes);

        String channelData = messageIn.readUTF();
        String[] _data = channelData.split("_UUID:");

        String subChannel = _data[0];
        String uuid = _data[1];

        if (handledRequests.contains(uuid)) {
            SneakyCharacterManager.getInstance().getLogger().warning("Received duplicated message! Ignoring");
            return;
        }

        handledRequests.add(uuid);

        switch (subChannel) {
            case "playerJoin":
                String playerUUID = messageIn.readUTF();
                PlayerData playerData = PlayerData.get(playerUUID);
                playerData.loadLastPlayedCharacter(serverInfo);
                break;
            case "playerQuit":
                playerUUID = messageIn.readUTF();
                PlayerData.remove(playerUUID);
                break;
            case "characterSelectionGUI":
                playerUUID = messageIn.readUTF();
                playerData = PlayerData.get(playerUUID);
                String requesterUUID = messageIn.readUTF();
                playerData.sendEnabledCharacters(serverInfo, subChannel, requesterUUID);
                break;
            case "preloadSkins":
                playerUUID = messageIn.readUTF();
                playerData = PlayerData.get(playerUUID);
                playerData.sendEnabledCharacters(serverInfo, subChannel, playerUUID);
                break;
            case "selectCharacter":
                playerUUID = messageIn.readUTF();
                playerData = PlayerData.get(playerUUID);
                playerData.loadCharacter(serverInfo, messageIn.readUTF(), false);
                break;
            case "tempCharacter":
                requesterUUID = messageIn.readUTF();
                playerUUID = messageIn.readUTF();
                playerData = PlayerData.get(playerUUID);
                playerData.loadTempCharacter(serverInfo, requesterUUID, messageIn.readUTF());
                break;
            case "selectCharacterByName":
                playerUUID = messageIn.readUTF();
                playerData = PlayerData.get(playerUUID);
                playerData.loadCharacterByName(serverInfo, messageIn.readUTF());
                break;
            case "updateCharacter":
                playerUUID = messageIn.readUTF();
                playerData = PlayerData.get(playerUUID);
                String characterUUID = messageIn.readUTF();
                int type = messageIn.readInt();
                switch (type) {
                    case 1: // Updating Skin
                        playerData.setCharacterSkin(characterUUID, messageIn.readUTF(), messageIn.readUTF(), messageIn.readBoolean());
                        break;
                    case 2: // Updating Name
                        playerData.setCharacterName(characterUUID, messageIn.readUTF());
                        playerData.updateCharacterList(serverInfo);
                        break;
                    case 3: // Updating enabled
                        Character character = playerData.getCharacter(characterUUID);

                        Boolean newEnabled = messageIn.readBoolean();

                        playerData.setCharacterEnabled(characterUUID, newEnabled);
                        if (!newEnabled)
                            PaperMessagingUtil.sendByteArray(serverInfo, "deleteConfirmed", playerUUID, character.getName(), characterUUID);
                        playerData.updateCharacterList(serverInfo);
                        break;
                    case 4: // Updating Tags
                        playerData.setCharacterTags(characterUUID, messageIn.readUTF());
                        break;
                    case 5: // Updating Gender
                        playerData.setCharacterGender(characterUUID, messageIn.readUTF());
                        playerData.updateCharacterList(serverInfo);
                        break;
                }
                break;
            case "defaultSkin":
                playerUUID = messageIn.readUTF();
                characterUUID = messageIn.readUTF();
                String url = messageIn.readUTF();
                String skinUUID = messageIn.readUTF();
                boolean slim = messageIn.readBoolean();
                playerData = PlayerData.get(playerUUID);
                playerData.setCharacterSkin(characterUUID, url, skinUUID, slim);
                break;
            case "createNewCharacter":
                playerUUID = messageIn.readUTF();
                playerData = PlayerData.get(playerUUID);
                characterUUID = playerData.createNewCharacter(ProxyServer.getInstance().getPlayer(UUID.fromString(playerUUID)).getName());
                if (characterUUID != null) playerData.loadCharacter(serverInfo, characterUUID, true);
                break;
            case "saveTemplateChar":
                String characterID = messageIn.readUTF();
                String characterName = messageIn.readUTF();
                String characterSkin = messageIn.readUTF();
                skinUUID = messageIn.readUTF();
                boolean characterSlim = messageIn.readBoolean();

                playerData = PlayerData.get("template");

                playerData.createNewCharacter(characterID, characterName, characterSkin, skinUUID, characterSlim);

                break;

            case "getAllCharacters":
                requesterUUID = messageIn.readUTF();
                String filter = messageIn.readUTF();
                try {
                    List<String> data = PlayerData.getAllCharacters(filter);
                    PaperMessagingUtil.sendByteArray(serverInfo, "getAllCharacters", requesterUUID, data);
                } catch (IOException e) {
                    break;
                }
                break;
            default:
                SneakyCharacterManager.getInstance().getLogger().severe("SneakyCharacterManager received a packet but the subchannel was unknown: " + subChannel);
                break;
        }
    }

	/*
	public static List<String> readStringList(ByteArrayDataInput in) {
		int size = in.readInt();

		List<String> strings = new ArrayList<>();
		while (strings.size() < size) {
			strings.add(in.readUTF());
		}

		return strings;
	}
	*/

}