package net.sneakycharactermanager.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.sneakycharactermanager.proxy.common.ProxyConstants;
import net.sneakycharactermanager.proxy.common.ProxyServerConnection;
import net.sneakycharactermanager.proxy.core.ProxyCore;
import net.sneakycharactermanager.proxy.core.ProxyKeys;

import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.UUID;

@Plugin(
        id = "sneakycharactermanager",
        name = "SneakyCharacterManager",
        version = "1.0",
        authors = {"Team Sneakymouse"}
)
public final class SneakyCharacterManagerVelocity {

    private final ProxyServer proxy;
    private final Logger slf4jLogger;
    private final Path dataDirectory;

    private final MinecraftChannelIdentifier channelIdentifier;
    private final ProxyCore core;

    @Inject
    public SneakyCharacterManagerVelocity(
            ProxyServer proxy,
            Logger logger,
            @DataDirectory Path dataDirectory
    ) {
        this.proxy = proxy;
        this.slf4jLogger = logger;
        this.dataDirectory = dataDirectory;

        String[] parts = ProxyConstants.CHANNEL.split(":", 2);
        this.channelIdentifier = MinecraftChannelIdentifier.create(parts[0], parts[1]);

        // Velocity derives dataDirectory from the lowercase plugin id ("sneakycharactermanager").
        // Override to "SneakyCharacterManager" so data files are compatible with BungeeCord.
        Path correctedDataDir = dataDirectory.getParent().resolve("SneakyCharacterManager");

        VelocityLogger proxyLogger = new VelocityLogger(logger);
        ProxyKeys keys = ProxyKeys.loadOrGenerate(correctedDataDir.toFile(), proxyLogger);
        this.core = new ProxyCore(new VelocityPlatform(proxy, correctedDataDir, proxyLogger), keys);
    }

    @Subscribe
    public void onInit(com.velocitypowered.api.event.proxy.ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(channelIdentifier);
        slf4jLogger.info("Registered plugin messaging channel {}", ProxyConstants.CHANNEL);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(channelIdentifier)) return;
        if (!(event.getSource() instanceof ServerConnection serverConnection)) return;

        ProxyServerConnection serverConn = new ProxyServerConnection() {
            @Override
            public void sendPluginMessage(byte[] data) {
                serverConnection.sendPluginMessage(channelIdentifier, data);
            }

            @Override
            public String debugName() {
                return serverConnection.getServerInfo().getName();
            }
        };

        core.onPluginMessage(serverConn, event.getData());
        event.setResult(PluginMessageEvent.ForwardResult.handled());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        core.onPlayerDisconnect(uuid);
    }
}

