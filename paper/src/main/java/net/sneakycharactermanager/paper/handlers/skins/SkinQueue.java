package net.sneakycharactermanager.paper.handlers.skins;

import java.util.*;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import net.sneakycharactermanager.paper.SneakyCharacterManager;

public class SkinQueue {
    private static Map<Integer, List<SkinData>> queue = new LinkedHashMap<Integer, List<SkinData>>();
    private static List<SkinData> processing = new ArrayList<SkinData>();
    private static boolean running = false;

    public static void add(SkinData skinData, int priority) {
        if (queue.containsKey(priority)) {
            queue.get(priority).add(skinData);
        } else {
            queue.put(priority, new ArrayList<SkinData>(Arrays.asList(new SkinData[]{skinData})));
        }
    }

    public static void remove(SkinData skinData) {
        processing.remove(skinData);
        for (Entry<Integer, List<SkinData>> entry : queue.entrySet()) {
            entry.getValue().remove(skinData);
        }
    }

    private static List<SkinData> getNext(int amount) {
        List<SkinData> next = new ArrayList<SkinData>();

        while (next.size() < amount) {
            int highestPriority = -1;

            for (Entry<Integer, List<SkinData>> entry : queue.entrySet()) {
                if (entry.getKey() < highestPriority) continue;

                for (SkinData skinData : entry.getValue()) {
                    if (!processing.contains(skinData)) {
                        highestPriority = entry.getKey();
                        break;
                    }
                }
            }

            if (highestPriority == -1) break;

            for (SkinData skinData : queue.get(highestPriority)) {
                if (!next.contains(skinData)) {
                    next.add(skinData);
                    processing.add(skinData);
                    break;
                }
            }
        }

        return next;
    }

    public static void start() {
        if (running) return;
        running = true;
        run(getNext(5));
    }

    private static void run(List<SkinData> list) {
        if (processing.size() < 1) {
            running = false;
            return;
        };

        for (SkinData skinData : list) {
            Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (s) ->{
                skinData.convertSkinURL();
                Bukkit.getScheduler().runTaskLater(SneakyCharacterManager.getInstance(), () ->{
                    if (skinData.isProcessed()) {
                        remove(skinData);
                    } else {
                        processing.remove(skinData);
                    }
                    run(getNext(1));
                }, 0);
            });
        }
    }
    
}
