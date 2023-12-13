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
        String playerUUID = character.getPlayer().getUniqueId().toString();
    
        ProfileProperty profileProperty = SkinCache.get(playerUUID, url);
    
        if (profileProperty == null) {
            SkinUtil.handleNewSkin(character);
        } else {
            SkinUtil.handleCachedSkin(character, profileProperty);
        }
    
        Bukkit.getServer().getPluginManager().callEvent(new LoadCharacterEvent(
                character.getPlayer(),
                character.isFirstLoad(),
                character.getCharacterUUID(),
                character.getCharacterName(),
                url,
                character.isSlim()
        ));
    
        character.setFirstLoad(false);
    }

    public static void updateSkin(Player player, String url) {
        boolean isSlimSkin = checkIsSlimSkin(url);
    
        SkinData data = new SkinData(url, isSlimSkin);
        SneakyCharacterManager.getInstance().skinQueue.add(data, 1);
    
        Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (s) -> {
            if (SkinUtil.waitForSkinProcessing(data, Character.get(player))) {
                BungeeMessagingUtil.sendByteArray("updateCharacter", player.getUniqueId().toString(), 1, url, data.isSlim());
            }
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
