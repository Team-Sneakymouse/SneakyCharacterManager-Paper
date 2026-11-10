package net.sneakycharactermanager.paper.api.character;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * A body stopped wearing a character it doesn't own (a puppet ended). Fired on the main thread, once, after the
 * borrowed character was saved to its owner and taken off the body, by whichever way it left ({@link #getReason()}).
 * Not cancellable. Not fired when SCM itself disables: the body keeps the character until the server stops, and its
 * state is still saved to the owner.
 */
public final class PuppetLeaveEvent extends PlayerEvent {

    public enum Reason {
        /** {@link Puppets#release} took it off. */
        RELEASED,
        /** Another character was loaded onto the body: its own {@code /char}, a staff load, another puppet. */
        LOADED_OTHER,
        /** The body quit. */
        QUIT,
        /** The owner joined as the character and took it back (the holder rule's owner-join exception). */
        OWNER_JOINED,
        /** The console's {@code chardisable} took the body off every character. */
        LOCKED_OUT
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final CharacterKey character;
    private final Reason reason;

    public PuppetLeaveEvent(Player body, CharacterKey character, Reason reason) {
        super(Objects.requireNonNull(body, "body"));
        this.character = Objects.requireNonNull(character, "character");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /** The character the body wore. Its owner is not the body. */
    public CharacterKey getCharacterKey() {
        return character;
    }

    public Reason getReason() {
        return reason;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
