package net.sneakycharactermanager.paper.storage;

import org.bukkit.entity.Player;
import net.sneakycharactermanager.paper.api.directory.CharacterDirectory;

/** Paper-side operations, independent of the proxy's YAML repository and wire framing. */
public interface CharacterBackend extends AutoCloseable {
    void submit(Player requester, CharacterRequest request);
    void disconnect(Player player);
    /** Read-only lookups served by this backend. */
    CharacterDirectory directory();
    @Override void close();
}
