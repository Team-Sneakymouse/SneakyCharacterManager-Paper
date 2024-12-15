package net.sneakycharactermanager.paper.handlers.skins;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class SkinQueue extends BukkitRunnable {

    final ConcurrentMap<Integer, List<SkinData>> queue = new ConcurrentHashMap<>();
    public BukkitTask task = null;

    public SkinQueue() {
        this.task = this.runTaskTimerAsynchronously(SneakyCharacterManager.getInstance(), 0, 5);
    }

    public void add(SkinData skinData, int priority) {
        Bukkit.getScheduler().runTaskAsynchronously(SneakyCharacterManager.getInstance(), () -> {
            if (priority > 1) {
                List<SkinData> toCancel = new ArrayList<>();
                queue.entrySet().stream().filter(entry -> entry.getKey() > 1).forEach(entry -> entry.getValue().stream().filter(data -> skinData.getPlayer().equals(data.getPlayer())).forEach(toCancel::add));
                toCancel.forEach(SkinData::cancel);
            }

            queue.computeIfAbsent(priority, k -> new CopyOnWriteArrayList<>()).add(skinData);
        });
        skinData.runTaskTimerAsynchronously(SneakyCharacterManager.getInstance(), 0, 20);
    }

    public void remove(SkinData skinData) {
        Bukkit.getScheduler().runTaskAsynchronously(SneakyCharacterManager.getInstance(), () -> {
            queue.values().forEach(list -> list.removeIf(s -> s.equals(skinData)));
        });
    }

    private synchronized SkinData getNext() {
        Optional<Entry<Integer, List<SkinData>>> maxEntry = this.queue.entrySet().stream().filter(entry -> entry.getValue().stream().anyMatch(skinData -> !skinData.processing)).max(Comparator.comparingInt(Entry::getKey));

        return maxEntry.map(entry -> entry.getValue().get(0)).orElse(null);
    }

    public synchronized void run() {
        if (this.task == null) return;
        SkinData next = this.getNext();

        if (next != null) {
            next.processing = true;
        }
    }
}
