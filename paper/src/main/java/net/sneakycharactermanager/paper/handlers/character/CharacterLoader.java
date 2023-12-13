package net.sneakycharactermanager.paper.handlers.character;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.skins.SkinCache;
import net.sneakycharactermanager.paper.handlers.skins.SkinData;
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.util.ChatUtility;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerTextures;

public class CharacterLoader {

    public static void loadCharacter(Character character){
        String url = character.getSkin();

        String playerUUID = character.getPlayer().getUniqueId().toString();
        
        ProfileProperty p = SkinCache.get(playerUUID, url);

        if (p == null) {
            //No Invalid URL for skin loading
            if(url == null || url.isEmpty() || !url.startsWith("http")) {
                SneakyCharacterManager.getInstance().getLogger().warning("Invalid Skin URL Received? Was this our fault?");
                if(character.getCharacterName() == null || character.getCharacterName().isEmpty()) return;
                //On players first join they have no Skin url but we still want to load a nickname plate so we can modify it
                SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(character.getPlayer(), character.getCharacterName());
                return;
            }

            //This system may need to change at some point
            PlayerProfile playerProfile = character.getPlayer().getPlayerProfile();
            boolean isSlimSkin = playerProfile.getTextures().getSkinModel().equals(PlayerTextures.SkinModel.SLIM);
            //Might need a setting for Is-Slim Skin? For now, defaulting to the characters base model type

            SkinData data = new SkinData(url, isSlimSkin);
            SkinQueue.add(data, 1);
            SkinQueue.start();

            Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (s) ->{
                while (true) {
                    if (data.isProcessed()) {
                        if (data.isValid()) {
                            Bukkit.getScheduler().runTaskLater(SneakyCharacterManager.getInstance(), () ->{
                                playerProfile.removeProperty("textures");
                                ProfileProperty property = data.getTextureProperty();
                                if(property == null){
                                    character.getPlayer().sendMessage(ChatUtility.convertToComponent("&4Failed to load skin! Something went wrong!"));
                                }else{
                                    SkinCache.put(playerUUID, url, property);
                                    playerProfile.setProperty(property);
                                    character.getPlayer().setPlayerProfile(playerProfile);
                                    SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(character.getPlayer(), character.getCharacterName());
                                }
                            }, 0);
                        }
                        break;
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        } else {
            PlayerProfile playerProfile = character.getPlayer().getPlayerProfile();
            playerProfile.removeProperty("textures");
            playerProfile.setProperty(p);
            character.getPlayer().setPlayerProfile(playerProfile);
            SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(character.getPlayer(), character.getCharacterName());
        }

        Bukkit.getServer().getPluginManager().callEvent(new LoadCharacterEvent(character.getPlayer(), character.isFirstLoad(), character.getCharacterUUID(), character.getCharacterName(), url));
        character.setFirstLoad(false);
    }

    public static void updateSkin(Player player, String url){
        //This system may need to change at some point
        PlayerProfile playerProfile = player.getPlayerProfile();
        boolean isSlimSkin = playerProfile.getTextures().getSkinModel().equals(PlayerTextures.SkinModel.SLIM);

        // String nickname = SneakyCharacterManager.getInstance().nametagManager.getNickname(player);
        // if(nickname.equals(player.getName())) return;

        SkinData data = new SkinData(url, isSlimSkin);
        SkinQueue.add(data, 1);
        SkinQueue.start();

        Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (s) -> {
            while (true) {
                if (data.isProcessed()) {
                    if (data.isValid()) {
                        Bukkit.getScheduler().runTaskLater(SneakyCharacterManager.getInstance(), () ->{
                            playerProfile.removeProperty("textures");
                            ProfileProperty property = data.getTextureProperty();
                            if(property == null){
                                player.sendMessage(ChatUtility.convertToComponent("&4Failed to load skin! Something went wrong!"));
                            }else{
                                SkinCache.put(player.getUniqueId().toString(), url, property);
                                playerProfile.setProperty(property);
                                player.setPlayerProfile(playerProfile);
                                //SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(player, nickname);
                                BungeeMessagingUtil.sendByteArray("updateCharacter", player.getUniqueId().toString(), 1, url);
                            }
                        }, 0);
                    }
                    break;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
