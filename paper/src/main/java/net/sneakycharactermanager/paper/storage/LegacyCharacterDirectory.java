package net.sneakycharactermanager.paper.storage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import net.sneakycharactermanager.paper.api.directory.CharacterDirectory;
import net.sneakycharactermanager.paper.api.directory.CharacterInfo;
import net.sneakycharactermanager.paper.util.ProxyListReplies;

/** Legacy backend: asks the proxy, which reads its account YAML files. */
public final class LegacyCharacterDirectory implements CharacterDirectory {
    private final Logger logger;

    public LegacyCharacterDirectory(Logger logger) {
        this.logger = logger;
    }

    @Override public CompletableFuture<List<CharacterInfo>> charactersOf(UUID account) {
        return ProxyListReplies.request("directoryOwner", account.toString())
                .thenApply(reply -> DirectoryResults.enabled(DirectoryResults.fromWire(reply)));
    }

    @Override public CompletableFuture<Optional<CharacterInfo>> character(UUID characterId) {
        return ProxyListReplies.request("directoryCharacter", characterId.toString())
                .thenApply(reply -> DirectoryResults.single(characterId, DirectoryResults.fromWire(reply), logger));
    }
}
