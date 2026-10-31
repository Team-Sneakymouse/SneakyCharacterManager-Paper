package net.sneakycharactermanager.paper.storage;

import com.sneakydb.SneakyDBProvider;
import net.sneakycharactermanager.paper.SneakyCharacterManager;

/** Loaded only after explicit opt-in; legacy enable never resolves SneakyDB API types. */
public final class SneakyDbBootstrap {
    private SneakyDbBootstrap() {}
    public static CharacterBackend open(SneakyCharacterManager plugin, int timeoutSeconds) {
        if (plugin.getServer().getPluginManager().getPlugin("SneakyDB") == null)
            throw new IllegalStateException("storage.backend=sneakydb requires the SneakyDB plugin; refusing legacy fallback");
        return new DatabaseBackend(plugin, new SqlStore(SneakyDBProvider.getApi(), timeoutSeconds));
    }
}
