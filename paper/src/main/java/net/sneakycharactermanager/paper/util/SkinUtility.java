package net.sneakycharactermanager.paper.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.skins.SkinData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerTextures;

public class SkinUtility {

    public static void applySkin(String url, Player player){
        //This system may need to change at some point
        PlayerProfile playerProfile = player.getPlayerProfile();
        boolean isSlimSkin = playerProfile.getTextures().getSkinModel().equals(PlayerTextures.SkinModel.SLIM);
        //Might need a setting for Is-Slim Skin? For now, defaulting to the characters base model type

        Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (s) ->{
            SkinData data = new SkinData(url, isSlimSkin);
            Bukkit.getScheduler().runTaskLater(SneakyCharacterManager.getInstance(), () ->{
                playerProfile.removeProperty("textures");
                ProfileProperty property = data.getTextureProperty();
                if(property == null){
                    player.sendMessage(ChatUtility.convertToComponent("&4Failed to load skin! Something went wrong!"));
                }else{
                    playerProfile.setProperty(property);
                    player.setPlayerProfile(playerProfile);
                    for(Player target : Bukkit.getOnlinePlayers()){
                        target.hidePlayer(SneakyCharacterManager.getInstance(), player);
                        target.showPlayer(SneakyCharacterManager.getInstance(), player);
                    }
                }
            }, 0);
        });

    }

}