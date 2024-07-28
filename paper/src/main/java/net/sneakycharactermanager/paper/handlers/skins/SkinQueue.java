package net.sneakycharactermanager.paper.handlers.skins;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.sneakycharactermanager.paper.SneakyCharacterManager;

public class SkinQueue {

    private ConcurrentMap<Integer, List<SkinData>> queue = new ConcurrentHashMap<>();
    private ScheduledTask task = null;

    public void add(SkinData skinData, int priority) {
        Bukkit.getScheduler().runTaskAsynchronously(SneakyCharacterManager.getInstance(), () -> {
            if (priority > 1) {
                List<SkinData> toCancel = new ArrayList<>();
                queue.entrySet().stream()
                    .filter(entry -> entry.getKey() > 1)
                    .forEach(entry -> 
                        entry.getValue().stream()
                            .filter(data -> skinData.getPlayer().equals(data.getPlayer()))
                            .forEach(toCancel::add)
                    );
                toCancel.forEach(SkinData::cancel);
            }
            
            queue.computeIfAbsent(priority, k -> new CopyOnWriteArrayList<>()).add(skinData);
            start();
        });
    }    

    public void remove(SkinData skinData) {
        Bukkit.getScheduler().runTaskAsynchronously(SneakyCharacterManager.getInstance(), () -> {
            queue.values().forEach(list -> list.removeIf(s -> s.equals(skinData)));
        });
    }    

    private synchronized SkinData getNext() {
        Optional<Entry<Integer, List<SkinData>>> maxEntry = this.queue.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .max(Comparator.comparingInt(Entry::getKey));

        return maxEntry.map(entry -> entry.getValue().get(0)).orElse(null);
    }

    private synchronized void start() {
        if (this.task != null)
            return;
        this.task = Bukkit.getAsyncScheduler().runDelayed(SneakyCharacterManager.getInstance(), (s) -> {
            this.run();
        }, 50, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (this.task == null)
            return;
        this.task.cancel();
        this.task = null;
    }

    public synchronized void run() {
        if (this.task == null)
            return;
        SkinData next = this.getNext();

        if (next == null) {
            this.stop();
        } else {
            boolean tooManyRequests = next.convertSkinURL();
            if (next.isProcessed()) {
                if (next.isValid()) {
                    Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
                        next.apply();
                    });
                }
                Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
                    next.remove();
                });
            }
            this.task = Bukkit.getAsyncScheduler().runDelayed(SneakyCharacterManager.getInstance(), (s) -> {
                this.run();
            }, 50 * (tooManyRequests ? 45 : 5), TimeUnit.MILLISECONDS);
        }
    }
}
