package net.sneakycharactermanager.paper.storage;

import org.bukkit.entity.Player;

/** Paper-side operations, independent of the proxy's YAML repository and wire framing. */
public interface CharacterBackend extends AutoCloseable {
    void submit(Player requester, CharacterRequest request);
    void disconnect(Player player);
    @Override void close();
}
