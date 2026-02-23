package net.sneakycharactermanager.paper.handlers.character;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public class CharacterNickChangeEvent extends PlayerEvent implements Cancellable {

	protected static final HandlerList handlers = new HandlerList();
	private boolean cancel = false;

	private final String characterUUID;
	private final String oldName;
	private final String newName;

	public CharacterNickChangeEvent(@NotNull Player player, String characterUUID, String oldName, String newName) {
		super(player);
		this.characterUUID = characterUUID;
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

	public String getCharacterUUID() {
		return characterUUID;
	}

	public String getOldName() {
		return oldName;
	}

	public String getNewName() {
		return newName;
	}

}
