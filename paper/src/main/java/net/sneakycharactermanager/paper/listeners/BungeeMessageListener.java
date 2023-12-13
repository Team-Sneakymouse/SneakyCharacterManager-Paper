package net.sneakycharactermanager.paper.listeners;

import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.NotNull;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.commands.CommandChar;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.util.ChatUtility;

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
                boolean slim = in.readBoolean();
                
                Character character = new Character(playerUUID, characterUUID, characterName, skin, slim);
                character.load();
                break;
            case "rebuildCharacterMap" :
                playerUUID = in.readUTF();
                characterUUID = in.readUTF();
                characterName = in.readUTF();
                skin = in.readUTF();
                slim = in.readBoolean();
                
                character = new Character(playerUUID, characterUUID, characterName, skin, slim);
                character.map();
                break;
            case "selectCharacterByNameFailed" :
                playerUUID = in.readUTF();
                Player pl = Bukkit.getPlayer(UUID.fromString(playerUUID));
                if(pl == null) return;

                pl.sendMessage(ChatUtility.convertToComponent("&aNo character found. Loading character menu..."));
                SneakyCharacterManager.getInstance().selectionMenu.openMenu(pl);
                break;
            case "characterSelectionGUI" :
                playerUUID = in.readUTF();
                pl = Bukkit.getPlayer(UUID.fromString(playerUUID));
                if(pl == null) return;
                List<CharacterSnapshot> characterSnapshots = readCharacterList(pl, in);
                SneakyCharacterManager.getInstance().selectionMenu.updateInventory(pl.getUniqueId().toString(), characterSnapshots);
                break;
            case "updateCharacterList" :
                playerUUID = in.readUTF();
                pl = Bukkit.getPlayer(UUID.fromString(playerUUID));
                CommandChar.tabCompleteMap.put(pl, readStringList(in));
                break;
            case "defaultSkin" :
                playerUUID = in.readUTF();
                characterUUID = in.readUTF();
                pl = Bukkit.getPlayer(UUID.fromString(playerUUID));
                if(pl == null) return;
                PlayerProfile profile = pl.getPlayerProfile();
                PlayerTextures textures = profile.getTextures();
                if(textures.getSkin() == null) return;
                String skinURL = textures.getSkin().toString();
                slim = textures.getSkinModel().equals(PlayerTextures.SkinModel.SLIM);

                BungeeMessagingUtil.sendByteArray("defaultSkin", playerUUID, characterUUID, skinURL, slim);
                break;
            case "deleteConfirmed" :
                playerUUID = in.readUTF();
                pl = Bukkit.getPlayer(UUID.fromString(playerUUID));
                pl.sendMessage(ChatUtility.convertToComponent("&aThe following character has been deleted: `" + in.readUTF() + "` (" + in.readUTF() + ")"));
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

    // This function is used to deserialize a character list from a ByteArrayDataInput, in order to build the character selection GUI
    public static List<CharacterSnapshot> readCharacterList(Player player, ByteArrayDataInput in) {
        int size = in.readInt();

        List<CharacterSnapshot> characters = new ArrayList<>();
        while (characters.size() < size) {
            characters.add(readCharacter(player, in));
        }

        return characters;
    }

    private static CharacterSnapshot readCharacter(Player player, ByteArrayDataInput in) {
        String uuid = in.readUTF();
        String name = in.readUTF();
        String skin = in.readUTF();
        boolean slim = in.readBoolean();

        return new CharacterSnapshot(player, uuid, name, skin, slim);
    }

    // This subclass is a heavily simplified version of the bungee side Character class. This allows us to deserialize a ByteArrayDataInput into the List<Character> that we need to build the GUI, without actually accessing the bungee-side Character class.
    public static class CharacterSnapshot {
        private Player player;
        private String uuid;
        private String name;
        private String skin;
        private boolean slim;
        private ItemStack headItem;

        public CharacterSnapshot(Player player, String uuid, String name, String skin, boolean slim) {
            this.player = player;
            this.uuid = uuid;
            this.name = name;
            this.skin = skin;
            this.slim = slim;
            this.headItem = new ItemStack(Material.PLAYER_HEAD);
        }

        public Player getPlayer() {
            return player;
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

        public boolean isSlim(){
            return slim;
        }

        public ItemStack getHeadItem() {
            return headItem;
        }
    }

}