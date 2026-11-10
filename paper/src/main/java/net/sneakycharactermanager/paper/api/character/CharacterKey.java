package net.sneakycharactermanager.paper.api.character;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifies a character: the account that owns it plus its character ID. Character IDs are unique across all
 * accounts, but everything stored about a character is filed under its owner, so the key carries both.
 *
 * <p>The rule: everything stored about a character (its identity record and its Paper-local gameplay file
 * {@code characterdata/<owner>/<characterId>.yml}) is addressed by this key, never by the player who is wearing the
 * character right now. Usually the wearer is the owner, but another player can wear a character it does not own.
 *
 * <p>State that deliberately stays with the wearing player instead is listed in the README ("Character identity").
 */
public record CharacterKey(UUID owner, String characterId) {

    public CharacterKey {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(characterId, "characterId");
        if (characterId.isEmpty()) throw new IllegalArgumentException("characterId is empty");
    }

    /** Parses the owner from its string form, as storage and the plugin-message protocol carry it. */
    public static CharacterKey of(String owner, String characterId) {
        return new CharacterKey(UUID.fromString(owner), characterId);
    }

    @Override
    public String toString() {
        return owner + "/" + characterId;
    }
}
