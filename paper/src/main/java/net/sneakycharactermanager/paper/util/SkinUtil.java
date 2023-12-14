package net.sneakycharactermanager.paper.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.skins.SkinCache;
import net.sneakycharactermanager.paper.handlers.skins.SkinData;

public class SkinUtil {
    
    public static void waitForSkinProcessing(SkinData data, Character character) {
        while (true) {
            if (data.isProcessed()) {
                if (data.isValid()) {
                    Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
                        ProfileProperty property = data.getTextureProperty();

                        if (property == null) return;

                        SkinCache.put(character.getPlayer().getUniqueId().toString(), character.getSkin(), property);
                    });
                }
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    public static PlayerProfile handleCachedSkin(Player player, ProfileProperty profileProperty) {
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
