package net.sneakycharactermanager.paper.listeners;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.Character;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ConnectionEventListeners implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {

        //Loading the nicknames for newly connected player
        //TODO: This will be moved to the messagelistener where it awaits the correct nickname given by the bungee plugin 
        SneakyCharacterManager.getInstance().nametagManager.loadNames(event.getPlayer());

        // TODO: Fine-tune this delay. 1 tick might already be fine
        BungeeMessagingUtil.sendByteArrayDelayed(5, "playerJoin", event.getPlayer().getUniqueId().toString());
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event){
        //Un-Nick player who is disconnecting from the server
        SneakyCharacterManager.getInstance().nametagManager.unnicknamePlayer(event.getPlayer());

        Character.get(event.getPlayer()).save();
        Character.remove(event.getPlayer());
    }

}
