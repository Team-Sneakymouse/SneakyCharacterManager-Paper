package net.sneakycharactermanager.paper.util;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.SkullMeta;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.skins.SkinCache;
import net.sneakycharactermanager.paper.handlers.skins.SkinData;
import net.sneakycharactermanager.paper.listeners.BungeeMessageListener;
import net.sneakycharactermanager.paper.handlers.character.Character;;

public class SkinUtil {

    public static void handleNewSkin(Character character) {
        if (shouldSkipLoading(character)) {
            return;
        }
    
        SkinData data = new SkinData(character.getSkin(), character.isSlim());
    
        SneakyCharacterManager.getInstance().skinQueue.add(data, 1);
    
        Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (s) -> {
            waitForSkinProcessing(data, character);
        });
    }
    
    private static boolean shouldSkipLoading(Character character) {
        String url = character.getSkin();
        
        if (url == null || url.isEmpty() || !url.startsWith("http")) {
            if (url != null && !url.isEmpty()) {
                SneakyCharacterManager.getInstance().getLogger().warning("Invalid Skin URL Received? Was this our fault?");
            }
    
            if (character.getCharacterName() == null || character.getCharacterName().isEmpty()) {
                return true;
            }
    
            SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(character.getPlayer(), character.getCharacterName());
            return true;
        }
        return false;
    }
    
    public static boolean waitForSkinProcessing(SkinData data, Character character) {
        while (true) {
            if (data.isProcessed()) {
                if (data.isValid()) {
                    Bukkit.getScheduler().runTaskLater(SneakyCharacterManager.getInstance(), () -> {
                        ProfileProperty property = data.getTextureProperty();

                        if (property == null) return;

                        handleCachedSkin(character, property);
                        SkinCache.put(character.getPlayer().getUniqueId().toString(), character.getSkin(), property);
                    }, 0);
                    return true;
                }
                return false;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean waitForSkinProcessing(SkinData data, BungeeMessageListener.CharacterSnapshot character, Inventory inventory, int index) {
        while (true) {
            if (data.isProcessed()) {
                if (data.isValid()) {
                    Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
                        ProfileProperty property = data.getTextureProperty();

                        if (property == null) return;

                        handleCachedSkin(character, property, inventory, index);
                        SkinCache.put(character.getPlayer().getUniqueId().toString(), character.getSkin(), property);
                    });
                }
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    public static void handleCachedSkin(Character character, ProfileProperty profileProperty) {
        Player player = character.getPlayer();
        PlayerProfile playerProfile = applyProcessedSkin(player, profileProperty);

        if (playerProfile == null) return;

        player.setPlayerProfile(playerProfile);
        
        SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(player, character.getCharacterName());
    }

    public static void handleCachedSkin(BungeeMessageListener.CharacterSnapshot character, ProfileProperty profileProperty, Inventory inventory, int index) {
        Player player = character.getPlayer();
        PlayerProfile playerProfile = applyProcessedSkin(player, profileProperty);

        if (playerProfile == null) return;

        SkullMeta skullMeta = (SkullMeta) character.getHeadItem().getItemMeta();
        skullMeta.setPlayerProfile(playerProfile);
        character.getHeadItem().setItemMeta(skullMeta);

        inventory.setItem(index, character.getHeadItem());
    }
    
    private static PlayerProfile applyProcessedSkin(Player player, ProfileProperty profileProperty) {
        PlayerProfile playerProfile = player.getPlayerProfile();
        playerProfile.removeProperty("textures");
    
        if (profileProperty == null) {
            player.sendMessage(ChatUtility.convertToComponent("&4Failed to load skin! Something went wrong!"));
            return null;
        } else {
            playerProfile.setProperty(profileProperty);
            return playerProfile;
        }
    }

}
