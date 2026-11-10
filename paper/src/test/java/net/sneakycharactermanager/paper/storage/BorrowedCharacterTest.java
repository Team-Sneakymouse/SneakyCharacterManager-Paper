package net.sneakycharactermanager.paper.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import net.sneakycharactermanager.paper.api.character.PuppetLeaveEvent;
import net.sneakycharactermanager.paper.api.character.PuppetResult;
import net.sneakycharactermanager.paper.api.character.Puppets;
import net.sneakycharactermanager.paper.api.character.StorageMode;
import net.sneakycharactermanager.paper.commands.CommandNick;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.CharacterLoader;
import net.sneakycharactermanager.paper.handlers.character.CharacterSelectionMenu;
import net.sneakycharactermanager.paper.handlers.nametags.NametagManager;
import net.sneakycharactermanager.paper.handlers.skins.SkinCache;
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;
import net.sneakycharactermanager.paper.handlers.skins.SkinStateManager;
import net.sneakycharactermanager.paper.util.SkinUtil;

/**
 * End to end on the SneakyDB path, through the public {@link Puppets#puppet}: player Y wears offline owner X's character, and
 * the load, {@code /nick}, a skin change (also one that resolves after Y switched) and the save all land on X's record.
 */
class BorrowedCharacterTest {
    static final String SKIN_URL = "https://skins/new.png";
    static final String TEXTURE_URL = "http://textures.minecraft.net/texture/new";

    @TempDir Path data;
    final UUID ownerId = UUID.randomUUID();
    final UUID bodyId = UUID.randomUUID();
    final CharacterKey borrowed = new CharacterKey(ownerId, "shared-id");
    final CharacterKey bodysOwn = new CharacterKey(bodyId, "shared-id"); // same ID, other owner
    final Player body = mock(Player.class);
    final SneakyCharacterManager plugin = mock(SneakyCharacterManager.class);
    final Database api = new Database();
    final List<Path> saves = new ArrayList<>();
    DatabaseBackend backend;
    MockedStatic<SneakyCharacterManager> plugins;
    MockedStatic<Bukkit> bukkit;
    MockedStatic<SkinUtil> skins;
    MockedStatic<SkinCache> cache;

    /** Fake SneakyDB: answers snapshots per owner and records every write with its bound values. */
    static class Database extends SqlStoreTest.Api {
        final Map<String, List<Map<String,Object>>> owners = new HashMap<>();
        @Override public CompletableFuture<List<Map<String,Object>>> query(String sql, List<?> args) {
            if (sql.contains("o.last_played_character, o.revision, c.*")) {
                statements.add(sql); bindings.add(args);
                return CompletableFuture.completedFuture(owners.getOrDefault((String) args.getFirst(), List.of()));
            }
            if (sql.startsWith("SELECT * FROM scm.uniform_variants")) return CompletableFuture.completedFuture(List.of());
            return super.query(sql, args);
        }
        /** Character edits and selects. Leaves out ensureOwner, which every edit runs and is a no-op for an existing owner. */
        List<List<?>> writes() {
            List<List<?>> writes = new ArrayList<>();
            for (int i = 0; i < statements.size(); i++) if (statements.get(i).startsWith("WITH locked")
                    || statements.get(i).contains("SET last_played_character")) writes.add(bindings.get(i));
            return writes;
        }
        boolean any(String fragment) { return statements.stream().anyMatch(s -> s.contains(fragment)); }
    }

    @BeforeEach
    void setUp() throws Exception {
        api.ready.complete(null);
        api.owners.put(ownerId.toString(), List.of(row("shared-id", "Owner's Character")));
        api.owners.put(bodyId.toString(), List.of(row("shared-id", "Body's Own")));
        plugins = mockStatic(SneakyCharacterManager.class);
        bukkit = mockStatic(Bukkit.class);
        skins = mockStatic(SkinUtil.class);
        cache = mockStatic(SkinCache.class, CALLS_REAL_METHODS);
        plugins.when(SneakyCharacterManager::getInstance).thenReturn(plugin);
        plugins.when(SneakyCharacterManager::getCharacterDataFolder).thenReturn(data.toFile());
        skins.when(() -> SkinUtil.getTextureUrl(any())).thenReturn(TEXTURE_URL);
        YamlConfiguration config = new YamlConfiguration();
        config.set("manageInventories", false);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getStorageMode()).thenReturn(StorageMode.SNEAKYDB);
        when(plugin.writeFile(any(), anyString())).thenAnswer(call -> {
            saves.add(call.getArgument(0));
            return CompletableFuture.completedFuture(null);
        });
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
        bukkit.when(() -> Bukkit.getPlayer(bodyId)).thenReturn(body);
        when(body.getUniqueId()).thenReturn(bodyId);
        when(body.getName()).thenReturn("Supporter");
        when(body.isOnline()).thenReturn(true);
        when(body.getLocation()).thenReturn(new Location(null, 0, 0, 0));
        when(body.hasPermission("sneakycharacters.admin.bypass.characterlocks")).thenReturn(true);
        gameplayFile(borrowed, 40);
        gameplayFile(bodysOwn, 10);
        backend = new DatabaseBackend(plugin, new SqlStore(api, 5));
        when(plugin.getCharacterBackend()).thenReturn(backend);
    }

    @AfterEach
    void tearDown() {
        backend.close();
        Character.remove(body, PuppetLeaveEvent.Reason.QUIT);
        cache.close(); skins.close(); bukkit.close(); plugins.close();
    }

    @Test
    void loadPutsTheOwnersCharacterOnTheBodyWithoutTouchingLastPlayed() {
        assertEquals(PuppetResult.PUPPETED, Puppets.puppet(body, borrowed).join());

        Character worn = Character.get(body);
        assertNotNull(worn);
        assertEquals(borrowed, worn.getKey());
        assertSame(body, worn.getPlayer());
        assertEquals("Owner's Character", worn.getName());
        verify(body).teleport(new Location(null, 40, 65, 0)); // the owner's gameplay file
        assertFalse(api.any("last_played_character = ?"), "a borrow must not select for anyone");
        assertFalse(api.any("INSERT INTO scm.owners"), "a borrow must not create its owner");
        assertEquals(List.of(), api.writes());
    }

    @Test
    void nickAndSaveLandOnTheOwnersRecord() {
        assertEquals(PuppetResult.PUPPETED, Puppets.puppet(body, borrowed).join());

        assertTrue(new CommandNick().execute(body, "nick", new String[]{"New", "Name"}));
        Character.get(body).save();

        assertEquals(List.of(List.of(ownerId.toString(), "New Name", "shared-id")), api.writes());
        assertEquals(List.of(data.resolve(ownerId.toString()).resolve("shared-id.yml")), saves);
    }

    @Test
    void skinChangeLandsOnTheOwnersRecord() {
        CompletableFuture<SkinCache.ResolveResult> resolve = new CompletableFuture<>();
        cache.when(() -> SkinCache.resolve(body, SKIN_URL, false)).thenReturn(resolve);
        assertEquals(PuppetResult.PUPPETED, Puppets.puppet(body, borrowed).join());

        CharacterLoader.updateSkin(body, borrowed, SKIN_URL, false, null);
        resolve.complete(hit());

        assertEquals(List.of(skinWrite(ownerId)), api.writes());
        assertEquals(TEXTURE_URL, Character.get(body).getSkin());
    }

    @Test
    void skinResolvingAfterTheBodySwitchedStillLandsOnlyOnTheOwnersRecord() {
        CompletableFuture<SkinCache.ResolveResult> resolve = new CompletableFuture<>();
        cache.when(() -> SkinCache.resolve(body, SKIN_URL, false)).thenReturn(resolve);
        assertEquals(PuppetResult.PUPPETED, Puppets.puppet(body, borrowed).join());
        CharacterLoader.updateSkin(body, borrowed, SKIN_URL, false, null);

        backend.submit(body, new CharacterRequest.Select(bodyId.toString(), "shared-id")); // back to its own, same ID
        assertEquals(bodysOwn, Character.get(body).getKey());
        assertEquals(List.of(data.resolve(ownerId.toString()).resolve("shared-id.yml")), saves); // switching saved X's file
        api.statements.clear(); api.bindings.clear();

        resolve.complete(hit());

        assertEquals(List.of(skinWrite(ownerId)), api.writes());
        assertEquals("Body's Own", Character.get(body).getName());
        assertNotEquals(TEXTURE_URL, Character.get(body).getSkin());
        skins.verify(() -> SkinUtil.applySkin(eq(body), argThat(p -> p.getValue().equals("new-texture"))), never());
    }

    @Test
    void missingCharacterLoadsNothing() {
        assertEquals(PuppetResult.NOT_FOUND, Puppets.puppet(body, new CharacterKey(ownerId, "gone")).join());

        assertNull(Character.get(body));
        verifyNoInteractions(plugin.selectionMenu);
        assertEquals(List.of(), api.writes());
    }

    @Test
    void ownCharactersStillLoadAndSaveAsBefore() {
        backend.submit(body, new CharacterRequest.Select(bodyId.toString(), "shared-id"));
        Character.get(body).save();

        assertEquals(bodysOwn, Character.get(body).getKey());
        verify(body).teleport(new Location(null, 10, 65, 0));
        assertEquals(List.of(data.resolve(bodyId.toString()).resolve("shared-id.yml")), saves);
        assertTrue(api.any("last_played_character = ?"));
    }

    private List<Object> skinWrite(UUID owner) {
        return List.of(owner.toString(), "skin-id", "", "", "", "", false, "shared-id");
    }

    private static SkinCache.ResolveResult hit() {
        return new SkinCache.ResolveResult(SkinCache.ResolveStatus.HIT, "skin-id", TEXTURE_URL, "new-texture", "new-signature", "");
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
