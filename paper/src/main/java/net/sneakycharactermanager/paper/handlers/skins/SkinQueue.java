package net.sneakycharactermanager.paper.handlers.skins;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.OfflinePlayer;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SkinQueue extends BukkitRunnable {

    public static final int PRIO_PRELOAD = 0;
    public static final int PRIO_ONLINE = 1;
    public static final int PRIO_LOAD = 2;
    public static final int PRIO_SKIN = 3;
    public static final int PRIO_UNIFORM = 4;

    private final ConcurrentMap<Integer, List<SkinData>> queue = new ConcurrentHashMap<>();
    private BukkitTask task = null;

    private int limit = 20;
    private int remaining = 20;
    private long nextReset = 0;
    private int delayMillis = 2000;
    private long lastRequestTime = 0;
    private long lastActionTime = 0; // Tracks when remaining last changed or nextReset was set
    private long lastDebugLog = 0;
    public final List<Player> preLoadedPlayers = new ArrayList<>();
    private boolean offlineSkinsRequested = false;

    public SkinQueue() {
        for (int i = 0; i <= PRIO_UNIFORM; i++) {
            queue.put(i, new CopyOnWriteArrayList<>());
        }
        
        // Initialize capacity from config
        this.limit = SneakyCharacterManager.getInstance().getConfig().getInt("mineskin.rate_limit_base", 20);
        this.remaining = this.limit;
        
        this.task = this.runTaskTimerAsynchronously(SneakyCharacterManager.getInstance(), 0, 5);
    }

    public void add(SkinData skinData, int priority) {
        queue.get(priority).add(skinData);
    }

    public void remove(SkinData skinData) {
        queue.values().forEach(list -> list.remove(skinData));
    }

    public void removeForPlayer(Player player) {
        queue.values().forEach(list -> list.removeIf(data -> data.getPlayer().equals(player)));
    }

    public void updatePriority(Player player, int newPriority) {
        boolean debug = SneakyCharacterManager.getInstance().getConfig().getBoolean("mineskin.debug", false);
        List<SkinData> toMove = new ArrayList<>();
        queue.values().forEach(list -> {
            Iterator<SkinData> it = list.iterator();
            while (it.hasNext()) {
                SkinData data = it.next();
                if (data.getPlayer().getUniqueId().equals(player.getUniqueId())) {
                    if (data.getPriority() < newPriority) {
                        toMove.add(data);
                        list.remove(data);
                    }
                }
            }
        });
        
        if (!toMove.isEmpty() && debug) {
            SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] Bulk moved " + toMove.size() + " items for " + player.getName() + " to P" + newPriority);
        }

        toMove.forEach(data -> {
            queue.get(newPriority).add(data);
            data.setPriority(newPriority);
        });
    }

    public void updatePriority(Player player, String characterUUID, int newPriority) {
        boolean debug = SneakyCharacterManager.getInstance().getConfig().getBoolean("mineskin.debug", false);
        queue.values().forEach(list -> {
            Optional<SkinData> dataOpt = list.stream()
                .filter(d -> d.getPlayer().getUniqueId().equals(player.getUniqueId()) && Objects.equals(d.getCharacterUUID(), characterUUID))
                .findFirst();
            if (dataOpt.isPresent()) {
                SkinData data = dataOpt.get();
                if (data.getPriority() < newPriority) {
                    if (debug) {
                        SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] Moving single item for " + player.getName() + " (Char: " + characterUUID + ") P" + data.getPriority() + " -> P" + newPriority);
                    }
                    list.remove(data);
                    queue.get(newPriority).add(data);
                    data.setPriority(newPriority);
                }
            }
        });
    }

    public void updateRateLimit(int limit, int remaining, long nextReset, int delayMillis) {
        boolean debug = SneakyCharacterManager.getInstance().getConfig().getBoolean("mineskin.debug", false);
        if (debug) {
            SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] Rate Limit Update: " + remaining + "/" + limit + " (Next Reset: " + nextReset + ")");
        }
        
        if (limit != -1) this.limit = limit;
        if (remaining != -1) this.remaining = remaining;
        if (nextReset != 0) this.nextReset = nextReset;
        if (delayMillis != -1) this.delayMillis = delayMillis;
        
        this.lastActionTime = System.currentTimeMillis();
    }


    public int getLimit()      { return limit; }
    public int getRemaining()  { return remaining; }
    public long getNextReset() { return nextReset; }
    public int getDelayMillis(){ return delayMillis; }

    @Override
    public void run() {
        if (this.task == null) return;

        long now = System.currentTimeMillis();
        
        // Handle reset
        if (nextReset != 0) {
            if (now > nextReset) {
                boolean debug = SneakyCharacterManager.getInstance().getConfig().getBoolean("mineskin.debug", false);
                if (debug) {
                    SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] Rate Limit Reset via timestamp (NextReset: 0)");
                }
                remaining = limit;
                nextReset = 0;
                lastActionTime = now;
            }
        } else if (remaining < limit) {
           // Fallback: If we've been idle for 60 seconds with no reset time known,
           // assume the rate limit has bucket-filled.
           if (now - lastActionTime > 60000) {
               boolean debug = SneakyCharacterManager.getInstance().getConfig().getBoolean("mineskin.debug", false);
               if (debug) {
                   SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] Idle for 60s with nextReset=0. Resetting capacity.");
               }
               remaining = limit;
               lastActionTime = now;
           }
        }


        if (remaining <= 0) return;

        // Get all skins currently being processed
        List<SkinData> inProgress = queue.values().stream()
            .flatMap(List::stream)
            .filter(d -> d.isProcessing())
            .toList();

        // Debug logging every 10 seconds
        boolean debug = SneakyCharacterManager.getInstance().getConfig().getBoolean("mineskin.debug", false);
        if (debug) {
            if (now - lastDebugLog > 10000) {
                lastDebugLog = now;
                StringBuilder sb = new StringBuilder("[SkinQueue Debug] ");
                sb.append("Capacity: ").append(remaining).append("/").append(limit).append(", ");
                sb.append("NextReset: ").append(nextReset == 0 ? "0" : (nextReset - now) / 1000).append("s, ");
                sb.append("Delay: ").append(delayMillis).append("ms, ");
                sb.append("Processing: ").append(inProgress.size()).append(", ");
                sb.append("Queued: [");
                for (int p = PRIO_UNIFORM; p >= PRIO_PRELOAD; p--) {
                    sb.append("P").append(p).append(":").append(queue.get(p).stream().filter(d -> !d.isProcessing()).count()).append(p > 0 ? ", " : "");
                }
                sb.append("]");
                SneakyCharacterManager.getInstance().getLogger().info(sb.toString());
                saveQueueJson();
            }
        }

        // 1. Priority: Poll existing jobs as fast as capacity allows (ignoring delayMillis)
        for (SkinData data : inProgress) {
            if (remaining <= 0) break;
            if (data.hasJobId() && now - data.getLastPollTime() > 10000) {
                if (debug) {
                    SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] Polling job " + data.getJobId() + " for " + data.getPlayer().getName());
                }
                // Polling an in-progress job (GET /v2/queue/:jobId) does not cost capacity
                // Run in a separate thread to avoid blocking the queue timer
                Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (t) -> data.run());
            }
        }

        if (remaining <= 0) return;
        if (now - lastRequestTime < delayMillis) {
            return;
        }

        // 2. Priority: Start new jobs if we have slots
        SkinData next = getNext();
        if (next != null) {
            boolean isInteraction = next.getPriority() >= PRIO_LOAD;
            long activeJobsCount = inProgress.stream().filter(d -> d.hasJobId()).count();
            
            if (activeJobsCount < 3 || (isInteraction && activeJobsCount < 10)) {
                if (debug) {
                    SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] Starting new job for " + next.getPlayer().getName() + " (P" + next.getPriority() + ") | Active: " + activeJobsCount);
                }
                next.processing = true;
                lastRequestTime = now;
                lastActionTime = now;
                // Only POST /v2/queue (new generation, skinUUID empty) costs rate limit capacity.
                // Fetch requests (GET /v2/skins/:uuid) are free.
                if (next.getSkinUUID().isEmpty()) remaining--;
                // Run in a separate thread to avoid blocking the queue timer
                Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (t) -> next.run());
            } else if (debug) {
                 if (now - lastDebugLog > 5000) { // Throttle debug log
                    SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] Stalling next job (P" + next.getPriority() + ") - Max active jobs reached (" + activeJobsCount + ")");
                }
            }
        }
    }

    private SkinData getNext() {
        int minReservation = SneakyCharacterManager.getInstance().getConfig().getInt("mineskin.min_capacity_reservation", 5);

        // Check P5 down to P2 normally (P2 is now unused but harmless to scan)
        for (int p = PRIO_UNIFORM; p > PRIO_ONLINE; p--) {
            List<SkinData> list = queue.get(p);
            if (list.isEmpty()) continue;
            for (SkinData data : list) {
                if (!data.processing) return data;
            }
        }

        // Low priority skins that have a UUID go first, and bypass the reservation (because they do not cost capacity)
        List<SkinData> p1 = queue.get(PRIO_ONLINE);
        // Menu-open pass
        for (SkinData data : p1) {
            if (!data.processing && data.isMenuOpen() && data.getSkinUUID() != null && !data.getSkinUUID().isEmpty()) return data;
        }
        // Normal P1 pass
        for (SkinData data : p1) {
            if (!data.processing && data.getSkinUUID() != null && !data.getSkinUUID().isEmpty()) return data;
        }

        // P0: only when above reservation
        for (SkinData data : queue.get(PRIO_PRELOAD)) {
            if (!data.processing && data.getSkinUUID() != null && !data.getSkinUUID().isEmpty()) return data;
        }

        // P1: first prefer entries whose menu is currently open (player has the UI open),
        // then fall back to normal P1 order. Only when we have capacity above the reservation.
        if (remaining > minReservation) {
            // Menu-open pass
            for (SkinData data : p1) {
                if (!data.processing && data.isMenuOpen()) return data;
            }
            // Normal P1 pass
            for (SkinData data : p1) {
                if (!data.processing) return data;
            }

            // P0: only when above reservation
            for (SkinData data : queue.get(PRIO_PRELOAD)) {
                if (!data.processing) return data;
            }
        }

        return null;
    }

    public Map<Integer, List<SkinData>> getQueue() {
        return queue;
    }

    private void saveQueueJson() {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("timestamp", System.currentTimeMillis());
        export.put("limit", limit);
        export.put("remaining", remaining);
        export.put("nextReset", nextReset);
        export.put("delayMillis", delayMillis);
        
        Map<String, List<Map<String, Object>>> priorities = new LinkedHashMap<>();
        for (int p = PRIO_UNIFORM; p >= PRIO_PRELOAD; p--) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (SkinData data : queue.get(p)) {
                list.add(data.toMap());
            }
            priorities.put("PRIO_" + p, list);
        }
        export.put("queue", priorities);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        File file = new File(SneakyCharacterManager.getInstance().getDataFolder(), "mineskin_queue.json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(export, writer);
        } catch (IOException e) {
            SneakyCharacterManager.getInstance().getLogger().warning("Failed to save mineskin_queue.json: " + e.getMessage());
        }
    }

    public void requestOfflineSkins(Player requester) {
        if (offlineSkinsRequested) return;
        offlineSkinsRequested = true;

        Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (task) -> {
            int days = SneakyCharacterManager.getInstance().getConfig().getInt("mineskin.preload_active_days", 8);
            long cutoff = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L);
            List<String> uuidsToPreload = new ArrayList<>();
            File dataFolder = SneakyCharacterManager.getCharacterDataFolder();

            for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                if (offlinePlayer.getLastPlayed() < cutoff) continue;
                
                File playerDir = new File(dataFolder, offlinePlayer.getUniqueId().toString());
                if (playerDir.exists() && playerDir.isDirectory()) {
                    String[] files = playerDir.list((dir, name) -> name.endsWith(".yml"));
                    if (files != null && files.length > 0) {
                        uuidsToPreload.add(offlinePlayer.getUniqueId().toString());
                    }
                }
            }

            if (!uuidsToPreload.isEmpty()) {
                Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
                    Player actualRequester = requester.isOnline() ? requester : Bukkit.getOnlinePlayers().stream().findAny().orElse(null);
                    if (actualRequester != null) {
                        BungeeMessagingUtil.sendByteArray(actualRequester, "preloadSkinsBulk", uuidsToPreload);
                    }
                });
            }
        });
    }

    public void preload(Player player) {
        if (this.preLoadedPlayers.contains(player)) return;
        preLoadedPlayers.add(player);

        BungeeMessagingUtil.sendByteArray(player, "preloadSkins", player.getUniqueId().toString());
    }

}
