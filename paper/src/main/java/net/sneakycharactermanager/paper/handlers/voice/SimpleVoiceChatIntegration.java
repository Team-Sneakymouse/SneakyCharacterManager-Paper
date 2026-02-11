package net.sneakycharactermanager.paper.handlers.voice;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import net.sneakycharactermanager.paper.SneakyCharacterManager;

/**
 * Simple Voice Chat integration: toggles talking nameplates based on incoming voice packets.
 */
public class SimpleVoiceChatIntegration implements VoicechatPlugin {

    private final Map<UUID, BukkitTask> resetTasks = new HashMap<>();
    private final Set<UUID> talking = new HashSet<>();
    private static final int TALKING_TIMEOUT_TICKS = 10; // 0.5 seconds of inactivity to stop talking

    @Override
    public String getPluginId() {
        return "sneakycharactermanager";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, onMicPacket(), 0);
    }

    private Consumer<MicrophonePacketEvent> onMicPacket() {
        return event -> {
            // Use INFO so we can confirm packets are coming through without changing the global log level
            UUID uuid = resolveSender(event);
            if (uuid != null) {
                handleTalking(uuid);
            } else {
                SneakyCharacterManager.getInstance().getLogger().info("[SVC] Could not resolve speaker for MicrophonePacketEvent");
            }
        };
    }

    private UUID resolveSender(MicrophonePacketEvent event) {
        try {
            var conn = event.getSenderConnection();
            if (conn != null && conn.getPlayer() != null) return conn.getPlayer().getUuid();
        } catch (Throwable ignored) {}
        try {
            var senderMethod = event.getClass().getMethod("getSender");
            Object sender = senderMethod.invoke(event);
            if (sender != null) {
                var uuidMethod = sender.getClass().getMethod("getUuid");
                Object uuidObj = uuidMethod.invoke(sender);
                if (uuidObj instanceof UUID) return (UUID) uuidObj;
            }
        } catch (Throwable ignored) {}
        SneakyCharacterManager.getInstance().getLogger().fine("[SVC] resolveSender: no sender found");
        return null;
    }

    private void handleTalking(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        // Only send the "start talking" update on transition from silent -> talking
        if (talking.add(uuid)) {
            SneakyCharacterManager.getInstance().nametagManager.setTalking(player, true);
        }

        BukkitTask prior = resetTasks.remove(uuid);
        if (prior != null) prior.cancel();

        BukkitTask task = Bukkit.getScheduler().runTaskLater(SneakyCharacterManager.getInstance(), () -> {
            Player p = Bukkit.getPlayer(uuid);
            // If no packet refreshed the timer, mark as not talking and clear state
            if (p != null && talking.remove(uuid)) {
                SneakyCharacterManager.getInstance().nametagManager.setTalking(p, false);
            }
            resetTasks.remove(uuid);
        }, TALKING_TIMEOUT_TICKS);
        resetTasks.put(uuid, task);
    }

}

