package net.sneakycharactermanager.paper.api.voice;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Decides whose nameplate shows as talking for a Simple Voice Chat microphone packet. Without one, SCM lights up the
 * packet's sender. A plugin that plays a player's voice from another body (another player) registers one so the
 * nameplate follows the voice.
 *
 * <p>Register it in Bukkit's {@link org.bukkit.plugin.ServicesManager}, on the main thread:
 *
 * <pre>{@code
 * Bukkit.getServicesManager().register(TalkingNameplateLookup.class,
 *         (speaker, payload) -> isRouted(speaker, payload) ? bodyOf(speaker) : speaker,
 *         myPlugin, ServicePriority.Normal);
 * }</pre>
 *
 * SCM uses the highest-priority registration and stops using it once it is unregistered, which Bukkit does when the
 * registering plugin disables.
 *
 * <p>SCM asks before any other voice plugin has changed or cancelled the packet, so the payload is exactly what the
 * sender's client sent. SCM only asks about packets that carry audio. Simple Voice Chat ends each talk with an empty
 * packet, which can't say which body it belongs to, so while a lookup is registered that packet lights up no
 * nameplate. The plate still turns off half a second after the last audio packet.
 */
@FunctionalInterface
public interface TalkingNameplateLookup {

    /**
     * Called on Simple Voice Chat's network thread for every audio packet, so it must be fast, thread-safe and
     * non-blocking, and must not touch Bukkit world state.
     *
     * @param speaker the packet's sender
     * @param payload the packet's Opus payload, read-only, from position 0 to its limit, never empty. Valid only during
     *                the call.
     * @return the player whose nameplate lights up: {@code speaker} (or null) for the sender's own, or another player.
     *         Nothing lights up while the returned player is offline. If the lookup throws, SCM uses the sender.
     */
    UUID bodyFor(UUID speaker, ByteBuffer payload);
}
