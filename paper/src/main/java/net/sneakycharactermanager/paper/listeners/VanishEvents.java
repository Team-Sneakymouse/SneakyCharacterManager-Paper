package net.sneakycharactermanager.paper.listeners;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.nametags.NametagManager;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerHideEntityEvent;
import org.bukkit.event.player.PlayerShowEntityEvent;

/**
 * Per-viewer hide/show. Paper fires these only when an entity becomes hidden from / visible to one viewer, after
 * {@code canSee} already reflects the change, so the plate is reconciled for that viewer only. A genuinely vanished
 * owner (CMI) loses the plate for everyone instead.
 */
public class VanishEvents implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVanish(PlayerHideEntityEvent event){
        NametagManager manager = SneakyCharacterManager.getInstance().nametagManager;
        if (event.getEntity() instanceof Player owner) manager.ownerHidden(owner, event.getPlayer());
        manager.anchorVisibilityChanged(event.getEntity(), event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVanish(PlayerShowEntityEvent event){
        NametagManager manager = SneakyCharacterManager.getInstance().nametagManager;
        if (event.getEntity() instanceof Player owner) manager.ownerShown(owner, event.getPlayer());
        manager.anchorVisibilityChanged(event.getEntity(), event.getPlayer());
    }

}
