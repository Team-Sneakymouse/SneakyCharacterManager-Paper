package net.sneakycharactermanager.paper.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import net.md_5.bungee.event.EventHandler;
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
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        if (player == null) return;

        Character character = Character.get(player);
        
        if (character == null) return;
        
        SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(player, character.getName());
    }

}
