package net.sneakycharactermanager.paper.api.character;

import org.bukkit.entity.Player;

/**
 * Called instead of a load when an owner picks, in {@code /char}, one of their own characters that another body is
 * wearing right now. Register one with {@link CharacterHolders#setSelectionHandler}.
 */
@FunctionalInterface
public interface HeldCharacterSelectionHandler {

    /**
     * Runs on the server main thread. SCM has not loaded anything and has not changed the owner's last-played
     * character (in SneakyDB mode; see the README for legacy mode).
     *
     * @param owner  the player who picked the character; always its owner
     * @param key    the character
     * @param holder the other body wearing it
     * @return true if the handler dealt with the pick (SCM says nothing), false to let SCM refuse it with its usual
     *         "being played" message
     */
    boolean onOwnerSelect(Player owner, CharacterKey key, Player holder);
}
