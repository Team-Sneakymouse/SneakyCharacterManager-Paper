package net.sneakycharactermanager.paper.handlers.character;

import com.destroystokyo.paper.profile.ProfileProperty;
import net.sneakycharactermanager.paper.util.SkinUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Synchronous resolved-skin hook fired inside {@link SkinUtil#applySkin} BEFORE
 * profile preparation or application, despite the historical "Applied" name.
 * The player still has their old profile during dispatch. Read the resolved
 * property directly, or wait until the next server tick to read the player's
 * profile (provided this application was not cancelled or superseded).
 * This is not a guarantee that the client has rendered the new skin.
 *
 * <p>Every applySkin path fires this hook, including uniforms, cached character
 * loads, undo/re-apply and default-skin restoration. There is no pending
 * SkinState payload: state/history may not yet exist or may describe an older
 * skin. The supplied property is the exact resolved property being applied.
 * Callers must invoke applySkin on the server thread.
 *
 * <p>Cancellation prevents profile preparation, application, vehicle removal
 * and teleport inside applySkin only; caller-side history, caches and backend
 * updates are not rolled back. Use CharacterSkinChangeEvent to veto the request.
 */
public class CharacterSkinAppliedEvent extends PlayerEvent implements Cancellable {
    private boolean cancelled;

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
    private static final HandlerList HANDLERS = new HandlerList();
    private final ProfileProperty profileProperty;

    public CharacterSkinAppliedEvent(@NotNull Player player, @NotNull ProfileProperty profileProperty) {
        super(player);
        this.profileProperty = profileProperty;
    }

    public @NotNull ProfileProperty getProfileProperty() {
        return profileProperty;
    }

    /** @return resolved texture URL, or null when the property cannot be decoded */
    public @Nullable String getSkinUrl() {
        return SkinUtil.getTextureUrl(profileProperty);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
