package net.sneakycharactermanager.paper.handlers.nametags;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;

public class NameTagRefresher extends BukkitRunnable {

    private BukkitTask task = null;
    private List<Player> handled = new ArrayList<>();

    public NameTagRefresher() {
        this.task = runTaskTimer(SneakyCharacterManager.getInstance(), 0, 1);
    }

    public void stop() {
        this.task.cancel();
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (handled.contains(player)) continue;
            handled.add(player);

            Character character = Character.get(player);

            if (character == null) continue;

            SneakyCharacterManager.getInstance().nametagManager.refreshNickname(player);

            return;
        }
        this.handled.clear();
    }
    
}
