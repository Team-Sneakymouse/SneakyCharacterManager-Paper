package net.sneakycharactermanager.paper.api.directory;

import java.util.UUID;

/**
 * Read-only identity of one character.
 *
 * @param id      character id, unique across all accounts
 * @param owner   account (player) UUID that owns the character
 * @param name    character name as plain text, with color and formatting codes removed
 * @param enabled false once the owner deleted the character; disabled characters are kept, not removed
 */
public record CharacterInfo(UUID id, UUID owner, String name, boolean enabled) {}
