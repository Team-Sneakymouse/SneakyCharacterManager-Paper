package net.sneakycharactermanager.paper.api.character;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.HolderRegistry;
import net.sneakycharactermanager.paper.storage.DatabaseBackend;

/**
 * Puppeting: putting another player's character on a body, and taking it off again. SneakyDB mode only; in legacy
 * proxy mode every call reports {@code UNSUPPORTED}. The cases are listed in the README ("Puppets").
 *
 * <p>A body is puppeting while it wears a character it doesn't own. When it stops, by any route, SCM fires
 * {@link PuppetLeaveEvent} with the reason.
 *
 * <p>All methods must be called on the server main thread, and returned futures complete on it.
 */
public final class Puppets {

    private Puppets() {}

    /**
     * Puts {@code character} on {@code body}. This is a normal switch for the body: what it wore is saved first, and
     * the character loads from its owner's gameplay file (location, inventory, attributes) with its owner's name and
     * skin. The owner may be offline. Neither the body's nor the owner's last-played character changes, so a body that
     * quits rejoins as its own last-played character. Commands are not blocked, and every save and edit made while
     * wearing it goes to the owner.
     *
     * <p>Never fails silently: the future always completes, with {@link PuppetResult#PUPPETED} only if the body now
     * wears the character.
     */
    public static CompletableFuture<PuppetResult> puppet(Player body, CharacterKey character) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(character, "character");
        requireMainThread();
        DatabaseBackend backend = backend();
        if (backend == null) return CompletableFuture.completedFuture(PuppetResult.UNSUPPORTED);
        if (Bukkit.getPlayer(body.getUniqueId()) != body) return CompletableFuture.completedFuture(PuppetResult.BODY_OFFLINE);
        if (character.owner().equals(body.getUniqueId())) return CompletableFuture.completedFuture(PuppetResult.OWN_CHARACTER);
        if (heldAway(body, character)) return CompletableFuture.completedFuture(PuppetResult.HELD);
        return backend.loadBorrowed(body, character).handle((outcome, error) -> {
            if (error != null) return PuppetResult.FAILED;
            return switch (outcome) {
                case LOADED -> PuppetResult.PUPPETED;
                case REFUSED -> heldAway(body, character) ? PuppetResult.HELD : PuppetResult.REFUSED;
                case NOT_FOUND -> PuppetResult.NOT_FOUND;
                case INTERRUPTED -> PuppetResult.INTERRUPTED;
                case DONE -> PuppetResult.FAILED; // a borrow always ends in one of the above
            };
        });
    }

    /**
     * Takes the borrowed character off {@code body}: saves it where it stands (to its owner), then loads the body's own
     * last-played character as a normal switch, which takes the body to wherever that character was left. Fires
     * {@link PuppetLeaveEvent} ({@link PuppetLeaveEvent.Reason#RELEASED}).
     *
     * <p>Until its own character arrives, the body wears nothing and its inventory is cleared, so the borrowed items
     * aren't on two bodies. If someone else wears that character by then, the body gets its character menu instead.
     * A puppet still on its way to the body isn't cancelled: release once its future completes.
     */
    public static ReleaseResult release(Player body) {
        Objects.requireNonNull(body, "body");
        requireMainThread();
        if (backend() == null) return ReleaseResult.UNSUPPORTED;
        return HolderRegistry.release(body, PuppetLeaveEvent.Reason.RELEASED) ? ReleaseResult.RELEASED : ReleaseResult.NOT_PUPPETED;
    }

    /** The character {@code body} wears if it isn't the body's own, or null. */
    public static @Nullable CharacterKey borrowedBy(Player body) {
        Objects.requireNonNull(body, "body");
        requireMainThread();
        return Character.borrowedBy(body);
    }

    /** True if SCM runs in SneakyDB mode, which puppets and character lists need. */
    public static boolean isSupported() {
        return backend() != null;
    }

    /** SneakyDB's backend, or null in legacy mode. */
    static @Nullable DatabaseBackend backend() {
        if (StorageMode.current() != StorageMode.SNEAKYDB) return null;
        return SneakyCharacterManager.getInstance().getCharacterBackend() instanceof DatabaseBackend db ? db : null;
    }

    static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("SCM's puppet API must be called on the main thread");
    }

    private static boolean heldAway(Player body, CharacterKey character) {
        Player holder = Character.wearerOf(character);
        return holder != null && holder != body;
    }
}
