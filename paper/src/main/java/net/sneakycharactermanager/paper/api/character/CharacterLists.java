package net.sneakycharactermanager.paper.api.character;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.storage.DatabaseBackend;
import net.sneakycharactermanager.paper.storage.DatabaseOperations;

/**
 * Any owner's characters, online or offline, for another plugin's menu. SneakyDB mode only.
 *
 * <p>Must be called on the server main thread; the future completes on it.
 *
 * <p>For plain names only, or to look one character up by its ID, use
 * {@link net.sneakycharactermanager.paper.api.directory.CharacterDirectory}, which works in both storage modes.
 */
public final class CharacterLists {

    private CharacterLists() {}

    /**
     * {@code owner}'s characters in their {@code /char} order, without disabled ones, with the owner's current
     * character marked ({@link ListedCharacter#current()}). An owner storage doesn't know gets an empty list. Reads
     * only: it creates nothing in storage and doesn't touch any player or SCM's own caches.
     *
     * <p>Completes exceptionally with {@link UnsupportedOperationException} in legacy proxy mode, and with another
     * exception if storage fails or SCM shuts down.
     */
    public static CompletableFuture<List<ListedCharacter>> of(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        Puppets.requireMainThread();
        DatabaseBackend backend = Puppets.backend();
        if (backend == null) return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Character lists need SCM's SneakyDB storage mode"));
        return backend.listCharacters(owner).thenApply(roster -> {
            CharacterKey current = current(owner, roster);
            return roster.characters().stream().map(c -> {
                CharacterKey key = new CharacterKey(owner, c.id());
                return new ListedCharacter(key, c.name(), c.skin(), c.texture(), c.signature(), c.slim(), key.equals(current));
            }).toList();
        });
    }

    /** What the owner wears if online and wearing one of their own characters, otherwise their last-played one. */
    private static @Nullable CharacterKey current(UUID owner, DatabaseOperations.Roster roster) {
        Player online = Bukkit.getPlayer(owner);
        Character worn = online == null ? null : Character.get(online);
        if (worn != null && worn.getOwnerUUID().equals(owner)) return worn.getKey();
        return roster.lastPlayed().isEmpty() ? null : new CharacterKey(owner, roster.lastPlayed());
    }
}
