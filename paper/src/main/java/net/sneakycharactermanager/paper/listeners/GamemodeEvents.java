package net.sneakycharactermanager.paper.listeners;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.nametags.NametagManager;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

public class GamemodeEvents implements Listener {

    @EventHandler
    public void onGamemodeChange(PlayerGameModeChangeEvent event){
        Player player = event.getPlayer();
        NametagManager manager = SneakyCharacterManager.getInstance().nametagManager;

        if(event.getNewGameMode().equals(GameMode.SPECTATOR)){
            manager.unnicknamePlayer(player);
        }
        else{
            // Fenced: re-reads the character later and is dropped if the plate was rebuilt or removed meanwhile.
            manager.rebuild(player);
        }
    }

}
