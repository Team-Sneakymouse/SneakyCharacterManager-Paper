package net.sneakycharactermanager.bungee.listeners;

import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.sneakycharactermanager.proxy.core.ProxyCore;

public class ConnectionEventListeners implements Listener {

    private final ProxyCore core;

    public ConnectionEventListeners(ProxyCore core) {
        this.core = core;
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        core.onPlayerDisconnect(event.getPlayer().getUniqueId());
    }
    
}
