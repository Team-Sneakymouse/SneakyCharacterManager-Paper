package net.sneakycharactermanager.paper.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;

public class TeleportEvents implements Listener {

    @EventHandler
    public void onGamemodeChange(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        if (!rebuildsNametag(event.getFrom().getWorld() == event.getTo().getWorld(), player.isInsideVehicle())) return;

        SneakyCharacterManager.getInstance().nametagManager.unnicknamePlayer(player);

        Bukkit.getScheduler().runTaskLater(SneakyCharacterManager.getInstance(), ()->{
            Character character = Character.get(player);

            if(character == null) return;

            SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(player, character.getDisplayName());
        }, 2);
    }

    /**
     * Whether a teleport needs the nametag rebuilt. A rider carried along by a moving vehicle (a theme park ride
     * moves its seats every tick or two, and Paper reports each move as a teleport of the rider) keeps its passengers,
     * nametag included, so rebuilding it then only makes the name flash.
     */
    static boolean rebuildsNametag(boolean sameWorld, boolean insideVehicle) {
        return !(sameWorld && insideVehicle);
    }

}
