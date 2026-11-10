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
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.consolecommands.ConsoleCommandCharDisable;
import net.sneakycharactermanager.paper.handlers.nametags.NametagManager;
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;

/**
 * Gameplay files are written asynchronously; until a write lands, loading a character must see the newest queued
 * content, never an older file on disk.
 */
class GameplayFileReadThroughTest {
    @TempDir Path data;

    final UUID wearerId = UUID.randomUUID();
    final UUID otherBodyId = UUID.randomUUID();
    final UUID ownerId = UUID.randomUUID();
    final Player wearer = mock(Player.class);
    final Player otherBody = mock(Player.class);
    final SneakyCharacterManager plugin = mock(SneakyCharacterManager.class);
    final YamlConfiguration config = new YamlConfiguration();
    /** Writes handed to the async writer and not yet landed, oldest first. */
    final List<PendingWrite> pending = new ArrayList<>();
    MockedStatic<SneakyCharacterManager> plugins;
    MockedStatic<Bukkit> bukkit;
    MockedStatic<CharacterLoader> loader;
    MockedStatic<ConsoleCommandCharDisable> disables;
    MockedStatic<GameplayFile> files;

    record PendingWrite(Path target, String content, CompletableFuture<Void> future) {}

    @BeforeEach
    void setUp() {
        plugins = mockStatic(SneakyCharacterManager.class);
        bukkit = mockStatic(Bukkit.class);
        loader = mockStatic(CharacterLoader.class);
        disables = mockStatic(ConsoleCommandCharDisable.class);
        // Real file layout and read-through; only the empty-inventory encoding needs a running server.
        files = mockStatic(GameplayFile.class, CALLS_REAL_METHODS);
        files.when(() -> GameplayFile.emptyInventory(anyInt())).thenReturn("EMPTY");
        plugins.when(SneakyCharacterManager::getInstance).thenReturn(plugin);
        plugins.when(SneakyCharacterManager::getCharacterDataFolder).thenReturn(data.toFile());
        loader.when(() -> CharacterLoader.loadCharacter(any())).thenReturn(true);
        config.set("manageInventories", false);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.writeFile(any(), anyString())).thenAnswer(call -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            pending.add(new PendingWrite(call.getArgument(0), call.getArgument(1), future)); // lands on flush
            return future;
        });
        plugin.nametagManager = mock(NametagManager.class);
        plugin.skinQueue = mock(SkinQueue.class);
        World world = mock(World.class);
        when(world.getSpawnLocation()).thenReturn(new Location(null, 7, 70, 7));
        bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
        body(wearer, wearerId);
        body(otherBody, otherBodyId);
    }

    @AfterEach
    void tearDown() throws Exception {
        flushWrites(); // never leave entries in GameplayFile's static queue for the next test
        Character.remove(wearer);
        Character.remove(otherBody);
        files.close(); disables.close(); loader.close(); bukkit.close(); plugins.close();
    }

    /** Bug: reloading the same character on the same body read the old file, undoing the save just queued. */
    @Test
    void reloadingTheSameCharacterOnTheSameBodySeesTheQueuedSave() throws Exception {
        saved(ownerId, "k", 10);
        character(wearer, ownerId, "k").load();
        verify(wearer).teleport(new Location(null, 10, 65, 0));

        when(wearer.getLocation()).thenReturn(new Location(null, 50, 2, 0));
        character(wearer, ownerId, "k").load(); // saves the first instance first; no write has landed

        verify(wearer).teleport(new Location(null, 50, 3, 0));
        verify(wearer, times(1)).teleport(new Location(null, 10, 65, 0)); // not sent back to the old file's spot
    }

    /** Bug: another body picking a character up right after it was saved got the old file's location. */
    @Test
    void anotherBodyPickingTheCharacterUpRightAfterSeesTheQueuedSave() throws Exception {
        saved(ownerId, "k", 10);
        character(wearer, ownerId, "k").load();
        when(wearer.getLocation()).thenReturn(new Location(null, 60, 2, 0));
        character(wearer, wearerId, "own").load(); // switching away queues k's save

        character(otherBody, ownerId, "k").load();

        verify(otherBody).teleport(new Location(null, 60, 3, 0));
    }

    /** Bug guard: once a write lands the queue entry must go, or later edits on disk would be hidden forever. */
    @Test
    void afterTheWriteLandsReadsComeFromDisk() throws Exception {
        File file = saved(ownerId, "k", 10);
        character(wearer, ownerId, "k").load();
        when(wearer.getLocation()).thenReturn(new Location(null, 50, 2, 0));
        character(wearer, wearerId, "own").load();
        flushWrites();
        assertEquals(new Location(null, 50, 2, 0), YamlConfiguration.loadConfiguration(file).getLocation("location"));

        saved(ownerId, "k", 99); // changed on disk after the queued save landed
        assertEquals(new Location(null, 99, 64, 0), GameplayFile.read(file).getLocation("location"));
        character(otherBody, ownerId, "k").load();

        verify(otherBody).teleport(new Location(null, 99, 65, 0));
    }

    /** Bug guard: an older write landing must not drop a newer queued save of the same file. */
    @Test
    void anOlderWriteLandingKeepsTheNewerQueuedContent() throws Exception {
        File file = saved(ownerId, "k", 10);
        Character worn = character(wearer, ownerId, "k");
        worn.load();
        when(wearer.getLocation()).thenReturn(new Location(null, 20, 2, 0));
        worn.save();
        when(wearer.getLocation()).thenReturn(new Location(null, 30, 2, 0));
        worn.save();

        flushOldestWrite(); // the x=20 save lands; x=30 is still queued

        assertEquals(new Location(null, 20, 2, 0), YamlConfiguration.loadConfiguration(file).getLocation("location"));
        assertEquals(new Location(null, 30, 2, 0), GameplayFile.read(file).getLocation("location"));
    }

    /** Bug guard: saves A, B, A (same content twice): the first A landing must not hide the newest A behind B. */
    @Test
    void anOlderIdenticalWriteLandingKeepsTheNewestQueuedSave() throws Exception {
        File file = saved(ownerId, "k", 10);
        Character worn = character(wearer, ownerId, "k");
        worn.load();
        when(wearer.getLocation()).thenReturn(new Location(null, 20, 2, 0));
        worn.save(); // A
        when(wearer.getLocation()).thenReturn(new Location(null, 30, 2, 0));
        worn.save(); // B
        when(wearer.getLocation()).thenReturn(new Location(null, 20, 2, 0));
        worn.save(); // A again

        flushOldestWrite(); // the first A lands
        flushOldestWrite(); // B lands; the newest A is still queued

        assertEquals(new Location(null, 30, 2, 0), YamlConfiguration.loadConfiguration(file).getLocation("location"));
        assertEquals(new Location(null, 20, 2, 0), GameplayFile.read(file).getLocation("location"));
    }

    /** Bug: the nametag was set before the body was mapped to the new character, so it showed the previous one's title. */
    @Test
    void nametagIsSetAfterTheBodyWearsTheNewCharacter() throws Exception {
        saved(ownerId, "k", 10);
        character(wearer, wearerId, "own").load(); // the body wears something else before
        Character loading = character(wearer, ownerId, "k");
        doAnswer(call -> {
            assertSame(loading, Character.get(wearer));
            return null;
        }).when(plugin.nametagManager).nicknamePlayer(same(wearer), eq("Name"));

        loading.load();

        verify(plugin.nametagManager, times(2)).nicknamePlayer(same(wearer), eq("Name"));
        assertSame(loading, Character.get(wearer));
    }

    /** Bug: closing the admin inventory editor overwrote inventory the character gained while it was open. */
    @Test
    void adminInventoryEditIsDroppedWhenTheStoredInventoryChangedMeanwhile() throws Exception {
        String current = file(new Location(null, 5, 64, 0), "PLAYED-SINCE");

        assertNull(CharacterSelectionMenu.editedInventory(current, "WHEN-OPENED", "EDITED"));
    }

    /** Bug guard: an admin edit replaces only the inventory and keeps the rest of the gameplay file. */
    @Test
    void adminInventoryEditReplacesOnlyTheInventory() throws Exception {
        String current = file(new Location(null, 5, 64, 0), "WHEN-OPENED");

        String updated = CharacterSelectionMenu.editedInventory(current, "WHEN-OPENED", "EDITED");

        YamlConfiguration result = new YamlConfiguration();
        result.loadFromString(updated);
        assertEquals("EDITED", result.getString("inventory"));
        assertEquals(new Location(null, 5, 64, 0), result.getLocation("location"));
    }

    /** Bug: a character whose file was only queued was refused by the admin editor as "never been loaded". */
    @Test
    void adminInventoryEditorTreatsAQueuedFileAsStored() throws Exception {
        CharacterKey key = new CharacterKey(ownerId, "k");
        assertTrue(CharacterSelectionMenu.inventoryEditRefusal(key).contains("never been loaded"));

        character(wearer, ownerId, "k").load(); // no file yet: load queues a fresh one
        assertTrue(CharacterSelectionMenu.inventoryEditRefusal(key).contains("Someone"));
        character(wearer, wearerId, "own").load(); // switching away queues k's save

        assertFalse(GameplayFile.of(key).exists(), "nothing landed on disk yet");
        assertNull(CharacterSelectionMenu.inventoryEditRefusal(key));
    }

    private void body(Player body, UUID id) {
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getSize()).thenReturn(41);
        when(body.getInventory()).thenReturn(inventory);
        when(body.getUniqueId()).thenReturn(id);
        when(body.getLocation()).thenReturn(new Location(null, 1, 2, 3));
    }

    private Character character(Player body, UUID owner, String id) {
        return new Character(new CharacterKey(owner, id), body, "Name", "", "", "", "", false, "", "", "");
    }

    /** Lands every pending write, oldest first, as the real writer eventually would. */
    private void flushWrites() throws Exception {
        while (!pending.isEmpty()) flushOldestWrite();
    }

    private void flushOldestWrite() throws Exception {
        PendingWrite write = pending.remove(0);
        Files.writeString(write.target(), write.content());
        write.future().complete(null);
    }

    private static String file(Location location, String inventory) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("location", location);
        yaml.set("inventory", inventory);
        return yaml.saveToString();
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
