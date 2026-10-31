package net.sneakycharactermanager.paper.storage;

/** Chosen once per enable. Missing configuration must retain the proxy protocol. */
public enum StorageMode {
    LEGACY, SNEAKYDB;

    public static StorageMode parse(String value) {
        if (value == null || value.equals("legacy")) return LEGACY;
        if (value.equals("sneakydb")) return SNEAKYDB;
        throw new IllegalArgumentException("storage.backend must be legacy or sneakydb (restart required)");
    }
}
