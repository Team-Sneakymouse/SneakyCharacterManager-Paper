package net.sneakycharactermanager.paper.handlers.character;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.api.character.CharacterKey;

// Public API (with CharacterNickChangeEvent, CharacterSkinChangeEvent, Character.get): used by MagicSpells,
// SneakyVehicles and others, so keep signatures compatible or update those repos together.
/**
 * Fired before a character is put on a player. {@link #getPlayer()} is the body that will wear it;
 * {@link #getCharacterKey()} is the character, whose owner can be a different player.
 */
public class LoadCharacterEvent extends PlayerEvent implements Cancellable {

	protected static final HandlerList handlers = new HandlerList();
	private boolean cancel = false;

	private final CharacterKey key;
	private final boolean firstLoad;
	private final String characterName;
	private final String skin;
	private final boolean slim;
	private final String tags;

	public LoadCharacterEvent(@NotNull Player player, @NotNull CharacterKey key, boolean firstLoad, String characterName,
			String skin, boolean slim, String tags) {
		super(player);
		this.key = key;
		this.firstLoad = firstLoad;
		this.characterName = characterName;
		this.skin = skin;
		this.slim = slim;
		this.tags = tags;
	}

	/**
	 * @deprecated assumes {@code player} owns the character; use the constructor that takes a {@link CharacterKey}.
	 *             Kept for callers outside SCM that build the event themselves (SneakyItems' tests).
	 */
	@Deprecated
	public LoadCharacterEvent(@NotNull Player player, boolean firstLoad, String characterUUID, String characterName,
			String skin, boolean slim, String tags) {
		this(player, new CharacterKey(player.getUniqueId(), characterUUID), firstLoad, characterName, skin, slim, tags);
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handlers;
	}

	public static @NotNull HandlerList getHandlerList() {
		return handlers;
	}

	@Override
	public boolean isCancelled() {
		return this.cancel;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancel = cancel;
	}

	/** The character being loaded: its owner and ID. */
	public @NotNull CharacterKey getCharacterKey() {
		return key;
	}

	/** The account that owns the character; not necessarily {@link #getPlayer()}. */
	public @NotNull UUID getOwnerUUID() {
		return key.owner();
	}

	public boolean getFirstLoad() {
		return firstLoad;
	}

	public String getCharacterUUID() {
		return key.characterId();
	}

	public String getCharacterName() {
		return characterName;
	}

	public String getSkin() {
		return skin;
	}

	public boolean isSlim() {
		return slim;
	}

	public String getTags() {
		return tags;
	}

}
