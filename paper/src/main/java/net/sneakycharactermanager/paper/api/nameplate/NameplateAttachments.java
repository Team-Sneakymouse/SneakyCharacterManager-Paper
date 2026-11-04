package net.sneakycharactermanager.paper.api.nameplate;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Lets another plugin put a player's SCM nameplate on a different entity (a body copy, a shell, a disguise) without
 * touching SCM's display entity. Registered in Bukkit's {@link org.bukkit.plugin.ServicesManager} once SCM has
 * enabled; Bukkit unregisters it when SCM disables.
 *
 * <pre>{@code
 * NameplateAttachments.get().ifPresent(api -> {
 *     AttachResult result = api.attach(myPlugin, player, shell);
 *     if (result instanceof AttachResult.Attached attached) handles.put(player.getUniqueId(), attached.handle());
 * });
 * }</pre>
 *
 * All methods must be called on the server main thread and throw {@link IllegalStateException} otherwise.
 */
public interface NameplateAttachments {

    /**
     * Attaches {@code owner}'s nameplate to {@code anchor}. First attachment wins: while another plugin's attachment
     * is active the result is {@link AttachResult.Reason#CONFLICT}. Attaching again with the same consumer returns the
     * same handle, moved to the new anchor.
     *
     * <p>The owner does not need a nameplate right now (no character, dead, spectating, vanished); the attachment is
     * applied whenever SCM builds one.
     */
    AttachResult attach(Plugin consumer, Player owner, Entity anchor);

    /** The consumer's active handle for {@code owner}, if any. */
    Optional<NameplateHandle> current(Plugin consumer, Player owner);

    /**
     * The registered service, or empty when SCM is not enabled. Callers that only compile against SCM must still
     * guard against {@link LinkageError} when SCM is not installed.
     */
    static Optional<NameplateAttachments> get() {
        return Optional.ofNullable(Bukkit.getServicesManager().load(NameplateAttachments.class));
    }
}
