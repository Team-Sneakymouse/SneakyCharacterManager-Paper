package net.sneakycharactermanager.paper.handlers.character;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.sneakycharactermanager.paper.api.character.CharacterKey;

// Public API, like LoadCharacterEvent: other plugins listen to it, so keep signatures compatible.
/**
 * Fired after a body started wearing a character: the "character changed" signal. A snapshot of the change, delivered
 * on the main thread, synchronously, once the load or swap that made it has finished its own processing (the worn
 * character, the nameplate, its {@code PuppetLeaveEvent}, its reservations). Delivered before that load or swap call
 * returns, unless the call was made while another {@code CharacterLoadedEvent} is being delivered (see below).
 * Not cancellable.
 * <ul>
 * <li>A load ({@link Character#load()}, a join, {@code /char}, a create, a puppet, a release's return, a staff load):
 *     once, after a {@link LoadCharacterEvent} that nobody cancelled, after the load's own
 *     {@link net.sneakycharactermanager.paper.api.character.PuppetLeaveEvent} for a borrowed character it replaced.
 *     A cancelled or refused load fires nothing. Reloading the character the body already wears fires it too, with
 *     {@link #getPreviousCharacterKey()} equal to {@link #getCharacterKey()}.</li>
 * <li>A swap ({@code Puppets.swap}): once for each body, after the swap's own processing, with {@link #isSwap()} true. A
 *     swap fires no {@link LoadCharacterEvent}, so listeners that reset a character's state on load should skip
 *     swaps.</li>
 * </ul>
 * <p><b>Order and staleness.</b> Events are delivered in the order their loads and swaps <em>finished processing</em>,
 * not the order the characters were put on. So a load whose own {@code PuppetLeaveEvent} listener loads a character
 * onto another body delivers that other body's event first. A listener that loads or swaps characters doesn't
 * interrupt the event being delivered: the new change's event is queued and delivered after this one reached every
 * listener, and after that load or swap call returned. An event whose body changed again before its turn (another
 * load, a same-character reload, a swap, a quit, a lock-out) is dropped. So for a body that still wears a character,
 * the last event a listener saw for it is what it wears now. A body that wears nothing gets no event for that (see
 * below), so the last event can be stale for it. While an event is delivered, live state ({@link Character#get(Player)}, the {@code %sneakycharacters_character_*%}
 * placeholders) is at least this new, and may already be newer if an earlier listener changed the body; read the
 * event's fields for this change.
 * <p>Nothing fires when a body stops wearing anything: a quit ({@code PlayerQuitEvent}), a release until its own
 * character arrives ({@code PuppetLeaveEvent}), or the console's {@code chardisable}. A {@code chardisable} of a body
 * wearing a borrowed character fires {@code PuppetLeaveEvent} ({@code LOCKED_OUT}); of a body wearing its own
 * character it fires no SCM event at all, and the player stays online. A cache of what bodies wear should also check
 * {@link Character#get(Player)} (null) where being stale matters. The skin may still be on its way when this
 * fires.
 */
public class CharacterLoadedEvent extends PlayerEvent {

	private static final HandlerList HANDLERS = new HandlerList();

	private final CharacterKey key;
	private final @Nullable CharacterKey previous;
	private final boolean firstLoad;
	private final boolean swap;
	private final String characterName;

	public CharacterLoadedEvent(@NotNull Player body, @NotNull CharacterKey key, @Nullable CharacterKey previous,
			boolean firstLoad, boolean swap, String characterName) {
		super(Objects.requireNonNull(body, "body"));
		this.key = Objects.requireNonNull(key, "key");
		this.previous = previous;
		this.firstLoad = firstLoad;
		this.swap = swap;
		this.characterName = characterName;
	}

	/** The character the body ({@link #getPlayer()}) wears now: its owner and ID. */
	public @NotNull CharacterKey getCharacterKey() {
		return key;
	}

	/** The account that owns the character; not necessarily {@link #getPlayer()}. */
	public @NotNull UUID getOwnerUUID() {
		return key.owner();
	}

	public @NotNull String getCharacterUUID() {
		return key.characterId();
	}

	/** What the body wore just before, or null if it wore nothing (a login, after a release or {@code charenable}). */
	public @Nullable CharacterKey getPreviousCharacterKey() {
		return previous;
	}

	/** True if the character had no gameplay file on this server before this load. Always false for a swap. */
	public boolean isFirstLoad() {
		return firstLoad;
	}

	/** True if a swap put the character on this body, rather than a load. */
	public boolean isSwap() {
		return swap;
	}

	public String getCharacterName() {
		return characterName;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

	public static @NotNull HandlerList getHandlerList() {
		return HANDLERS;
	}
}
