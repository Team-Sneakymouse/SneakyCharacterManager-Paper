package net.sneakycharactermanager.paper.storage;

import com.sneakydb.SneakyDBApi;
import com.sneakydb.SneakyDBProvider;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import java.util.concurrent.CompletableFuture;

/** Loaded only after explicit opt-in; legacy enable never resolves SneakyDB API types. */
public final class SneakyDbBootstrap {
    private SneakyDbBootstrap() {}
    public static CharacterBackend open(SneakyCharacterManager plugin, int timeoutSeconds) {
        if (plugin.getServer().getPluginManager().getPlugin("SneakyDB") == null)
            throw new IllegalStateException("storage.backend=sneakydb requires the SneakyDB plugin; refusing legacy fallback");
        SneakyDBApi api = SneakyDBProvider.getApi();
        CompletableFuture<Integer> migrated;
        try {
            // Registers this jar's db/migrations; SneakyDB applies them after every plugin has enabled.
            migrated = api.migrate(SneakyCharacterManager.IDENTIFIER, SneakyCharacterManager.class);
        } catch (RuntimeException error) {
            migrated = CompletableFuture.failedFuture(error);
        }
        return new DatabaseBackend(plugin, new SqlStore(api, timeoutSeconds, migrated));
    }
}
