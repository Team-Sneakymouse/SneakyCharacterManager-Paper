package net.sneakycharactermanager.paper.handlers.skins;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;

import com.destroystokyo.paper.profile.ProfileProperty;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.sneakycharactermanager.paper.SneakyCharacterManager;

public class SkinQueue {

    private Map<Integer, List<SkinData>> queue = new LinkedHashMap<>();
    private ScheduledTask task = null;
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
        this.task = Bukkit.getAsyncScheduler().runDelayed(SneakyCharacterManager.getInstance(), (s) -> {
            this.run();
        }, 50, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        this.task.cancel();
        this.task = null;
    }

    public synchronized void run() {
        SkinData next = this.getNext();

        if (next == null) {
            this.stop();
        } else {
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
            this.task = Bukkit.getAsyncScheduler().runDelayed(SneakyCharacterManager.getInstance(), (s) -> {
                this.run();
            }, 50 * (this.pauseTicks + 5), TimeUnit.MILLISECONDS);
            this.pauseTicks = 0;
        }
    }
}
