package net.sneakycharactermanager.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import net.sneakycharactermanager.proxy.common.ProxyLogger;
import net.sneakycharactermanager.proxy.common.ProxyPlatform;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

public final class VelocityPlatform implements ProxyPlatform {

    private final ProxyServer proxy;
    private final Path dataDirectory;
    private final ProxyLogger logger;

    public VelocityPlatform(ProxyServer proxy, Path dataDirectory, ProxyLogger logger) {
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    @Override
    public File dataFolder() {
        return dataDirectory.toFile();
    }

    @Override
    public ProxyLogger logger() {
        return logger;
    }

    @Override
    public String playerName(UUID playerUniqueId) {
        return proxy.getPlayer(playerUniqueId).map(Player::getUsername).orElse(null);
    }
}

