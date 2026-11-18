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
import net.sneakycharactermanager.paper.api.character.PuppetLeaveEvent;
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
            Path target = call.getArgument(0);
            writes.add(target);
            Files.writeString(target, call.getArgument(1)); // the real writer lands it later; this one at once
            return CompletableFuture.completedFuture(null);
        });
        plugin.nametagManager = mock(net.sneakycharactermanager.paper.handlers.nametags.NametagManager.class);
        plugin.skinQueue = mock(SkinQueue.class);
        World world = mock(World.class);
        when(world.getSpawnLocation()).thenReturn(new Location(null, 7, 70, 7));
        bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
        Server server = mock(Server.class); // leaving a borrowed character fires PuppetLeaveEvent
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        bukkit.when(Bukkit::getServer).thenReturn(server);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getSize()).thenReturn(41);
        when(wearer.getInventory()).thenReturn(inventory);
        when(wearer.getUniqueId()).thenReturn(wearerId);
        when(wearer.getLocation()).thenReturn(new Location(null, 1, 2, 3));
    }

    @AfterEach
    void tearDown() {
        Character.remove(wearer, PuppetLeaveEvent.Reason.QUIT);
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
        assertEquals(List.of(created.toPath()), writes); // only the owner's new file is written
    }

    /** Bug 3, offline owner: building the owner's list must not touch a player or the disk. */
    @Test
    void listEntriesForAnOfflineOwnerNeedNoPlayerAndCreateNoFiles() {
        assertDoesNotThrow(() -> new Character(new CharacterKey(ownerId, "a"), null, "Name", "", "", "", "", false, "", ""));
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

    /** Files are created on load, so the admin editor must not create one that would skip fresh defaults. */
    @Test
    void adminInventoryEditorOnlyEditsStoredCharacters() throws Exception {
        CharacterKey neverLoaded = new CharacterKey(ownerId, "never-loaded");
        assertTrue(CharacterSelectionMenu.inventoryEditRefusal(neverLoaded).contains("never been loaded"));
        assertFalse(data.resolve(ownerId.toString()).toFile().exists());

        saved(ownerId, "stored", 40);
        assertNull(CharacterSelectionMenu.inventoryEditRefusal(new CharacterKey(ownerId, "stored")));

        character(ownerId, "stored").load(); // worn by someone: live, not stored
        assertTrue(CharacterSelectionMenu.inventoryEditRefusal(new CharacterKey(ownerId, "stored")).contains("Someone"));
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
    void eachLoadFiresOneCharacterLoadedEventOnceTheCharacterIsOn() throws Exception {
        List<String> order = new ArrayList<>();
        List<CharacterLoadedEvent> loaded = loadedEvents(order);
        saved(ownerId, "borrowed", 40);
        Character borrowed = character(ownerId, "borrowed");
        loader.when(() -> CharacterLoader.loadCharacter(any())).thenAnswer(call -> {
            order.add("LoadCharacterEvent");
            return true;
        });

        borrowed.load();
        Character own = character(wearerId, "own");
        own.load();
        own.load(); // reloading the worn character

        assertEquals(List.of("LoadCharacterEvent", "loaded:borrowed", "LoadCharacterEvent", "PuppetLeaveEvent", "loaded:own",
                "LoadCharacterEvent", "loaded:own"), order, "after its LoadCharacterEvent, and after the borrowed one left");
        assertEquals(3, loaded.size());
        assertEquals(new CharacterKey(ownerId, "borrowed"), loaded.get(0).getCharacterKey());
        assertNull(loaded.get(0).getPreviousCharacterKey());
        assertFalse(loaded.get(0).isFirstLoad(), "the owner's file existed");
        assertSame(wearer, loaded.get(1).getPlayer());
        assertEquals(new CharacterKey(wearerId, "own"), loaded.get(1).getCharacterKey());
        assertEquals(new CharacterKey(ownerId, "borrowed"), loaded.get(1).getPreviousCharacterKey());
        assertTrue(loaded.get(1).isFirstLoad(), "no file yet");
        assertEquals("Name", loaded.get(1).getCharacterName());
        assertEquals(new CharacterKey(wearerId, "own"), loaded.get(2).getPreviousCharacterKey());
        assertFalse(loaded.get(2).isFirstLoad());
        assertTrue(loaded.stream().noneMatch(CharacterLoadedEvent::isSwap));
    }

    @Test
    void aCancelledLoadFiresNoCharacterLoadedEvent() {
        List<CharacterLoadedEvent> loaded = loadedEvents(new ArrayList<>());
        loader.when(() -> CharacterLoader.loadCharacter(any())).thenReturn(false);

        character(wearerId, "own").load();

        assertEquals(List.of(), loaded);
        assertNull(Character.get(wearer));
    }

    @Test
    void aLoadReplacedFromItsOwnEventsFiresOnlyForWhatTheBodyEndsUpWearing() throws Exception {
        List<CharacterLoadedEvent> loaded = loadedEvents(new ArrayList<>());
        character(ownerId, "borrowed").load();
        loaded.clear();
        Character other = character(wearerId, "other");
        // A PuppetLeaveEvent listener loads yet another character before "own"'s load has fired its event.
        listener = event -> {
            if (event instanceof PuppetLeaveEvent) other.load();
        };

        character(wearerId, "own").load();

        assertEquals(1, loaded.size());
        assertEquals(new CharacterKey(wearerId, "other"), loaded.get(0).getCharacterKey());
        assertEquals(new CharacterKey(wearerId, "own"), loaded.get(0).getPreviousCharacterKey());
    }

    /**
     * Codex review finding 3: a listener loading another character onto the body doesn't cut in front of the event
     * being delivered; its event reaches every listener after it, so the last one a cache sees is the current state.
     */
    @Test
    void aLoadFromAListenerIsDeliveredAfterTheCurrentEventReachedEveryListener() throws Exception {
        Server server = mock(Server.class);
        PluginManager events = mock(PluginManager.class);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        when(server.getPluginManager()).thenReturn(events);
        Character other = character(wearerId, "other");
        List<String> firstListener = new ArrayList<>();
        List<String> secondListener = new ArrayList<>();
        doAnswer(call -> {
            if (!(call.getArgument(0) instanceof CharacterLoadedEvent e)) return null;
            firstListener.add(e.getCharacterUUID());
            if (e.getCharacterUUID().equals("own")) other.load(); // the first listener switches the body again
            secondListener.add(e.getCharacterUUID() + "/" + Character.get(wearer).getCharacterUUID());
            return null;
        }).when(events).callEvent(any());

        character(wearerId, "own").load();

        assertEquals(List.of("own", "other"), firstListener);
        // The second listener got "own" whole before "other", while live state was already "other".
        assertEquals(List.of("own/other", "other/other"), secondListener);
        assertEquals(new CharacterKey(wearerId, "other"), Character.get(wearer).getKey());
    }

    /**
     * Codex review 3, finding 1: a skinless character's default skin is the skin its body had when its load finished,
     * not the skin of another character a {@code CharacterLoadedEvent} listener put on the body before the default-skin
     * step ran (after the load returned in SneakyDB mode, on a later proxy message in legacy mode).
     */
    @Test
    void theDefaultSkinIsTheBodysSkinFromBeforeTheLoadedEventsListeners() throws Exception {
        java.net.URL ownSkin = java.net.URI.create("http://textures.minecraft.net/texture/own").toURL();
        java.net.URL otherSkin = java.net.URI.create("http://textures.minecraft.net/texture/other").toURL();
        java.net.URL[] worn = {ownSkin};
        org.bukkit.profile.PlayerTextures textures = mock(org.bukkit.profile.PlayerTextures.class);
        when(textures.getSkin()).thenAnswer(call -> worn[0]);
        when(textures.getSkinModel()).thenReturn(org.bukkit.profile.PlayerTextures.SkinModel.CLASSIC);
        com.destroystokyo.paper.profile.PlayerProfile profile = mock(com.destroystokyo.paper.profile.PlayerProfile.class);
        when(profile.getTextures()).thenReturn(textures);
        when(wearer.getPlayerProfile()).thenReturn(profile);
        bukkit.when(() -> Bukkit.getPlayer(wearerId)).thenReturn(wearer);
        List<CharacterLoadedEvent> loaded = loadedEvents(new ArrayList<>());
        Character skinless = character(wearerId, "skinless");
        Character other = new Character(new CharacterKey(wearerId, "other"), wearer, "Other", "http://skins/other", "", "",
                "", false, "", "");
        loader.when(() -> CharacterLoader.loadCharacter(other)).thenAnswer(call -> {
            worn[0] = otherSkin; // its cached skin goes on the body
            return true;
        });
        listener = event -> {
            if (event instanceof CharacterLoadedEvent e && e.getCharacterUUID().equals("skinless")) other.load();
        };
        String owner = wearerId.toString();

        try (MockedStatic<net.sneakycharactermanager.paper.util.ProxyMessagingUtil> messages =
                mockStatic(net.sneakycharactermanager.paper.util.ProxyMessagingUtil.class)) {
            net.sneakycharactermanager.paper.storage.CharacterResultEffects.load(skinless, true);
            assertEquals(new CharacterKey(wearerId, "other"), Character.get(wearer).getKey(), "the listener's load went on");
            assertEquals(2, loaded.size());

            net.sneakycharactermanager.paper.storage.CharacterResultEffects.defaultSkin(owner, "skinless");
            messages.verify(() -> net.sneakycharactermanager.paper.util.ProxyMessagingUtil.sendByteArray(
                    wearer, "defaultSkin", owner, "skinless", ownSkin.toString(), false));
            messages.verify(() -> net.sneakycharactermanager.paper.util.ProxyMessagingUtil.sendByteArray(
                    any(), eq("defaultSkin"), any(), eq("skinless"), eq(otherSkin.toString()), anyBoolean()), never());

            // Used once: without a load of it, the step reads the live skin, as before.
            net.sneakycharactermanager.paper.storage.CharacterResultEffects.defaultSkin(owner, "skinless");
            messages.verify(() -> net.sneakycharactermanager.paper.util.ProxyMessagingUtil.sendByteArray(
                    wearer, "defaultSkin", owner, "skinless", otherSkin.toString(), false));
        }
    }

    /**
     * Codex review 4, finding 2: the skin is taken the moment the load puts the skinless character on, before the
     * {@code PuppetLeaveEvent} of the borrowed character it replaces, whose listener may load a third character (and
     * its skin) onto the body. Both storage modes.
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void theDefaultSkinIsTakenBeforeTheReplacedPuppetsLeaveEvent(boolean sneakyDb) throws Exception {
        java.net.URL ownSkin = java.net.URI.create("http://textures.minecraft.net/texture/own").toURL();
        java.net.URL thirdSkin = java.net.URI.create("http://textures.minecraft.net/texture/third").toURL();
        java.net.URL[] worn = {ownSkin};
        org.bukkit.profile.PlayerTextures textures = mock(org.bukkit.profile.PlayerTextures.class);
        when(textures.getSkin()).thenAnswer(call -> worn[0]);
        when(textures.getSkinModel()).thenReturn(org.bukkit.profile.PlayerTextures.SkinModel.CLASSIC);
        com.destroystokyo.paper.profile.PlayerProfile profile = mock(com.destroystokyo.paper.profile.PlayerProfile.class);
        when(profile.getTextures()).thenReturn(textures);
        when(wearer.getPlayerProfile()).thenReturn(profile);
        bukkit.when(() -> Bukkit.getPlayer(wearerId)).thenReturn(wearer);
        List<CharacterLoadedEvent> loaded = loadedEvents(new ArrayList<>());
        character(ownerId, "borrowed").load();
        Character third = new Character(new CharacterKey(wearerId, "third"), wearer, "Third", "http://skins/third", "", "",
                "", false, "", "");
        loader.when(() -> CharacterLoader.loadCharacter(third)).thenAnswer(call -> {
            worn[0] = thirdSkin;
            return true;
        });
        listener = event -> {
            if (event instanceof PuppetLeaveEvent) third.load(); // the borrowed character leaving
        };
        Character skinless = character(wearerId, "skinless");
        String owner = wearerId.toString();

        try (MockedStatic<net.sneakycharactermanager.paper.util.ProxyMessagingUtil> messages =
                mockStatic(net.sneakycharactermanager.paper.util.ProxyMessagingUtil.class)) {
            if (sneakyDb) {
                assertTrue(net.sneakycharactermanager.paper.storage.CharacterResultEffects.load(skinless, true, true));
            } else {
                net.sneakycharactermanager.paper.storage.CharacterResultEffects.load(skinless, true);
                net.sneakycharactermanager.paper.storage.CharacterResultEffects.defaultSkin(owner, "skinless");
            }

            assertEquals(new CharacterKey(wearerId, "third"), Character.get(wearer).getKey(), "the leave listener's load went on");
            messages.verify(() -> net.sneakycharactermanager.paper.util.ProxyMessagingUtil.sendByteArray(
                    wearer, "defaultSkin", owner, "skinless", ownSkin.toString(), false));
            messages.verify(() -> net.sneakycharactermanager.paper.util.ProxyMessagingUtil.sendByteArray(
                    any(), eq("defaultSkin"), any(), any(), eq(thirdSkin.toString()), anyBoolean()), never());
        }
    }

    /** Codex review 4, finding 3: a legacy load's kept skin is dropped when its body quits. */
    @Test
    void aLegacyLoadsKeptSkinIsDroppedWhenTheBodyQuits() throws Exception {
        java.net.URL[] worn = {java.net.URI.create("http://textures.minecraft.net/texture/own").toURL()};
        org.bukkit.profile.PlayerTextures textures = mock(org.bukkit.profile.PlayerTextures.class);
        when(textures.getSkin()).thenAnswer(call -> worn[0]);
        when(textures.getSkinModel()).thenReturn(org.bukkit.profile.PlayerTextures.SkinModel.CLASSIC);
        com.destroystokyo.paper.profile.PlayerProfile profile = mock(com.destroystokyo.paper.profile.PlayerProfile.class);
        when(profile.getTextures()).thenReturn(textures);
        when(wearer.getPlayerProfile()).thenReturn(profile);
        bukkit.when(() -> Bukkit.getPlayer(wearerId)).thenReturn(wearer);
        String owner = wearerId.toString();

        try (MockedStatic<net.sneakycharactermanager.paper.util.ProxyMessagingUtil> messages =
                mockStatic(net.sneakycharactermanager.paper.util.ProxyMessagingUtil.class)) {
            net.sneakycharactermanager.paper.storage.CharacterResultEffects.load(character(wearerId, "skinless"), true);
            net.sneakycharactermanager.paper.storage.CharacterResultEffects.forget(wearer);
            worn[0] = java.net.URI.create("http://textures.minecraft.net/texture/later").toURL();
            net.sneakycharactermanager.paper.storage.CharacterResultEffects.defaultSkin(owner, "skinless");
            messages.verify(() -> net.sneakycharactermanager.paper.util.ProxyMessagingUtil.sendByteArray(
                    wearer, "defaultSkin", owner, "skinless", "http://textures.minecraft.net/texture/later", false));
        }
    }

    /** Runs inside each event, like a listener; null for none. */
    java.util.function.Consumer<org.bukkit.event.Event> listener;

    /** Records each event in {@code order} and returns the CharacterLoadedEvents, checking each sees the new character. */
    private List<CharacterLoadedEvent> loadedEvents(List<String> order) {
        Server server = mock(Server.class);
        PluginManager events = mock(PluginManager.class);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        when(server.getPluginManager()).thenReturn(events);
        List<CharacterLoadedEvent> loaded = new ArrayList<>();
        doAnswer(call -> {
            org.bukkit.event.Event event = call.getArgument(0);
            if (event instanceof CharacterLoadedEvent e) {
                assertEquals(e.getCharacterKey(), Character.get(e.getPlayer()).getKey(), "Character.get already returns it");
                order.add("loaded:" + e.getCharacterUUID());
                loaded.add(e);
            } else {
                order.add(event.getClass().getSimpleName());
            }
            if (listener != null) listener.accept(event);
            return null;
        }).when(events).callEvent(any());
        return loaded;
    }

    @Test
    void deprecatedEventConstructorTreatsThePlayerAsOwner() {
        @SuppressWarnings("deprecation")
        LoadCharacterEvent event = new LoadCharacterEvent(wearer, false, "own", "Name", "", false);
        assertEquals(new CharacterKey(wearerId, "own"), event.getCharacterKey());
    }

    private Character character(UUID owner, String id) {
        return new Character(new CharacterKey(owner, id), wearer, "Name", "", "", "", "", false, "", "");
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
