package net.sneakycharactermanager.paper.handlers.voice;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;

import net.sneakycharactermanager.paper.api.voice.TalkingNameplateLookup;

/**
 * Picks whose nameplate a voice packet lights up, through the registered {@link TalkingNameplateLookup}. The lookup is
 * cached from service events, so a packet with no lookup registered costs one volatile read.
 */
public final class TalkingBodies implements Listener {

    private final ServicesManager services;
    private final Logger logger;
    private volatile TalkingNameplateLookup lookup;
    // The lookup that last threw, so a broken lookup logs once instead of on every packet. Only the latest is kept:
    // switching between two broken lookups logs again, which is rare and bounded by registrations.
    private volatile TalkingNameplateLookup failed;

    public TalkingBodies(ServicesManager services, Logger logger) {
        this.services = services;
        this.logger = logger;
    }

    /** Listens for lookup changes, then loads the current lookup, so no change can fall between the two. */
    public void start(PluginManager plugins, Plugin plugin) {
        plugins.registerEvents(this, plugin);
        refresh();
    }

    /** True when a lookup is registered. Without one, every packet lights up its sender. */
    boolean active() {
        return lookup != null;
    }

    /**
     * Voice thread. Whose nameplate this packet lights up, or null for none (an end-of-talk packet while a lookup is
     * registered). Callers skip this when {@link #active()} is false, so the payload is only read with a lookup.
     */
    UUID bodyFor(UUID speaker, byte[] payload) {
        TalkingNameplateLookup current = lookup;
        if (current == null) return speaker;
        if (payload.length == 0) return null;
        try {
            UUID body = current.bodyFor(speaker, ByteBuffer.wrap(payload).asReadOnlyBuffer());
            return body == null ? speaker : body;
        } catch (RuntimeException | LinkageError failure) {
            if (failed != current) {
                failed = current;
                logger.log(Level.WARNING, "[SVC] Talking nameplate lookup " + current.getClass().getName()
                        + " failed; lighting up the sender instead. Repeated failures are not logged.", failure);
            }
            return speaker;
        }
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent event) {
        refreshIfLookup(event);
    }

    @EventHandler
    public void onServiceUnregister(ServiceUnregisterEvent event) {
        refreshIfLookup(event);
    }

    private void refreshIfLookup(ServiceEvent event) {
        if (event.getProvider().getService() == TalkingNameplateLookup.class) refresh();
    }

    // Bukkit fires service events after the change, so each refresh reads a state at least as new as its event.
    synchronized void refresh() {
        lookup = services.load(TalkingNameplateLookup.class);
    }
}
