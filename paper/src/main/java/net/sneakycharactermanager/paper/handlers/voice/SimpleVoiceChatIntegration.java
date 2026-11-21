package net.sneakycharactermanager.paper.handlers.voice;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import net.sneakycharactermanager.paper.SneakyCharacterManager;

/**
 * Simple Voice Chat integration: toggles talking nameplates based on incoming voice packets. A registered
 * {@link net.sneakycharactermanager.paper.api.voice.TalkingNameplateLookup} picks whose nameplate a packet lights up.
 */
public class SimpleVoiceChatIntegration implements VoicechatPlugin {

    private static final int TALKING_TIMEOUT_TICKS = 10; // 0.5 seconds of inactivity to stop talking
    private static final long NANOS_PER_TICK = 50_000_000L;
    private static final long TALKING_TIMEOUT_NANOS = TALKING_TIMEOUT_TICKS * NANOS_PER_TICK;
    // Must run before plugins like SneakyVoiceChat (priority 100) that cancel this event;
    // SVC stops dispatching to lower-priority listeners once cancelled.
    private static final int MIC_EVENT_PRIORITY = 101;

    // When each talking nameplate last heard a packet; a key is present exactly while its plate is lit. Written by the
    // voice thread (packets) and the main thread (expiry), so every transition is one atomic map operation.
    private final Map<UUID, Long> lastHeard = new ConcurrentHashMap<>();
    private final TalkingBodies bodies;
    private final LongSupplier clock;

    public SimpleVoiceChatIntegration(TalkingBodies bodies) {
        this(bodies, System::nanoTime);
    }

    /** {@code clock} returns nanoseconds and is read from the voice thread. */
    SimpleVoiceChatIntegration(TalkingBodies bodies, LongSupplier clock) {
        this.bodies = bodies;
        this.clock = clock;
    }

    @Override
    public String getPluginId() {
        return "sneakycharactermanager";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, onMicPacket(), MIC_EVENT_PRIORITY);
    }

    private Consumer<MicrophonePacketEvent> onMicPacket() {
        return event -> {
            // Use INFO so we can confirm packets are coming through without changing the global log level
            UUID uuid = resolveSender(event);
            if (uuid == null) {
                SneakyCharacterManager.getInstance().getLogger().info("[SVC] Could not resolve speaker for MicrophonePacketEvent");
                return;
            }
            // This listener runs before SneakyVoiceChat (100) cancels the event, so the lookup sees the sent payload.
            if (bodies.active()) uuid = bodies.bodyFor(uuid, event.getPacket().getOpusEncodedData());
            if (uuid != null) handleTalking(uuid);
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

    /**
     * Voice thread, once per packet. Only records the time: the scheduler is touched when a nameplate starts talking,
     * never per packet, because each cancel queues a scan of the server's pending tasks on the main thread.
     */
    private void handleTalking(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        if (lastHeard.put(uuid, clock.getAsLong()) == null) {
            SneakyCharacterManager.getInstance().nametagManager.setTalking(player, true);
            scheduleExpiry(uuid, TALKING_TIMEOUT_TICKS);
        }
    }

    private void scheduleExpiry(UUID uuid, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(SneakyCharacterManager.getInstance(), () -> expire(uuid), delayTicks);
    }

    /**
     * Main thread. Turns the nameplate off once no packet arrived for the timeout; otherwise checks again when the rest
     * of it has passed. Delays are converted to ticks at 20 TPS, so under lag a check runs late: a single packet at
     * 10 TPS keeps the plate lit for about a second, as the old fixed 10-tick timer did.
     */
    private void expire(UUID uuid) {
        while (true) {
            Long heard = lastHeard.get(uuid);
            if (heard == null) return; // Only expiry removes keys, and one expiry runs per lit plate.
            long remainingNanos = TALKING_TIMEOUT_NANOS - (clock.getAsLong() - heard);
            if (remainingNanos > 0) {
                scheduleExpiry(uuid, Math.max(1, (remainingNanos + NANOS_PER_TICK - 1) / NANOS_PER_TICK));
                return;
            }
            // Fails when a packet arrived since the read; that packet saw the key, started nothing, and is rechecked.
            if (lastHeard.remove(uuid, heard)) break;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) SneakyCharacterManager.getInstance().nametagManager.setTalking(player, false);
    }

}

