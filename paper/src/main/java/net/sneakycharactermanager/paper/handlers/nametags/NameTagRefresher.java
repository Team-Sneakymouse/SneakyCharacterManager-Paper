package net.sneakycharactermanager.paper.handlers.nametags;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import me.clip.placeholderapi.PlaceholderAPI;
import net.sneakycharactermanager.paper.SneakyCharacterManager;

public class NameTagRefresher extends BukkitRunnable {

    private BukkitTask task = null;
    private Map<Player, List<Player>> trackedByPrev = new ConcurrentHashMap<>();

    public NameTagRefresher() {
        this.task = runTaskTimerAsynchronously(SneakyCharacterManager.getInstance(), 0, 20);
    }

    public void stop() {
        this.task.cancel();
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            List<Player> trackingPlayers = new ArrayList<>();
            if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR || (PlaceholderAPI.setPlaceholders(player, "%cmi_user_vanished_symbol%") != null && !PlaceholderAPI.setPlaceholders(player, "%cmi_user_vanished_symbol%").isEmpty())) {
                trackedByPrev.put(player, trackingPlayers);
                continue;
            }

            List<Player> trackingPlayersPrev = trackedByPrev.get(player);

            for (Player tracking : player.getTrackedBy()) {
                trackingPlayers.add(player);
                if (trackingPlayersPrev != null && trackingPlayersPrev.contains(tracking)) continue;

                Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
                    SneakyCharacterManager.getInstance().nametagManager.refreshNickname(tracking, player.getUniqueId().toString());
                });
            }

            trackedByPrev.put(player, trackingPlayers);
        }
    }
    
}
