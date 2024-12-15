package net.sneakycharactermanager.paper.handlers.nametags;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import me.clip.placeholderapi.PlaceholderAPI;
import net.sneakycharactermanager.paper.SneakyCharacterManager;

public class NameTagRefresher extends BukkitRunnable {

    public BukkitTask task = null;
    private ConcurrentMap<Player, List<Player>> trackedByPrev = new ConcurrentHashMap<>();

    public NameTagRefresher() {
        this.task = runTaskTimer(SneakyCharacterManager.getInstance(), 0, 20);
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Nickname name = SneakyCharacterManager.getInstance().nametagManager.getNickname(player);
            if (player.isDead() ||
                player.getGameMode() == GameMode.SPECTATOR ||
                (SneakyCharacterManager.getInstance().papiActive && PlaceholderAPI.setPlaceholders(player, "%cmi_user_vanished_symbol%") != null && !PlaceholderAPI.setPlaceholders(player, "%cmi_user_vanished_symbol%").isEmpty()) ||
                name == null
            ) {
                trackedByPrev.put(player, new ArrayList<>());
                continue;
            }

            trackedByPrev.put(player, SneakyCharacterManager.getInstance().nametagManager.refreshNicknames(player, name, trackedByPrev.getOrDefault(player, new ArrayList<>())));
        }
    }
    
}
