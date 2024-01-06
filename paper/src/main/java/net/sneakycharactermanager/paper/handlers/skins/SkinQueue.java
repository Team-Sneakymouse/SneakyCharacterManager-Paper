package net.sneakycharactermanager.paper.handlers.skins;

import java.util.*;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.destroystokyo.paper.profile.ProfileProperty;

import net.sneakycharactermanager.paper.SneakyCharacterManager;

public class SkinQueue extends BukkitRunnable {

    private Map<Integer, List<SkinData>> queue = new LinkedHashMap<>();
    private BukkitTask task = null;
    private boolean busy = false;
    public int pauseTicks = 0;

    public synchronized void add(SkinData skinData, int priority) {
        this.queue.computeIfAbsent(priority, k -> new ArrayList<>()).add(skinData);
        this.start();
    }

    public synchronized void remove(SkinData skinData) {
        this.queue.values().forEach(list -> list.removeIf(s -> s.equals(skinData)));
    }

    private synchronized SkinData getNext() {
        Optional<Entry<Integer, List<SkinData>>> maxEntry = this.queue.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .max(Comparator.comparingInt(Entry::getKey));
    
        return maxEntry.map(entry -> entry.getValue().get(0)).orElse(null);
    }

    private synchronized void start() {
        if (this.task != null) return;
        this.task = runTaskTimerAsynchronously(SneakyCharacterManager.getInstance(), 0, 1);
    }

    private synchronized void stop() {
        this.task.cancel();
        SneakyCharacterManager.getInstance().skinQueue = new SkinQueue();
    }

    @Override
    public synchronized void run() {
        if (this.busy) {
            return;
        }

        if (this.pauseTicks > 0) {
            this.pauseTicks--;
            return;
        }

        SkinData next = this.getNext();

        if (next == null) {
            this.stop();
        } else {
            this.busy = true;
            next.convertSkinURL();
            if (next.isProcessed()) {
                if (next.isValid()) {
                    Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
                        ProfileProperty property = next.getTextureProperty();

                        if (property == null) return;

                        SkinCache.put(next.getPlayer().getUniqueId().toString(), next.getUrl(), property);
                        next.apply();
                    });
                }
                next.remove();
            }
            this.busy = false;
            this.pauseTicks += 5;
        }
    }
}
