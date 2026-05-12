package net.sneakycharactermanager.bungee.listeners;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.Connection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.sneakycharactermanager.proxy.common.ProxyConstants;
import net.sneakycharactermanager.proxy.common.ProxyServerConnection;
import net.sneakycharactermanager.proxy.core.ProxyCore;

public class PluginMessageListener implements Listener {

    private final ProxyCore core;

    public PluginMessageListener(ProxyCore core) {
        this.core = core;
    }

    @EventHandler
    public void on(PluginMessageEvent event) {
        if (!event.getTag().equalsIgnoreCase(ProxyConstants.CHANNEL)) return;

        ServerInfo serverInfo = null;
        Connection sender = event.getSender();
        if (sender instanceof ProxiedPlayer proxiedPlayer) {
            serverInfo = proxiedPlayer.getServer().getInfo();
        } else if (sender instanceof Server server) {
            serverInfo = server.getInfo();
        }
        if (serverInfo == null) return;
        final ServerInfo finalServerInfo = serverInfo;

        ProxyServerConnection serverConn = new ProxyServerConnection() {
            @Override
            public void sendPluginMessage(byte[] data) {
                finalServerInfo.sendData(ProxyConstants.CHANNEL, data);
            }

            @Override
            public String debugName() {
                return finalServerInfo.getName();
            }
        };

        core.onPluginMessage(serverConn, event.getData());
    }
}