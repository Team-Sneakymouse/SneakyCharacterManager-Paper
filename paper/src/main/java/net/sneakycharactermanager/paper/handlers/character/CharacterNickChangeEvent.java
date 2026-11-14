package net.sneakycharactermanager.paper.handlers.character;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.api.character.CharacterKey;

public class CharacterNickChangeEvent extends PlayerEvent implements Cancellable {

	protected static final HandlerList handlers = new HandlerList();
	private boolean cancel = false;

	private final CharacterKey key;
	private final String oldName;
	private final String newName;

	public CharacterNickChangeEvent(@NotNull Player player, @NotNull CharacterKey key, String oldName, String newName) {
		super(player);
		this.key = key;
		this.oldName = oldName;
		this.newName = newName;
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

	/** The renamed character; its owner can differ from {@link #getPlayer()}, who is wearing it. */
	public @NotNull CharacterKey getCharacterKey() {
		return key;
	}

	public String getCharacterUUID() {
		return key.characterId();
	}

	public String getOldName() {
		return oldName;
	}

	public String getNewName() {
		return newName;
	}

}
