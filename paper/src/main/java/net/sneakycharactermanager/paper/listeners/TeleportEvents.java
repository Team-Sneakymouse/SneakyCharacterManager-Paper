package net.sneakycharactermanager.paper.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;

public class TeleportEvents implements Listener {
    
    @EventHandler
    public void onGamemodeChange(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        SneakyCharacterManager.getInstance().nametagManager.unnicknamePlayer(player);

        Character character = Character.get(player);

        if(character == null) return;

        Bukkit.getScheduler().runTaskLater(SneakyCharacterManager.getInstance(), ()->{
            SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(player, character.getName());
        }, 2);
    }

}
