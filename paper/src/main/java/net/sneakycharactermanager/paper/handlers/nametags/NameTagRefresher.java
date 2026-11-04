package net.sneakycharactermanager.paper.handlers.nametags;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import net.sneakycharactermanager.paper.SneakyCharacterManager;

/**
 * Once a second: removes plates of owners who became suppressed (vanished) and rebuilds them when that ends, keeps
 * plates on their owner/anchor, re-applies per-viewer visibility, and sends text to viewers who newly track a plate.
 */
public class NameTagRefresher extends BukkitRunnable {

    /** Viewers that already received the text of one specific display. */
    record Seen(UUID displayId, Set<UUID> viewers) {}

    public BukkitTask task = null;
    private final NametagManager manager;
    private final Map<UUID, Seen> seen = new HashMap<>();

    public NameTagRefresher(SneakyCharacterManager plugin, NametagManager manager) {
        this.manager = manager;
        this.task = runTaskTimer(plugin, 0, 20);
    }

    NameTagRefresher(NametagManager manager) {
        this.manager = manager;
    }

    @Override
    public void run() {
        Set<UUID> online = new HashSet<>();
        for (Player owner : List.copyOf(Bukkit.getOnlinePlayers())) {
            online.add(owner.getUniqueId());
            refresh(owner);
        }
        seen.keySet().retainAll(online);
    }

    private void refresh(Player owner) {
        UUID id = owner.getUniqueId();
        if (manager.isSuppressed(owner)) {
            manager.suppress(owner);
            seen.remove(id);
            return;
        }
        if (manager.getNickname(owner) == null) {
            manager.restoreIfUnsuppressed(owner);
            seen.remove(id);
            return;
        }

        manager.ensurePlacement(owner);
        Nickname name = manager.getNickname(owner);
        if (name == null) { // ensurePlacement scheduled a rebuild
            seen.remove(id);
            return;
        }

        UUID displayId = name.display().getUniqueId();
        Seen previous = seen.get(id);
        Set<UUID> alreadySent = previous != null && previous.displayId().equals(displayId) ? previous.viewers() : Set.of();
        seen.put(id, new Seen(displayId, manager.refreshNicknames(owner, name, alreadySent)));
    }

    Seen seen(UUID owner) {
        return seen.get(owner);
    }

    int seenSize() {
        return seen.size();
    }
}
