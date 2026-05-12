package net.sneakycharactermanager.proxy.common;

import java.io.File;
import java.util.UUID;

public interface ProxyPlatform {
    File dataFolder();
    ProxyLogger logger();

    /**
     * Used only for naming newly-created characters. Implementations should return null if unknown/offline.
     */
    String playerName(UUID playerUniqueId);
}

