package net.sneakycharactermanager.paper.handlers.character;

import net.sneakycharactermanager.paper.handlers.skins.SkinState;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CharacterSkinChangeEvent extends PlayerEvent implements Cancellable {

	protected static final HandlerList handlers = new HandlerList();
	private boolean cancel = false;

	private final SkinState skinState;

	/**
	 * @param player    The player changing their skin
	 * @param skinState A <strong>pending</strong> snapshot from {@link SkinState#pendingCharacterSkinChange(String, String, Boolean)}.
	 *                  {@link SkinState#proxyTextureUrl()} is the skin URL to fetch or the literal {@code "default"} for Mojang revert;
	 *                  {@link SkinState#slimModel()} is slim/classic when known, or {@code null} to auto-detect.
	 */
	public CharacterSkinChangeEvent(@NotNull Player player, @NotNull SkinState skinState) {
		super(player);
		if (!skinState.isPendingSkinChange()) {
			throw new IllegalArgumentException(
					"CharacterSkinChangeEvent requires a pending skin state; use SkinState.pendingCharacterSkinChange(...)");
		}
		this.skinState = skinState;
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

	public @NotNull SkinState getSkinState() {
		return skinState;
	}

	public String getCharacterUUID() {
		return skinState.characterUUID();
	}

	/** @return Requested skin URL, or {@code "default"} when reverting to Mojang skin */
	public String getSkinUrl() {
		return skinState.proxyTextureUrl();
	}

	public @Nullable Boolean getSlim() {
		return skinState.slimModel();
	}

}
