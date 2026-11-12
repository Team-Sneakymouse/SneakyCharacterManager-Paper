package net.sneakycharactermanager.paper.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import net.sneakycharactermanager.paper.api.directory.CharacterDirectory;
import net.sneakycharactermanager.paper.api.directory.CharacterInfo;

/** SneakyDB backend: reads {@code scm.characters} directly, outside the per-owner write queue. */
final class SqlCharacterDirectory implements CharacterDirectory {
    private final SqlStore store;
    private final Logger logger;

    SqlCharacterDirectory(SqlStore store, Logger logger) {
        this.store = store;
        this.logger = logger;
    }

    @Override public CompletableFuture<List<CharacterInfo>> charactersOf(UUID account) {
        return store.ready().thenCompose(unused -> store.directoryOfOwner(account.toString()))
                .thenApply(rows -> DirectoryResults.enabled(infos(rows)));
    }

    @Override public CompletableFuture<Optional<CharacterInfo>> character(UUID characterId) {
        return store.ready().thenCompose(unused -> store.directoryOfCharacter(characterId.toString()))
                .thenApply(rows -> DirectoryResults.single(characterId, infos(rows), logger));
    }

    private static List<CharacterInfo> infos(List<Map<String,Object>> rows) {
        List<CharacterInfo> characters = new ArrayList<>();
        for (Map<String,Object> row : rows)
            DirectoryResults.info(SqlStore.text(row, "character_id"), SqlStore.text(row, "owner_id"),
                    SqlStore.text(row, "name"), Boolean.TRUE.equals(row.get("enabled"))).ifPresent(characters::add);
        return characters;
    }
}
