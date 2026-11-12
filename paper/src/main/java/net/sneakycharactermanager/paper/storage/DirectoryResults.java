package net.sneakycharactermanager.paper.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import net.sneakycharactermanager.paper.api.directory.CharacterInfo;
import net.sneakycharactermanager.paper.util.ChatUtility;

/** Backend-independent rules for {@link net.sneakycharactermanager.paper.api.directory.CharacterDirectory} results. */
final class DirectoryResults {
    private DirectoryResults() {}

    /** Empty when the id or owner is not a UUID (template characters, legacy ids). */
    static Optional<CharacterInfo> info(String id, String owner, String name, boolean enabled) {
        UUID characterId = uuid(id), ownerId = uuid(owner);
        if (characterId == null || ownerId == null) return Optional.empty();
        return Optional.of(new CharacterInfo(characterId, ownerId, ChatUtility.stripFormatting(name), enabled));
    }

    /** Proxy wire format: four strings per character (id, owner, name, enabled). */
    static List<CharacterInfo> fromWire(List<String> flat) {
        if (flat.size() % 4 != 0) throw new IllegalStateException("Malformed directory reply: " + flat.size() + " fields");
        List<CharacterInfo> characters = new ArrayList<>();
        for (int i = 0; i < flat.size(); i += 4)
            info(flat.get(i), flat.get(i + 1), flat.get(i + 2), Boolean.parseBoolean(flat.get(i + 3))).ifPresent(characters::add);
        return characters;
    }

    /** Input is in menu order; disabled characters are dropped. */
    static List<CharacterInfo> enabled(List<CharacterInfo> characters) {
        return characters.stream().filter(CharacterInfo::enabled).toList();
    }

    /** Character ids are unique (db/002); if a duplicate slipped through anyway, refuse to guess. */
    static Optional<CharacterInfo> single(UUID id, List<CharacterInfo> matches, Logger logger) {
        List<CharacterInfo> exact = matches.stream().filter(c -> c.id().equals(id)).toList();
        if (exact.size() > 1) {
            logger.warning("Character id " + id + " is held by several accounts "
                    + exact.stream().map(c -> c.owner().toString()).toList() + "; returning no match");
            return Optional.empty();
        }
        return exact.stream().findFirst();
    }

    private static UUID uuid(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            return parsed.toString().equalsIgnoreCase(value) ? parsed : null;
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
