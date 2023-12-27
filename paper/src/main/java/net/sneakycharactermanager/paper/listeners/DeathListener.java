package net.sneakycharactermanager.paper.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;;

public class DeathListener implements Listener {
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();

        if (player == null) return;

        SneakyCharacterManager.getInstance().nametagManager.unnicknamePlayer(player);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();

        if (player == null) return;

        Character character = Character.get(player);
        
        if (character == null) return;
        
        SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(player, character.getName());
    }

}
