package net.sneakycharactermanager.paper.handlers.nametags;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import net.sneakycharactermanager.paper.api.nameplate.NameplateHandle;
import net.sneakycharactermanager.paper.api.nameplate.ReleaseReason;

/**
 * One consumer's attachment of one owner's plate, and the handle returned to that consumer. The object identity is the
 * attachment generation: the registry only acts on the instance it currently holds, so a released handle (or one from
 * an earlier attachment of the same owner) can never touch a later one.
 */
final class PlateAttachment implements NameplateHandle {

    /** Operations the handle forwards to; implemented by {@link NametagManager}. */
    interface Operations {
        boolean moveTo(PlateAttachment attachment, Entity anchor);
        boolean park(PlateAttachment attachment);
        boolean setBlank(PlateAttachment attachment, boolean blank);
        boolean setExcludedViewers(PlateAttachment attachment, Collection<UUID> viewers);
        void close(PlateAttachment attachment);
    }

    private final UUID owner;
    private final Plugin consumer;
    private final Operations operations;

    private Entity anchor;
    private boolean parked;
    private boolean blank;
    private Set<UUID> excluded = Set.of();
    private volatile ReleaseReason releaseReason;

    PlateAttachment(UUID owner, Plugin consumer, Entity anchor, Operations operations) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.consumer = Objects.requireNonNull(consumer, "consumer");
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    @Override public UUID owner() { return owner; }
    @Override public Plugin consumer() { return consumer; }
    @Override public boolean isActive() { return releaseReason == null; }
    @Override public Optional<ReleaseReason> releaseReason() { return Optional.ofNullable(releaseReason); }

    @Override public boolean moveTo(Entity anchor) { return operations.moveTo(this, anchor); }
    @Override public boolean park() { return operations.park(this); }
    @Override public boolean setBlank(boolean blank) { return operations.setBlank(this, blank); }
    @Override public boolean setExcludedViewers(Collection<UUID> viewers) { return operations.setExcludedViewers(this, viewers); }
    @Override public void close() { operations.close(this); }

    /** The anchor while unparked; {@code null} while parked. */
    Entity anchor() { return anchor; }
    boolean parked() { return parked; }
    boolean blank() { return blank; }
    boolean excludes(UUID viewer) { return excluded.contains(viewer); }
    Set<UUID> excluded() { return excluded; }

    // Mutated only through PlateAttachmentRegistry so its anchor index stays consistent.
    void anchorTo(Entity anchor) { this.anchor = Objects.requireNonNull(anchor, "anchor"); this.parked = false; }
    void parkOnOwner() { this.anchor = null; this.parked = true; }
    void blank(boolean blank) { this.blank = blank; }
    void excluded(Set<UUID> excluded) { this.excluded = Set.copyOf(excluded); }
    void released(ReleaseReason reason) { this.releaseReason = Objects.requireNonNull(reason, "reason"); }

    @Override
    public String toString() {
        return "PlateAttachment[owner=" + owner + ", consumer=" + consumer.getName() + ", parked=" + parked
                + ", released=" + releaseReason + "]";
    }
}
