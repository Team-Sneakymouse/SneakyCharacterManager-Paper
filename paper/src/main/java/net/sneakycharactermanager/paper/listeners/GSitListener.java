package net.sneakycharactermanager.paper.listeners;

import dev.geco.gsit.api.event.PlayerGetUpPoseEvent;
import dev.geco.gsit.api.event.PlayerPoseEvent;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class GSitListener implements Listener {

    @EventHandler
    public void onPose(PlayerPoseEvent event){
        Player player = event.getPlayer();
        Character character = Character.get(player);
        if(character == null) return;
        SneakyCharacterManager.getInstance().nametagManager.unnicknamePlayer(player);
    }

    @EventHandler
    public void onPose(PlayerGetUpPoseEvent event){
        Player player = event.getPlayer();
        Character character = Character.get(player);
        if(character == null) return;
        SneakyCharacterManager.getInstance().nametagManager.unnicknamePlayer(player);
        SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(player, character.getName());
    }

}
