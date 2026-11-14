package net.sneakycharactermanager.paper.api.character;

import net.sneakycharactermanager.paper.SneakyCharacterManager;

/**
 * Where SCM keeps character identity: the legacy proxy YAML ({@link #LEGACY}) or SneakyDB ({@link #SNEAKYDB}).
 * Chosen once per enable from {@code storage.backend}; changing it needs a restart. Gameplay state (inventory,
 * location, attributes) is a Paper-local file in both modes.
 */
public enum StorageMode {
    LEGACY, SNEAKYDB;

    /** The mode SCM is running in. Call after SCM has enabled. */
    public static StorageMode current() {
        return SneakyCharacterManager.getInstance().getStorageMode();
    }

    /** Parses {@code storage.backend}. Missing configuration keeps the proxy protocol. */
    public static StorageMode parse(String value) {
        if (value == null || value.equals("legacy")) return LEGACY;
        if (value.equals("sneakydb")) return SNEAKYDB;
        throw new IllegalArgumentException("storage.backend must be legacy or sneakydb (restart required)");
    }
}
