package net.sneakycharactermanager.bungee;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.sneakycharactermanager.proxy.common.ProxyLogger;
import net.sneakycharactermanager.proxy.common.ProxyPlatform;

import java.io.File;
import java.util.UUID;

public final class BungeePlatform implements ProxyPlatform {

    private final SneakyCharacterManager plugin;
    private final ProxyLogger logger;

    public BungeePlatform(SneakyCharacterManager plugin) {
        this.plugin = plugin;
        this.logger = new BungeeLogger(plugin.getLogger());
    }

    @Override
    public File dataFolder() {
        return plugin.getDataFolder();
    }

    @Override
    public ProxyLogger logger() {
        return logger;
    }

    @Override
    public String playerName(UUID playerUniqueId) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(playerUniqueId);
        return player == null ? null : player.getName();
    }
}

