package net.sneakycharactermanager.paper.handlers.character;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest;
import java.net.http.HttpClient;

import javax.imageio.ImageIO;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerTextures;

import com.destroystokyo.paper.profile.PlayerProfile;
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
                player,
                character.isFirstLoad(),
                character.getCharacterUUID(),
                character.getName(),
                url,
                character.isSlim()
        ));
    
        character.setFirstLoad(false);

        if (profileProperty == null) {
            if (!shouldSkipLoading(character)) {
                SkinData.getOrCreate(url, character.isSlim(), 2, player);
            }
        } else {
            player.setPlayerProfile(SkinUtil.handleCachedSkin(player, profileProperty));
        }

        SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(player, character.getName());
    }

    
    private static boolean shouldSkipLoading(Character character) {
        String url = character.getSkin();
        
        if (url == null || url.isEmpty() || !url.startsWith("http")) {
            if (url != null && !url.isEmpty()) {
                SneakyCharacterManager.getInstance().getLogger().warning("Invalid Skin URL Received? Was this our fault?");
            }
    
            if (character.getName() == null || character.getName().isEmpty()) {
                return true;
            }
    
            return true;
        }
        return false;
    }

    public static void updateSkin(Player player, String url, Boolean slim) {
        PlayerProfile playerProfile = player.getPlayerProfile();
        boolean def = playerProfile.getTextures().getSkinModel().equals(PlayerTextures.SkinModel.SLIM);

        boolean isSlimSkin = slim == null ? checkIsSlimSkin(url, def) : slim;
    
        SkinData.getOrCreate(url, isSlimSkin, 2, player);

        Character character = Character.get(player);
        character.setSkin(url);
        character.setSlim(isSlimSkin);
        BungeeMessagingUtil.sendByteArray(player, "updateCharacter", player.getUniqueId().toString(), 1, url, isSlimSkin);
    }
    
    private static boolean checkIsSlimSkin(String url, boolean def) {
        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(new URI(url)).build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            // Check the HTTP response status
            int statusCode = response.statusCode();

            if (statusCode == 200) {
                try (InputStream inputStream = response.body()) {
                    if (inputStream == null) return def;

                    BufferedImage image = ImageIO.read(inputStream);

                    if (image == null) return def;

                    int pixel = image.getRGB(55, 20);
                    int alpha = (pixel >> 24) & 0xFF;
                    return alpha == 0;
                }
            }
        } catch (IOException | InterruptedException | URISyntaxException e) {
            e.printStackTrace();
        }

        return def;
    }

}
