package net.sneakycharactermanager.paper.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.server.PluginDisableEvent;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.nameplate.ReleaseReason;
import net.sneakycharactermanager.paper.handlers.nametags.NametagManager;

/** Ends nameplate attachments whose consumer, anchor or world went away. */
public class NameplateLifecycleListener implements Listener {

    private final SneakyCharacterManager plugin;

    public NameplateLifecycleListener(SneakyCharacterManager plugin) {
        this.plugin = plugin;
    }

    private NametagManager manager() {
        return plugin.nametagManager;
    }

    /** Fired before the consumer's onDisable, so its plates can still be returned. SCM's own disable is separate. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) return;
        manager().releaseConsumer(event.getPlugin());
    }

    /** Removal, chunk unload or world change of an anchor. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        manager().anchorRemoved(event.getEntity().getUniqueId());
    }

    /** Backstop for world changes that did not go through a non-cancelled teleport event (e.g. respawn). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        manager().releaseOwner(event.getPlayer().getUniqueId(), ReleaseReason.OWNER_CHANGED_WORLD);
    }
}
