package net.sneakycharactermanager.paper.listeners;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ConnectionEventListeners implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        //Loading the nicknames for newly connected player
        SneakyCharacterManager.getInstance().nametagManager.loadNames(event.getPlayer());

    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event){
        //Un-Nick player who is disconnecting from the server
        SneakyCharacterManager.getInstance().nametagManager.unnicknamePlayer(event.getPlayer());
    }

}
