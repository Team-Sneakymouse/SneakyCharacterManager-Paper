package net.sneakycharactermanager.paper.handlers.nametags;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import net.sneakycharactermanager.paper.api.nameplate.ReleaseReason;

/**
 * Bookkeeping for plate attachments: at most one per owner, indexed by anchor. Has no world side effects; the
 * {@link NametagManager} moves and shows plates. Main thread only.
 */
final class PlateAttachmentRegistry {

    private final Map<UUID, PlateAttachment> byOwner = new HashMap<>();
    private final Map<UUID, Set<UUID>> ownersByAnchor = new HashMap<>();

    PlateAttachment get(UUID owner) {
        return byOwner.get(owner);
    }

    /** True while {@code attachment} is the active attachment of its owner. */
    boolean isCurrent(PlateAttachment attachment) {
        return attachment != null && byOwner.get(attachment.owner()) == attachment;
    }

    /** Registers a new attachment; the caller has checked there is none for the owner. */
    PlateAttachment add(UUID owner, Plugin consumer, Entity anchor, PlateAttachment.Operations operations) {
        if (byOwner.containsKey(owner)) throw new IllegalStateException("owner already attached: " + owner);
        PlateAttachment attachment = new PlateAttachment(owner, consumer, anchor, operations);
        byOwner.put(owner, attachment);
        index(attachment);
        return attachment;
    }

    void anchorTo(PlateAttachment attachment, Entity anchor) {
        unindex(attachment);
        attachment.anchorTo(anchor);
        index(attachment);
    }

    void park(PlateAttachment attachment) {
        unindex(attachment);
        attachment.parkOnOwner();
    }

    /** Removes the owner's attachment, marking its handle inert with {@code reason}. Returns it, or null if none. */
    PlateAttachment remove(UUID owner, ReleaseReason reason) {
        PlateAttachment attachment = byOwner.remove(owner);
        if (attachment == null) return null;
        unindex(attachment);
        attachment.released(reason);
        return attachment;
    }

    /** Owners whose active, unparked attachment uses {@code anchorId}. */
    List<UUID> ownersOfAnchor(UUID anchorId) {
        Set<UUID> owners = ownersByAnchor.get(anchorId);
        return owners == null ? List.of() : List.copyOf(owners);
    }

    List<UUID> ownersAttachedBy(Plugin consumer) {
        List<UUID> owners = new ArrayList<>();
        for (PlateAttachment attachment : byOwner.values()) {
            if (attachment.consumer().equals(consumer)) owners.add(attachment.owner());
        }
        return owners;
    }

    /** Releases everything with {@code reason}; returns the released attachments. */
    List<PlateAttachment> removeAll(ReleaseReason reason) {
        List<PlateAttachment> released = new ArrayList<>();
        for (UUID owner : List.copyOf(byOwner.keySet())) released.add(remove(owner, reason));
        return released;
    }

    Collection<PlateAttachment> all() {
        return List.copyOf(byOwner.values());
    }

    boolean isEmpty() {
        return byOwner.isEmpty();
    }

    private void index(PlateAttachment attachment) {
        if (attachment.anchor() == null) return;
        ownersByAnchor.computeIfAbsent(attachment.anchor().getUniqueId(), id -> new HashSet<>()).add(attachment.owner());
    }

    private void unindex(PlateAttachment attachment) {
        if (attachment.anchor() == null) return;
        UUID anchorId = attachment.anchor().getUniqueId();
        Set<UUID> owners = ownersByAnchor.get(anchorId);
        if (owners == null) return;
        owners.remove(attachment.owner());
        if (owners.isEmpty()) ownersByAnchor.remove(anchorId);
    }
}
