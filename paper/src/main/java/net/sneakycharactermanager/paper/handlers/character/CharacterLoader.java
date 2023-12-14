package net.sneakycharactermanager.paper.handlers.character;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.imageio.ImageIO;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.destroystokyo.paper.profile.ProfileProperty;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.skins.SkinCache;
import net.sneakycharactermanager.paper.handlers.skins.SkinData;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.util.SkinUtil;

public class CharacterLoader {

    public static void loadCharacter(Character character) {
        String url = character.getSkin();
        Player player = character.getPlayer();
    
        ProfileProperty profileProperty = SkinCache.get(player.getUniqueId().toString(), url);

        Bukkit.getServer().getPluginManager().callEvent(new LoadCharacterEvent(
                character.getPlayer(),
                character.isFirstLoad(),
                character.getCharacterUUID(),
                character.getCharacterName(),
                url,
                character.isSlim()
        ));
    
        character.setFirstLoad(false);

        if (profileProperty == null) {
            if (shouldSkipLoading(character)) return;
            
            SkinData data = new SkinData(character.getSkin(), character.isSlim());
            SneakyCharacterManager.getInstance().skinQueue.add(data, 1);

            Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (s) -> {
                SkinUtil.waitForSkinProcessing(data, character);
                Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
                    ProfileProperty p = SkinCache.get(player.getUniqueId().toString(), character.getSkin());
                    if (p != null) {
                        player.setPlayerProfile(SkinUtil.handleCachedSkin(player, p));
                    }
                });
            });
        } else {
            player.setPlayerProfile(SkinUtil.handleCachedSkin(player, profileProperty));
        }

        SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(player, character.getCharacterName());
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

    public static void updateSkin(Player player, String url) {
        boolean isSlimSkin = checkIsSlimSkin(url);
    
        SkinData data = new SkinData(url, isSlimSkin);
        SneakyCharacterManager.getInstance().skinQueue.add(data, 1);

        Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (s) -> {
            SkinUtil.waitForSkinProcessing(data, Character.get(player));
            Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
                ProfileProperty p = SkinCache.get(player.getUniqueId().toString(), url);
                if (p != null) {
                    player.setPlayerProfile(SkinUtil.handleCachedSkin(player, p));
                    BungeeMessagingUtil.sendByteArray("updateCharacter", player.getUniqueId().toString(), 1, url, data.isSlim());
                }
            });
        });
    }
    
    private static boolean checkIsSlimSkin(String url) {
        try (InputStream inputStream = new URL(url).openStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            int pixel = image.getRGB(55, 20);
    
            int alpha = (pixel >> 24) & 0xFF;
            return alpha == 0;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
