package net.sneakycharactermanager.paper.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.nameplate.ReleaseReason;
import net.sneakycharactermanager.paper.handlers.character.CharacterSwap;
import net.sneakycharactermanager.paper.handlers.nametags.NametagManager;

public class TeleportEvents implements Listener {

    enum Action { NONE, REBUILD, RELEASE_THEN_REBUILD }

    /** MONITOR + ignoreCancelled: only teleports that will actually happen, at their final destination. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGamemodeChange(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        // A swap's own teleports keep the plate riding along; the swap re-texts it (NametagManager.showWornCharacter).
        if (CharacterSwap.isSwapping(player)) return;
        NametagManager manager = SneakyCharacterManager.getInstance().nametagManager;

        Action action = decide(
                event.getFrom().getWorld() == event.getTo().getWorld(),
                player.isInsideVehicle(),
                manager.isAttached(player),
                manager.hasActiveAnchor(player));
        switch (action) {
            case NONE -> { }
            case RELEASE_THEN_REBUILD -> {
                manager.dropAttachment(player.getUniqueId(), ReleaseReason.OWNER_CHANGED_WORLD);
                manager.rebuild(player);
            }
            case REBUILD -> manager.rebuild(player);
        }
    }

    /**
     * What a teleport does to the plate.
     * <ul>
     *   <li>A rider carried along by a moving vehicle (a theme park ride moves its seats every tick or two, and Paper
     *       reports each move as a teleport of the rider) keeps its passengers, nametag included, so rebuilding it then
     *       only makes the name flash.</li>
     *   <li>A plate held on an anchor is not on the player, so a same-world teleport leaves it alone.</li>
     *   <li>Changing world ends any attachment (its anchor stays behind), then the plate is rebuilt.</li>
     * </ul>
     */
    static Action decide(boolean sameWorld, boolean insideVehicle, boolean attached, boolean anchoredAway) {
        if (sameWorld) return insideVehicle || anchoredAway ? Action.NONE : Action.REBUILD;
        return attached ? Action.RELEASE_THEN_REBUILD : Action.REBUILD;
    }

}
