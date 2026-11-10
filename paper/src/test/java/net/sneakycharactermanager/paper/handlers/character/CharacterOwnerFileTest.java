package net.sneakycharactermanager.paper.handlers.character;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.consolecommands.ConsoleCommandCharDisable;
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;

/** A character's gameplay file belongs to its owner: {@code characterdata/<owner>/<id>.yml}, never the wearer's. */
class CharacterOwnerFileTest {
    @TempDir Path data;

    final UUID wearerId = UUID.randomUUID();
    final UUID ownerId = UUID.randomUUID();
    final Player wearer = mock(Player.class);
    final SneakyCharacterManager plugin = mock(SneakyCharacterManager.class);
    final List<Path> writes = new ArrayList<>();
    final YamlConfiguration config = new YamlConfiguration();
    MockedStatic<SneakyCharacterManager> plugins;
    MockedStatic<Bukkit> bukkit;
    MockedStatic<CharacterLoader> loader;
    MockedStatic<ConsoleCommandCharDisable> disables;
    MockedStatic<GameplayFile> files;

    @BeforeEach
    void setUp() {
        plugins = mockStatic(SneakyCharacterManager.class);
        bukkit = mockStatic(Bukkit.class);
        loader = mockStatic(CharacterLoader.class);
        disables = mockStatic(ConsoleCommandCharDisable.class);
        // Real file layout; only the empty-inventory encoding needs a running server.
        files = mockStatic(GameplayFile.class, CALLS_REAL_METHODS);
        files.when(() -> GameplayFile.emptyInventory(anyInt())).thenReturn("EMPTY");
        plugins.when(SneakyCharacterManager::getInstance).thenReturn(plugin);
        plugins.when(SneakyCharacterManager::getCharacterDataFolder).thenReturn(data.toFile());
        loader.when(() -> CharacterLoader.loadCharacter(any())).thenReturn(true);
        config.set("manageInventories", false);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.writeFile(any(), anyString())).thenAnswer(call -> {
            writes.add(call.getArgument(0));
            return CompletableFuture.completedFuture(null);
        });
        plugin.skinQueue = mock(SkinQueue.class);
        World world = mock(World.class);
        when(world.getSpawnLocation()).thenReturn(new Location(null, 7, 70, 7));
        bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getSize()).thenReturn(41);
        when(wearer.getInventory()).thenReturn(inventory);
        when(wearer.getUniqueId()).thenReturn(wearerId);
        when(wearer.getLocation()).thenReturn(new Location(null, 1, 2, 3));
    }

    @AfterEach
    void tearDown() {
        Character.remove(wearer);
        files.close(); disables.close(); loader.close(); bukkit.close(); plugins.close();
    }

    @Test
    void ownCharacterKeepsItsExistingPath() throws Exception {
        File file = saved(wearerId, "own", 10);
        character(wearerId, "own").save();
        assertEquals(List.of(file.toPath()), writes);
    }

    @Test
    void borrowedCharacterLoadsAndSavesTheOwnersFile() throws Exception {
        saved(wearerId, "borrowed", 10); // a same-ID file of the wearer's must be ignored
        File ownersFile = saved(ownerId, "borrowed", 40);
        Character borrowed = character(ownerId, "borrowed");

        borrowed.load();
        verify(wearer).teleport(new Location(null, 40, 65, 0));
        assertSame(borrowed, Character.get(wearer));

        borrowed.save();
        assertEquals(List.of(ownersFile.toPath()), writes);
    }

    @Test
    void switchingAwayFromABorrowedCharacterSavesItToTheOwner() throws Exception {
        File ownersFile = saved(ownerId, "borrowed", 40);
        saved(wearerId, "own", 10);
        character(ownerId, "borrowed").load();

        character(wearerId, "own").load();

        assertEquals(List.of(ownersFile.toPath()), writes);
        verify(wearer).teleport(new Location(null, 10, 65, 0));
    }

    /** Bug 3: a borrowed character with no file copied the wearer's live inventory and location. */
    @Test
    void borrowedCharacterWithoutAFileStartsFreshInsteadOfCopyingTheWearer() throws Exception {
        Character borrowed = character(ownerId, "borrowed");

        borrowed.load();

        File created = data.resolve(ownerId.toString()).resolve("borrowed.yml").toFile();
        YamlConfiguration file = YamlConfiguration.loadConfiguration(created);
        assertEquals(new Location(null, 7, 70, 7), file.getLocation("location"));
        assertEquals("EMPTY", file.getString("inventory"));
        verify(wearer, never()).getLocation(); // no snapshot of the wearer
        assertFalse(data.resolve(wearerId.toString()).toFile().exists());
        verify(wearer).teleport(new Location(null, 7, 71, 7));
        assertEquals(List.of(), writes);
    }

    /** Bug 3, offline owner: building the owner's list must not touch a player or the disk. */
    @Test
    void listEntriesForAnOfflineOwnerNeedNoPlayerAndCreateNoFiles() {
        assertDoesNotThrow(() -> new Character(new CharacterKey(ownerId, "a"), null, "Name", "", "", "", "", false, "", "", ""));
        assertFalse(data.resolve(ownerId.toString()).toFile().exists());
        assertEquals(List.of(), writes);
    }

    @Test
    void ownersFirstCharacterStillStartsFromTheirLiveState() throws Exception {
        character(wearerId, "own").load();

        YamlConfiguration file = YamlConfiguration.loadConfiguration(data.resolve(wearerId.toString()).resolve("own.yml").toFile());
        assertEquals(new Location(null, 1, 2, 3), file.getLocation("location"));
        verify(wearer).teleport(new Location(null, 1, 3, 3));
    }

    @Test
    void ownersLaterNewCharacterStartsFresh() throws Exception {
        saved(wearerId, "first", 10);
        character(wearerId, "second").load();

        YamlConfiguration file = YamlConfiguration.loadConfiguration(data.resolve(wearerId.toString()).resolve("second.yml").toFile());
        assertEquals(new Location(null, 7, 70, 7), file.getLocation("location"));
    }

    @Test
    void aBodyAlreadyWearingSomethingNeverSeedsANewFile() throws Exception {
        saved(ownerId, "borrowed", 40);
        character(ownerId, "borrowed").load(); // the wearer has no files of its own yet

        character(wearerId, "own").load();

        YamlConfiguration file = YamlConfiguration.loadConfiguration(data.resolve(wearerId.toString()).resolve("own.yml").toFile());
        assertEquals(new Location(null, 7, 70, 7), file.getLocation("location"));
    }

    @Test
    void firstLoadMeansNoGameplayFileYet() throws Exception {
        Character fresh = character(ownerId, "fresh");
        loader.when(() -> CharacterLoader.loadCharacter(fresh)).thenAnswer(call -> {
            assertTrue(fresh.isFirstLoad());
            return true;
        });
        fresh.load();

        saved(ownerId, "existing", 1);
        Character existing = character(ownerId, "existing");
        loader.when(() -> CharacterLoader.loadCharacter(existing)).thenAnswer(call -> {
            assertFalse(existing.isFirstLoad());
            return true;
        });
        existing.load();
        loader.verify(() -> CharacterLoader.loadCharacter(any()), times(2));
    }

    @Test
    void loadEventCarriesTheOwner() {
        Server server = mock(Server.class);
        PluginManager events = mock(PluginManager.class);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        when(server.getPluginManager()).thenReturn(events);
        doAnswer(call -> {
            ((LoadCharacterEvent) call.getArgument(0)).setCancelled(true);
            return null;
        }).when(events).callEvent(any());
        Character borrowed = character(ownerId, "borrowed");
        loader.when(() -> CharacterLoader.loadCharacter(borrowed)).thenCallRealMethod();

        assertFalse(CharacterLoader.loadCharacter(borrowed));

        ArgumentCaptor<LoadCharacterEvent> event = ArgumentCaptor.forClass(LoadCharacterEvent.class);
        verify(events).callEvent(event.capture());
        assertSame(wearer, event.getValue().getPlayer());
        assertEquals(new CharacterKey(ownerId, "borrowed"), event.getValue().getCharacterKey());
        assertEquals(ownerId, event.getValue().getOwnerUUID());
        assertEquals("borrowed", event.getValue().getCharacterUUID());
    }

    @Test
    void deprecatedEventConstructorTreatsThePlayerAsOwner() {
        @SuppressWarnings("deprecation")
        LoadCharacterEvent event = new LoadCharacterEvent(wearer, false, "own", "Name", "", false, "{}");
        assertEquals(new CharacterKey(wearerId, "own"), event.getCharacterKey());
    }

    private Character character(UUID owner, String id) {
        return new Character(new CharacterKey(owner, id), wearer, "Name", "", "", "", "", false, "", "", "");
    }

    private File saved(UUID owner, String id, double x) throws Exception {
        File file = data.resolve(owner.toString()).resolve(id + ".yml").toFile();
        Files.createDirectories(file.getParentFile().toPath());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("location", new Location(null, x, 64, 0));
        yaml.save(file);
        return file;
    }
}
