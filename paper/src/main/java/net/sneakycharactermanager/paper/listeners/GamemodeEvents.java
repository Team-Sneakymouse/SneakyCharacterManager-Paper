package net.sneakycharactermanager.paper.listeners;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

public class GamemodeEvents implements Listener {

    @EventHandler
    public void onGamemodeChange(PlayerGameModeChangeEvent event){
        Player player = event.getPlayer();

        if(event.getNewGameMode().equals(GameMode.SPECTATOR)){
            SneakyCharacterManager.getInstance().nametagManager.unnicknamePlayer(player);
        }
        else{
            SneakyCharacterManager.getInstance().nametagManager.unnicknamePlayer(player);
            Character character = Character.get(player);
            if(character == null) return;
            SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(player, character.getName());
        }
    }

}
