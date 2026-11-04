package net.sneakycharactermanager.paper.api.nameplate;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * A consumer's claim on one player's SCM nameplate. The claim survives SCM rebuilding the plate (nickname, title,
 * gender, reload, death, gamemode, vanish): every rebuilt plate is placed and shown according to this handle.
 *
 * <p>Mutating methods and {@link #close()} must be called on the server main thread and throw
 * {@link IllegalStateException} otherwise. {@link #owner()}, {@link #consumer()}, {@link #isActive()} and
 * {@link #releaseReason()} are safe from any thread.
 *
 * <p>Once released (see {@link ReleaseReason}) the handle is inert: mutators return {@code false} and never affect a
 * later attachment for the same owner, even one made by the same consumer.
 */
public interface NameplateHandle extends AutoCloseable {

    /** UUID of the player whose nameplate this is. */
    UUID owner();

    /** The plugin that attached. */
    Plugin consumer();

    /** {@code false} after any release. */
    boolean isActive();

    /** Why the handle was released; empty while active. */
    Optional<ReleaseReason> releaseReason();

    /**
     * Mounts the plate on {@code anchor} and unparks it. Same rules as attach: valid, not the owner, not an SCM
     * nameplate, in the owner's world. Returns {@code false} (attachment unchanged) when refused or inert. Plate
     * visibility then follows each viewer's visibility of the anchor.
     */
    boolean moveTo(Entity anchor);

    /** Puts the plate back on the owner, hidden from every viewer. The handle stays active; unpark with {@link #moveTo}. */
    boolean park();

    /** Shows blank text to every viewer (for example while sneaking or burrowed); {@code false} restores the name. */
    boolean setBlank(boolean blank);

    /** Replaces the set of viewers who never see this plate. */
    boolean setExcludedViewers(Collection<UUID> viewers);

    /**
     * Releases the attachment with {@link ReleaseReason#CLOSED}: the plate returns to the owner with normal
     * visibility (hidden from viewers who cannot see the owner) and normal text. Idempotent.
     */
    @Override
    void close();
}
