package net.sneakycharactermanager.paper.handlers.nametags;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import net.sneakycharactermanager.paper.api.nameplate.AttachResult;
import net.sneakycharactermanager.paper.api.nameplate.NameplateAttachments;
import net.sneakycharactermanager.paper.api.nameplate.NameplateHandle;

/** The service registered for other plugins; all work happens in {@link NametagManager}. */
public final class NameplateAttachmentsImpl implements NameplateAttachments {

    private final NametagManager manager;

    public NameplateAttachmentsImpl(NametagManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @Override
    public AttachResult attach(Plugin consumer, Player owner, Entity anchor) {
        return manager.attach(consumer, owner, anchor);
    }

    @Override
    public Optional<NameplateHandle> current(Plugin consumer, Player owner) {
        return manager.currentAttachment(consumer, owner);
    }
}
