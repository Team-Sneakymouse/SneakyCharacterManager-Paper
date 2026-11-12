package net.sneakycharactermanager.paper.api.directory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;

/**
 * Looks up characters whether or not they are loaded, by account or by id. Read-only. Registered in Bukkit's
 * {@link org.bukkit.plugin.ServicesManager} once SCM has enabled; Bukkit unregisters it when SCM disables.
 * <pre>{@code
 * CharacterDirectory.get().ifPresent(directory -> directory.charactersOf(accountUuid).thenAccept(characters ->
 *         Bukkit.getScheduler().runTask(myPlugin, () -> showCharacters(player, characters))));
 * }</pre>
 * <p>Methods may be called from any thread. Futures complete on an arbitrary thread (a database worker or the server
 * main thread); hop to the main thread yourself before touching Bukkit state. Each call reads the store again; nothing
 * is cached, so cache on your side for frequent callers such as tab completion.
 * <p>Both storage backends are supported:
 * <ul>
 *   <li><b>SneakyDB:</b> a query on {@code scm.characters}.</li>
 *   <li><b>Legacy proxy:</b> a plugin-message round trip to the proxy, which reads its account files. Plugin messages
 *   travel over a player connection, so a lookup fails right away with {@link IllegalStateException} while no player
 *   is online on this server. A lookup that gets no reply (for example from a proxy that predates this API) fails
 *   with {@link java.util.concurrent.TimeoutException} after a few seconds.</li>
 * </ul>
 * <p>Results reflect the store at the time of the call, including renames made on other servers or by admins.
 * Ids that are not UUIDs (template characters) are never returned.
 */
public interface CharacterDirectory {

    /** The account's enabled characters, in character menu order. Empty when the account has none or is unknown. */
    CompletableFuture<List<CharacterInfo>> charactersOf(UUID account);

    /** One character by id, disabled characters included; empty when no such character exists. */
    CompletableFuture<Optional<CharacterInfo>> character(UUID characterId);

    /**
     * The registered service, or empty when SCM is not enabled. Callers that only compile against SCM must still
     * guard against {@link LinkageError} when SCM is not installed.
     */
    static Optional<CharacterDirectory> get() {
        return Optional.ofNullable(Bukkit.getServicesManager().load(CharacterDirectory.class));
    }
}
