package net.sneakycharactermanager.paper.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.api.character.StorageMode;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.CharacterSelectionMenu;
import net.sneakycharactermanager.paper.handlers.nametags.NametagManager;
import net.sneakycharactermanager.paper.handlers.skins.SkinCache;
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;
import net.sneakycharactermanager.paper.handlers.skins.SkinStateManager;
import net.sneakycharactermanager.paper.util.SkinUtil;

/** {@link DatabaseBackend#loadBorrowed} needs an online body: without one there is no session to fence the result to. */
class BorrowOfflineBodyTest {
    static final String SNAPSHOT = "o.last_played_character, o.revision, c.*";

    @TempDir Path data;
    final UUID ownerId = UUID.randomUUID();
    final UUID bodyId = UUID.randomUUID();
    final CharacterKey borrowed = new CharacterKey(ownerId, "shared-id");
    final Player body = mock(Player.class);
    final SneakyCharacterManager plugin = mock(SneakyCharacterManager.class);
    final Database api = new Database();
    DatabaseBackend backend;
    MockedStatic<SneakyCharacterManager> plugins;
    MockedStatic<Bukkit> bukkit;
    MockedStatic<SkinUtil> skins;
    MockedStatic<SkinCache> cache;

    /** Fake SneakyDB: answers the owner's snapshot, or holds it open so the test decides when it arrives. */
    static class Database extends SqlStoreTest.Api {
        final Map<String, List<Map<String,Object>>> owners = new HashMap<>();
        boolean hold;
        CompletableFuture<List<Map<String,Object>>> held;
        @Override public CompletableFuture<List<Map<String,Object>>> query(String sql, List<?> args) {
            if (sql.contains(SNAPSHOT)) {
                statements.add(sql); bindings.add(args);
                List<Map<String,Object>> rows = owners.getOrDefault((String) args.getFirst(), List.of());
                if (!hold) return CompletableFuture.completedFuture(rows);
                held = new CompletableFuture<>();
                return held.thenApply(unused -> rows);
            }
            if (sql.startsWith("SELECT * FROM scm.uniform_variants")) return CompletableFuture.completedFuture(List.of());
            return super.query(sql, args);
        }
        boolean any(String fragment) { return statements.stream().anyMatch(s -> s.contains(fragment)); }
    }

    @BeforeEach
    void setUp() throws Exception {
        api.ready.complete(null);
        api.owners.put(ownerId.toString(), List.of(row("shared-id", "Owner's Character")));
        plugins = mockStatic(SneakyCharacterManager.class);
        bukkit = mockStatic(Bukkit.class);
        skins = mockStatic(SkinUtil.class);
        cache = mockStatic(SkinCache.class, CALLS_REAL_METHODS);
        plugins.when(SneakyCharacterManager::getInstance).thenReturn(plugin);
        plugins.when(SneakyCharacterManager::getCharacterDataFolder).thenReturn(data.toFile());
        YamlConfiguration config = new YamlConfiguration();
        config.set("manageInventories", false);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getStorageMode()).thenReturn(StorageMode.SNEAKYDB);
        when(plugin.writeFile(any(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        plugin.skinStateManager = new SkinStateManager();
        plugin.skinQueue = mock(SkinQueue.class);
        plugin.nametagManager = mock(NametagManager.class);
        plugin.selectionMenu = mock(CharacterSelectionMenu.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            ((Runnable) call.getArgument(1)).run();
            return null;
        });
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        PluginManager events = mock(PluginManager.class);
        Server server = mock(Server.class);
        when(server.getPluginManager()).thenReturn(events);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(Bukkit::getPluginManager).thenReturn(events);
        when(body.getUniqueId()).thenReturn(bodyId);
        when(body.getName()).thenReturn("Supporter");
        when(body.isOnline()).thenReturn(true);
        when(body.getLocation()).thenReturn(new Location(null, 0, 0, 0));
        gameplayFile(borrowed, 40);
        backend = new DatabaseBackend(plugin, new SqlStore(api, 5));
        when(plugin.getCharacterBackend()).thenReturn(backend);
    }

    @AfterEach
    void tearDown() {
        backend.close();
        Character.remove(body);
        cache.close(); skins.close(); bukkit.close(); plugins.close();
    }

    /** Guards: a borrow for an offline body was submitted unfenced, so its result could land on the body's next session. */
    @Test
    void borrowForAnOfflineBodySubmitsNothingAndLoadsNothingOnRejoin() {
        api.hold = true;
        bukkit.when(() -> Bukkit.getPlayer(bodyId)).thenReturn(null); // offline when the borrow is asked for

        backend.loadBorrowed(body, borrowed);

        assertFalse(api.any(SNAPSHOT), "no snapshot query may reach the database");
        assertNull(api.held);

        // The body comes back (a new session) before any late result could arrive: nothing must be put on it.
        bukkit.when(() -> Bukkit.getPlayer(bodyId)).thenReturn(body);
        if (api.held != null) api.held.complete(null);

        assertNull(Character.get(body));
        verify(body, never()).teleport(any(Location.class));
        verifyNoInteractions(plugin.selectionMenu);
    }

    /** Control for the test above: the same fixture with the body online does read and load the owner's character. */
    @Test
    void borrowForAnOnlineBodyStillLoadsTheOwnersCharacter() {
        bukkit.when(() -> Bukkit.getPlayer(bodyId)).thenReturn(body);

        backend.loadBorrowed(body, borrowed);

        assertTrue(api.any(SNAPSHOT));
        assertNotNull(Character.get(body));
        assertEquals(borrowed, Character.get(body).getKey());
    }

    private static Map<String,Object> row(String id, String name) {
        Map<String,Object> row = new HashMap<>();
        row.put("last_played_character", id);
        row.put("revision", 0L);
        row.put("character_id", id);
        row.put("enabled", true);
        row.put("name", name);
        row.put("skin", "");
        row.put("texture", "stored-texture");
        row.put("signature", "stored-signature");
        return row;
    }

    private void gameplayFile(CharacterKey key, double x) throws Exception {
        File file = data.resolve(key.owner().toString()).resolve(key.characterId() + ".yml").toFile();
        Files.createDirectories(file.getParentFile().toPath());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("location", new Location(null, x, 64, 0));
        yaml.save(file);
    }
}
