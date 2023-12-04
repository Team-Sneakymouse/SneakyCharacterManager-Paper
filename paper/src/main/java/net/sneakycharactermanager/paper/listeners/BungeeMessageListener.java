package net.sneakycharactermanager.paper.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.Character;

public class BungeeMessageListener implements PluginMessageListener
{
    
    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (!channel.equalsIgnoreCase("sneakymouse:sneakycharactermanager"))
        {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subChannel = in.readUTF();
        
        switch (subChannel) {
            case "loadCharacter" :
                String playerUUID = in.readUTF();
                String characterUUID = in.readUTF();
                String characterName = in.readUTF();
                String skin = in.readUTF();
                
                Character character = new Character(playerUUID, characterUUID, characterName, skin);
                character.load();
                break;
            case "characterSelectionGUI" :
                playerUUID = in.readUTF();
                Player pl = Bukkit.getPlayer(UUID.fromString(playerUUID));
                List<CharacterSnapshot> characterSnapshots = receiveCharacterList(in);
                // TODO: Open GUI
                break;
            default:
                SneakyCharacterManager.getInstance().getLogger().severe("SneakyCharacterManager received a packet but the subchannel was unknown: " + subChannel);
                break;
        }
    }

    // This function is used to deserialize a character list from a ByteArrayDataInput, in order to build the character selection GUI
    public static List<CharacterSnapshot> receiveCharacterList(ByteArrayDataInput in) {
        int size = in.readInt();

        List<CharacterSnapshot> characters = new ArrayList<>();
        while (characters.size() < size) {
            characters.add(readCharacter(in));
        }

        return characters;
    }

    private static CharacterSnapshot readCharacter(ByteArrayDataInput in) {
        String uuid = in.readUTF();
        String name = in.readUTF();
        String skin = in.readUTF();

        return new CharacterSnapshot(uuid, name, skin);
    }

    // This subclass is a heavily simplified version of the bungee side Character class. This allows us to deserialize a ByteArrayDataInput into the List<Character> that we need to build the GUI, without actually accessing the bungee-side Character class.
    public static class CharacterSnapshot {
        private String uuid;
        private String name;
        private String skin;

        public CharacterSnapshot(String uuid, String name, String skin) {
            this.uuid = uuid;
            this.name = name;
            this.skin = skin;
        }
    
        public String getUUID() {
            return uuid;
        }
    
        public String getName() {
            return name;
        }
    
        public String getSkin() {
            return skin;
        }
    }

}