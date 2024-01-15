package net.sneakycharactermanager.paper.listeners;

import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.NotNull;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.commands.CommandChar;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.skins.SkinCache;
import net.sneakycharactermanager.paper.handlers.skins.SkinData;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.util.ChatUtility;

public class BungeeMessageListener implements PluginMessageListener
{
    
    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (!channel.equalsIgnoreCase("sneakymouse:" + SneakyCharacterManager.IDENTIFIER))
        {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subChannel = in.readUTF();
        
        switch (subChannel) {
            case "loadCharacter" :
                String playerUUID = in.readUTF();
                Character character = readCharacter(playerUUID, in);
                boolean forced = in.readBoolean();

                Player pl = Bukkit.getPlayer(UUID.fromString(playerUUID));

                if (!forced && !Character.canPlayerLoadCharacter(player, character.getCharacterUUID())) {
                    pl.sendMessage(ChatUtility.convertToComponent("&4You cannot access this character right now."));
                    break;
                }
                
                character.load();
                break;
            case "rebuildCharacterMap" :
                playerUUID = in.readUTF();
                character = readCharacter(playerUUID, in);
                forced = in.readBoolean();
                
                character.map();
                break;
            case "selectCharacterByNameFailed" :
                playerUUID = in.readUTF();
                pl = Bukkit.getPlayer(UUID.fromString(playerUUID));
                if (pl == null) return;

                pl.sendMessage(ChatUtility.convertToComponent("&aNo character found. Loading character menu..."));
                SneakyCharacterManager.getInstance().selectionMenu.openMenu(pl);
                break;
            case "characterSelectionGUI" :
                playerUUID = in.readUTF();
                String requesterUUID = in.readUTF();
                List<Character> characters = readCharacterList(playerUUID, in);


                SneakyCharacterManager.getInstance().selectionMenu.updateInventory(requesterUUID, characters);
                break;
            case "preloadSkins" :
                playerUUID = in.readUTF();
                pl = Bukkit.getPlayer(UUID.fromString(playerUUID));
                if (pl == null) return;
                requesterUUID = in.readUTF(); // This is only read to clear it from the Input.
                characters = readCharacterList(requesterUUID, in);

                for (Character c : characters) {
                    ProfileProperty p = SkinCache.get(playerUUID, c.getSkin());

                    if (p == null) {
                        SkinData.getOrCreate(c.getSkin(), c.isSlim(), 0, pl);
                    }
                }
                break;
            case "updateCharacterList" :
                playerUUID = in.readUTF();
                pl = Bukkit.getPlayer(UUID.fromString(playerUUID));
                assert pl != null;
                CommandChar.tabCompleteMap.put(pl.getUniqueId().toString(), readStringList(in));
                break;
            case "defaultSkin" :
                playerUUID = in.readUTF();
                String characterUUID = in.readUTF();
                pl = Bukkit.getPlayer(UUID.fromString(playerUUID));
                if (pl == null) return;
                PlayerProfile profile = pl.getPlayerProfile();
                PlayerTextures textures = profile.getTextures();
                if (textures.getSkin() == null) return;
                String skinURL = textures.getSkin().toString();
                boolean slim = textures.getSkinModel().equals(PlayerTextures.SkinModel.SLIM);

                BungeeMessagingUtil.sendByteArray(player, "defaultSkin", playerUUID, characterUUID, skinURL, slim);
                break;
            case "deleteConfirmed" :
                playerUUID = in.readUTF();
                pl = Bukkit.getPlayer(UUID.fromString(playerUUID));
                pl.sendMessage(ChatUtility.convertToComponent("&aThe following character has been deleted: &b`" + in.readUTF() + "`&a (" + in.readUTF() + ")"));
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

    public static List<Character> readCharacterList(String uuid, ByteArrayDataInput in) {
        int size = in.readInt();

        List<Character> characters = new ArrayList<>();
        while (characters.size() < size) {
            Character character = readCharacter(uuid, in);
            characters.add(character);
        }

        return characters;
    }

    private static Character readCharacter(String playerUUID, ByteArrayDataInput in) {
        String uuid = in.readUTF();
        String name = in.readUTF();
        String skin = in.readUTF();
        boolean slim = in.readBoolean();
        List<String> tags = readStringList(in);
        return new Character(playerUUID, uuid, name, skin, slim, tags);
    }

}