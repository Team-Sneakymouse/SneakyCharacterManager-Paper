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
import net.sneakycharactermanager.paper.util.SkinUtil;

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
                if (pl == null) return;

                pl.sendMessage(ChatUtility.convertToComponent("&aNo character found. Loading character menu..."));
                SneakyCharacterManager.getInstance().selectionMenu.openMenu(pl);
                break;
            case "characterSelectionGUI" :
                playerUUID = in.readUTF();
                pl = Bukkit.getPlayer(UUID.fromString(playerUUID));
                if (pl == null) return;
                List<Character> characters = readCharacterList(pl, in);
                SneakyCharacterManager.getInstance().selectionMenu.updateInventory(pl.getUniqueId().toString(), characters);
                break;
            case "preloadSkins" :
                playerUUID = in.readUTF();
                pl = Bukkit.getPlayer(UUID.fromString(playerUUID));
                if (pl == null) return;
                characters = readCharacterList(pl, in);

                for (Character c : characters) {
                    ProfileProperty p = SkinCache.get(playerUUID, c.getSkin());

                    if (p == null) {
                        SkinData data = SkinData.getOrCreate(c.getSkin(), c.isSlim(), 0);

                        Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (s) -> {
                            SkinUtil.waitForSkinProcessing(data, c);
                        });
                    }
                }
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
                if (pl == null) return;
                PlayerProfile profile = pl.getPlayerProfile();
                PlayerTextures textures = profile.getTextures();
                if (textures.getSkin() == null) return;
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

    public static List<Character> readCharacterList(Player player, ByteArrayDataInput in) {
        int size = in.readInt();

        List<Character> characters = new ArrayList<>();
        while (characters.size() < size) {
            characters.add(readCharacter(player, in));
        }

        return characters;
    }

    private static Character readCharacter(Player player, ByteArrayDataInput in) {
        String uuid = in.readUTF();
        String name = in.readUTF();
        String skin = in.readUTF();
        boolean slim = in.readBoolean();

        return new Character(player.getUniqueId().toString(), uuid, name, skin, slim);
    }

}