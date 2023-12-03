package net.sneakycharactermanager.bungee;

import java.net.SocketAddress;
import java.util.List;
import java.util.ArrayList;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Plugin;
import net.sneakycharactermanager.bungee.listeners.ConnectionEventListeners;
import net.sneakycharactermanager.bungee.listeners.PluginMessageListener;
import net.sneakycharactermanager.bungee.util.PaperMessagingUtil;

public class SneakyCharacterManager extends Plugin {

    private static SneakyCharacterManager instance;
    private static final List<SocketAddress> connectedServers = new ArrayList<SocketAddress>();

    @Override
    public void onEnable() {
        instance = this;

        getProxy().registerChannel("sneakymouse:sneakycharactermanager");
        getProxy().getPluginManager().registerListener(this, new PluginMessageListener());
        
        getProxy().getPluginManager().registerListener(this, new ConnectionEventListeners());

        for (ServerInfo serverInfo : getProxy().getServers().values()) {
            PaperMessagingUtil.sendByteArray(serverInfo, "checkConnection");
        }
    }

    public static SneakyCharacterManager getInstance() {
        return instance;
    }

    public static void addConnectedServer(ServerInfo server) {
        if (connectedServers.contains(server.getSocketAddress())) {
            SneakyCharacterManager.getInstance().getLogger().warning("The following backend server tried to connect to SneakyCharacterManager but was already listed: [" + server.getName() + ", " + server.getSocketAddress().toString() + "]");
        } else {
            connectedServers.add(server.getSocketAddress());
            SneakyCharacterManager.getInstance().getLogger().info("The following backend server has connected to SneakyCharacterManager: [" + server.getName() + ", " + server.getSocketAddress().toString() + "]");
        }
    }

    public static void removeConnectedServer(ServerInfo server) {
        if (connectedServers.contains(server.getSocketAddress())) {
            connectedServers.remove(server.getSocketAddress());
            SneakyCharacterManager.getInstance().getLogger().info("The following backend server has disconnected from SneakyCharacterManager: [" + server.getName() + ", " + server.getSocketAddress().toString() + "]");
        } else {
            SneakyCharacterManager.getInstance().getLogger().warning("The following backend server tried to disconnect from SneakyCharacterManager but was not listed: [" + server.getName() + ", " + server.getSocketAddress().toString() + "]");
        }
    }

    public static boolean isServerConnected(ServerInfo server) {
        return (connectedServers.contains(server.getSocketAddress()));
    }

}