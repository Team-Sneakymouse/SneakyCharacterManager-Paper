package net.sneakycharactermanager.paper.api.character;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.HolderRegistry;

/**
 * The holder registry: which online body wears which character. At most one body wears a character; SCM refuses any
 * load that would put a worn character on a second body. The owner has two exceptions, described in the README
 * ("Holder rule"): picking it in {@code /char} calls the {@linkplain #setSelectionHandler selection handler}, and
 * joining as it takes it back from the holder.
 *
 * <p>All methods must be called on the server main thread.
 */
public final class CharacterHolders {

    private CharacterHolders() {}

    /** The body wearing {@code key} right now, or null. */
    public static @Nullable Player holderOf(CharacterKey key) {
        return Character.wearerOf(Objects.requireNonNull(key, "key"));
    }

    /** True if some body wears {@code key} right now. */
    public static boolean isHeld(CharacterKey key) {
        return holderOf(key) != null;
    }

    /**
     * Sets the handler for an owner picking their held character in {@code /char}. One handler at a time: a second
     * plugin replaces the first. The handler is dropped when {@code plugin} disables. Without a handler, the pick is
     * refused.
     */
    public static void setSelectionHandler(Plugin plugin, HeldCharacterSelectionHandler handler) {
        HolderRegistry.setSelectionHandler(Objects.requireNonNull(plugin, "plugin"), Objects.requireNonNull(handler, "handler"));
    }

    /** Removes {@code plugin}'s handler, if it is the one set. */
    public static void clearSelectionHandler(Plugin plugin) {
        HolderRegistry.clearSelectionHandler(Objects.requireNonNull(plugin, "plugin"));
    }
}
