package net.sneakycharactermanager.paper.api.character;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;

/**
 * The name prefix of the character a player wears, as {@code /charprefix} sets it. The prefix goes in front of the
 * character name on the nameplate and in {@code %sneakycharacters_character_name_prefix%}. It is kept in memory on
 * the worn character only: it is not saved, and is gone when the character is loaded again or the server
 * restarts. Text is formatted like the nameplate (legacy {@code &} codes, MiniMessage tags, <code>&#92;uXXXX</code> escapes).
 *
 * <p>All methods must be called on the server main thread.
 */
public final class CharacterNamePrefix {

    private CharacterNamePrefix() {}

    /** The prefix of the character the player wears ({@code ""} when none is set), or null when they wear none. */
    public static @Nullable String get(Player player) {
        Character character = Character.get(Objects.requireNonNull(player, "player"));
        return character == null ? null : character.getNamePrefix();
    }

    /**
     * Sets the prefix of the character the player wears and updates their nameplate; {@code ""} clears it. Returns
     * false, changing nothing, when they wear no character.
     */
    public static boolean set(Player player, String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        Character character = Character.get(Objects.requireNonNull(player, "player"));
        if (character == null) return false;
        character.setNamePrefix(prefix);
        SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(player, character.getDisplayName());
        return true;
    }

    /** Clears the prefix of the character the player wears; false when they wear none. */
    public static boolean clear(Player player) {
        return set(player, "");
    }
}
