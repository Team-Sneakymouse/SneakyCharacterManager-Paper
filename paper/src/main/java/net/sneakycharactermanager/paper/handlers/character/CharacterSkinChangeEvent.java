package net.sneakycharactermanager.paper.handlers.character;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CharacterSkinChangeEvent extends PlayerEvent implements Cancellable {

	protected static final HandlerList handlers = new HandlerList();
	private boolean cancel = false;

	private final String characterUUID;
	private final String skinUrl;
	private final @Nullable Boolean slim;

	/**
	 * @param player        The player changing their skin
	 * @param characterUUID The UUID of the character being modified
	 * @param skinUrl       The skin URL being applied, or "default" when reverting to the player's Mojang skin
	 * @param slim          True for slim, false for classic, null if unspecified (auto-detect)
	 */
	public CharacterSkinChangeEvent(@NotNull Player player, String characterUUID, String skinUrl,
			@Nullable Boolean slim) {
		super(player);
		this.characterUUID = characterUUID;
		this.skinUrl = skinUrl;
		this.slim = slim;
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

	public String getSkinUrl() {
		return skinUrl;
	}

	public @Nullable Boolean getSlim() {
		return slim;
	}

}
