package net.sneakycharactermanager.paper.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.sneakyutils.util.InventoryUtility;

import net.kyori.adventure.text.Component;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.character.CharacterHolders;
import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.api.character.CharacterLists;
import net.sneakycharactermanager.paper.api.character.ListedCharacter;
import net.sneakycharactermanager.paper.api.character.PuppetLeaveEvent;
import net.sneakycharactermanager.paper.api.character.PuppetLeaveEvent.Reason;
import net.sneakycharactermanager.paper.api.character.PuppetResult;
import net.sneakycharactermanager.paper.api.character.Puppets;
import net.sneakycharactermanager.paper.api.character.ReleaseResult;
import net.sneakycharactermanager.paper.api.character.StorageMode;
import net.sneakycharactermanager.paper.consolecommands.ConsoleCommandCharDisable;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.CharacterSelectionMenu;
import net.sneakycharactermanager.paper.handlers.character.LoadCharacterEvent;
import net.sneakycharactermanager.paper.handlers.nametags.NametagManager;
import net.sneakycharactermanager.paper.handlers.skins.SkinCache;
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;
import net.sneakycharactermanager.paper.handlers.skins.SkinStateManager;
import net.sneakycharactermanager.paper.handlers.titles.TitleRegistry;
import net.sneakycharactermanager.paper.listeners.ConnectionEventListeners;
import net.sneakycharactermanager.paper.util.SkinUtil;

/**
 * The public puppet API ({@link Puppets}, {@link PuppetLeaveEvent}, {@link CharacterLists}) end to end on the SneakyDB
 * path. The Supporter's body puppets the Driver's (the owner's) characters. The README's "Puppets" section lists the
 * same cases. {@code BorrowedCharacterTest} runs ticket 02's end-to-end checks (nick, skin, saves land on the owner,
 * commands work) through {@link Puppets#puppet} with an offline owner.
 *
 * <p>Starting:
 * <ol>
 * <li>S1 An online or offline owner's character loads onto the body as a normal switch: the body's own character is
 *     saved first, the borrowed one loads from the owner's file, and no last-played pointer moves.</li>
 * <li>S2 A body that quits while puppeting saves to the owner and rejoins as its own last-played character.</li>
 * </ol>
 * Failing (nothing loads, and the future says why):
 * <ol>
 * <li>F1 Legacy mode: {@code UNSUPPORTED} for puppet and release, and the list fails.</li>
 * <li>F2 The body is offline: {@code BODY_OFFLINE}. F3 The body's own character: {@code OWN_CHARACTER}.</li>
 * <li>F4 Another body, or the owner, wears it when asked or by the time the load arrives: {@code HELD}.</li>
 * <li>F5 No such character, or a disabled one: {@code NOT_FOUND}.</li>
 * <li>F6 The owner logs in or out while the load is on its way: {@code INTERRUPTED}.</li>
 * <li>F7 The body quits mid-load, or a newer load for the body replaces it: {@code INTERRUPTED}.</li>
 * <li>F8 The owner's record changes while the load is on its way (a stale read), or a listener of the load's own
 *     events takes the character off again at once: {@code INTERRUPTED}.</li>
 * <li>F9 A {@code LoadCharacterEvent} listener cancels the load: {@code REFUSED}.</li>
 * <li>F10 Storage fails, or SCM closes mid-load: {@code FAILED}. F11 Called off the main thread: throws.</li>
 * </ol>
 * Ending ({@link PuppetLeaveEvent} fires once per way of leaving, after the change):
 * <ol>
 * <li>E1 {@code release}: saves the borrowed character where it stands, takes it off, the body gets its own last-played
 *     character; {@code RELEASED}. Its inventory is cleared after the save. A body that isn't puppeting: {@code NOT_PUPPETED}, nothing happens.</li>
 * <li>E2 Any other load onto the body (its own {@code /char}, another puppet): {@code LOADED_OTHER}.</li>
 * <li>E3 A quit, or the autosave finding the body offline: {@code QUIT} (S2). E4 The owner joining as it: {@code OWNER_JOINED}. E5 {@code chardisable}:
 *     {@code LOCKED_OUT}.</li>
 * <li>E6 Reloading the same borrowed character, and switches between one's own characters, fire nothing.</li>
 * </ol>
 * Character lists:
 * <ol>
 * <li>L1 An offline owner's enabled characters in order, last-played marked, without creating the owner or building
 *     any {@code Character}. An unknown owner: empty.</li>
 * <li>L2 An online owner's current character is the one they wear, unless it isn't theirs.</li>
 * <li>L3 Storage fails, or SCM closes mid-list: the future fails.</li>
 * </ol>
 */
class PuppetApiTest {
    static final String SNAPSHOT = "o.last_played_character, o.revision, c.*";

    @TempDir Path data;
    final UUID ownerId = UUID.randomUUID();
    final UUID supporterId = UUID.randomUUID();
    final UUID thirdId = UUID.randomUUID();
    final CharacterKey main = new CharacterKey(ownerId, "main");
    final CharacterKey second = new CharacterKey(ownerId, "second");
    final CharacterKey supportersOwn = new CharacterKey(supporterId, "own");
    final CharacterKey thirdsOwn = new CharacterKey(thirdId, "third");
    final SneakyCharacterManager plugin = mock(SneakyCharacterManager.class);
    final Database api = new Database();
    final List<PendingWrite> writes = new ArrayList<>();
    /** Each leave event with what the body wore when it fired. */
    final List<Object[]> left = new ArrayList<>();
    boolean deferTasks;
    final List<Runnable> deferred = new ArrayList<>();
    PluginManager events;
    Player owner, supporter, third;
    DatabaseBackend backend;
    MockedStatic<SneakyCharacterManager> plugins;
    MockedStatic<Bukkit> bukkit;
    MockedStatic<SkinUtil> skins;
    MockedStatic<SkinCache> cache;

    record PendingWrite(Path target, String content) {}

    /** Fake SneakyDB: one snapshot per owner, with a settable last-played character and disabled characters. */
    static class Database extends SqlStoreTest.Api {
        final Map<String, List<String[]>> characters = new HashMap<>();
        final Map<String, String> lastPlayed = new HashMap<>();
        final Set<String> disabled = new HashSet<>();
        boolean failSnapshots;
        @Override public CompletableFuture<List<Map<String,Object>>> query(String sql, List<?> args) {
            if (sql.contains(SNAPSHOT)) {
                statements.add(sql); bindings.add(args);
                if (failSnapshots) return CompletableFuture.failedFuture(new IllegalStateException("database down"));
                String owner = (String) args.getFirst();
                List<Map<String,Object>> rows = new ArrayList<>();
                for (String[] c : characters.getOrDefault(owner, List.of()))
                    rows.add(row(c[0], c[1], lastPlayed.get(owner), !disabled.contains(c[0])));
                return CompletableFuture.completedFuture(rows);
            }
            if (sql.startsWith("SELECT * FROM scm.uniform_variants")) return CompletableFuture.completedFuture(List.of());
            return super.query(sql, args);
        }
        /** Every last-played write, as "owner/character". */
        List<String> pointerWrites() {
            List<String> writes = new ArrayList<>();
            for (int i = 0; i < statements.size(); i++) if (statements.get(i).contains("SET last_played_character = ?"))
                writes.add(bindings.get(i).get(1) + "/" + bindings.get(i).get(0));
            return writes;
        }
        boolean any(String fragment) { return statements.stream().anyMatch(s -> s.contains(fragment)); }
        void forget() { statements.clear(); bindings.clear(); }
    }

    @BeforeEach
    void setUp() throws Exception {
        api.ready.complete(null);
        api.characters.put(ownerId.toString(), List.of(new String[]{"main", "Main Hero"}, new String[]{"retired", "Old Self"},
                new String[]{"second", "Second Self"}));
        api.disabled.add("retired");
        api.characters.put(supporterId.toString(), List.<String[]>of(new String[]{"own", "Supporter Self"}));
        api.characters.put(thirdId.toString(), List.<String[]>of(new String[]{"third", "Third Self"}));
        api.lastPlayed.put(ownerId.toString(), "main");
        api.lastPlayed.put(supporterId.toString(), "own");
        api.lastPlayed.put(thirdId.toString(), "third");
        plugins = mockStatic(SneakyCharacterManager.class);
        bukkit = mockStatic(Bukkit.class);
        skins = mockStatic(SkinUtil.class);
        cache = mockStatic(SkinCache.class, CALLS_REAL_METHODS);
        plugins.when(SneakyCharacterManager::getInstance).thenReturn(plugin);
        plugins.when(SneakyCharacterManager::getCharacterDataFolder).thenReturn(data.toFile());
        skins.when(() -> SkinUtil.getTextureUrl(any())).thenReturn("http://textures.minecraft.net/texture/stored");
        YamlConfiguration config = new YamlConfiguration();
        config.set("manageInventories", false);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getStorageMode()).thenReturn(StorageMode.SNEAKYDB);
        when(plugin.writeFile(any(), anyString())).thenAnswer(call -> {
            writes.add(new PendingWrite(call.getArgument(0), call.getArgument(1)));
            return CompletableFuture.completedFuture(null);
        });
        plugin.skinStateManager = new SkinStateManager();
        plugin.skinQueue = mock(SkinQueue.class);
        plugin.nametagManager = mock(NametagManager.class);
        plugin.selectionMenu = mock(CharacterSelectionMenu.class);
        plugin.titleRegistry = mock(TitleRegistry.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            if (deferTasks) deferred.add(call.getArgument(1));
            else ((Runnable) call.getArgument(1)).run();
            return null;
        });
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call -> List.of());
        events = mock(PluginManager.class);
        doAnswer(call -> {
            if (call.getArgument(0) instanceof PuppetLeaveEvent event) {
                Character worn = Character.get(event.getPlayer());
                left.add(new Object[]{event.getPlayer(), event.getCharacterKey(), event.getReason(),
                        worn == null ? null : worn.getKey(), CharacterHolders.holderOf(event.getCharacterKey())});
            }
            return null;
        }).when(events).callEvent(any());
        Server server = mock(Server.class);
        when(server.getPluginManager()).thenReturn(events);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(Bukkit::getPluginManager).thenReturn(events);
        owner = player(ownerId, "Driver", 1);
        supporter = player(supporterId, "Supporter", 2);
        third = player(thirdId, "Staff", 3);
        bukkit.when(() -> Bukkit.getPlayer(ownerId)).thenReturn(null); // the owner is offline until it joins
        gameplayFile(main, 10);
        gameplayFile(second, 20);
        gameplayFile(supportersOwn, 30);
        gameplayFile(thirdsOwn, 40);
        backend = new DatabaseBackend(plugin, new SqlStore(api, 5));
        when(plugin.getCharacterBackend()).thenReturn(backend);
        join(supporter);
        assertEquals(supportersOwn, Character.get(supporter).getKey());
        api.forget(); writes.clear(); clearInvocations(supporter);
    }

    @AfterEach
    void tearDown() {
        backend.close();
        Character.remove(owner, Reason.QUIT); Character.remove(supporter, Reason.QUIT); Character.remove(third, Reason.QUIT);
        ConsoleCommandCharDisable.playerCharEnable(supporterId.toString());
        cache.close(); skins.close(); bukkit.close(); plugins.close();
    }

    // S1 Starting

    @Test
    void puppetingAnOnlineOwnersCharacterIsANormalSwitchThatMovesNoPointer() {
        join(owner); // online, wearing main
        api.forget();

        assertEquals(PuppetResult.PUPPETED, puppet(supporter, second));

        assertEquals(second, Character.get(supporter).getKey());
        assertSame(supporter, CharacterHolders.holderOf(second));
        assertEquals(second, Puppets.borrowedBy(supporter));
        // The body's own character was saved first, where it stood; then the owner's file loaded.
        assertEquals(file(supportersOwn), writes.getFirst().target());
        assertEquals(new Location(null, 2, 0, 0), location(writes.getFirst()));
        verify(supporter).teleport(new Location(null, 20, 65, 0));
        assertEquals(List.of(), api.pointerWrites(), "neither the body's nor the owner's last-played may move");
        assertEquals(main, Character.get(owner).getKey());
        assertEquals(List.of(), left);
    }

    @Test
    void puppetingAnOfflineOwnersCharacterWorks() {
        assertEquals(PuppetResult.PUPPETED, puppet(supporter, second));

        assertEquals(second, Character.get(supporter).getKey());
        assertEquals(List.of(), api.pointerWrites());
        assertFalse(api.any("INSERT INTO scm.owners"), "puppeting must not create its owner");
    }

    // S2 / E3 Quit

    @Test
    void aPuppetedBodyThatQuitsSavesToTheOwnerAndRejoinsAsItsOwnLastPlayedCharacter() {
        puppet(supporter, second);
        when(supporter.getLocation()).thenAnswer(call -> new Location(null, 66, 5, 0));
        writes.clear();

        new ConnectionEventListeners().onPlayerLeave(new PlayerQuitEvent(supporter, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertEquals(List.of(file(second)), writes.stream().map(PendingWrite::target).toList());
        assertEquals(new Location(null, 66, 5, 0), location(writes.getFirst()));
        assertLeft(supporter, second, Reason.QUIT, null);
        assertNull(CharacterHolders.holderOf(second));

        join(supporter);

        assertEquals(supportersOwn, Character.get(supporter).getKey(), "the puppet is not re-applied on rejoin");
        assertEquals(List.of(supporterId + "/own"), api.pointerWrites()); // the join re-selects the unchanged pointer
        assertEquals(1, left.size());
    }

    // F1-F3, F11 Refused before anything is asked of storage

    @Test
    void legacyModeReportsUnsupported() {
        when(plugin.getStorageMode()).thenReturn(StorageMode.LEGACY);

        assertFalse(Puppets.isSupported());
        assertEquals(PuppetResult.UNSUPPORTED, puppet(supporter, second));
        assertEquals(ReleaseResult.UNSUPPORTED, Puppets.release(supporter));
        CompletionException error = assertThrows(CompletionException.class, () -> CharacterLists.of(ownerId).join());
        assertInstanceOf(UnsupportedOperationException.class, error.getCause());
        assertFalse(api.any(SNAPSHOT));
        assertEquals(supportersOwn, Character.get(supporter).getKey());
    }

    @Test
    void anOfflineBodyIsRefused() {
        bukkit.when(() -> Bukkit.getPlayer(supporterId)).thenReturn(null);

        assertEquals(PuppetResult.BODY_OFFLINE, puppet(supporter, second));
        assertFalse(api.any(SNAPSHOT));
    }

    @Test
    void theBodysOwnCharacterIsNotAPuppet() {
        assertEquals(PuppetResult.OWN_CHARACTER, puppet(supporter, supportersOwn));
        assertFalse(api.any(SNAPSHOT));
    }

    @Test
    void offTheMainThreadItThrows() {
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> Puppets.puppet(supporter, second));
        assertThrows(IllegalStateException.class, () -> Puppets.release(supporter));
        assertThrows(IllegalStateException.class, () -> CharacterLists.of(ownerId));
    }

    // F4 Held

    @Test
    void aCharacterAnotherBodyOrTheOwnerWearsIsHeld() {
        puppet(third, second);
        join(owner); // wears main
        api.forget();

        assertEquals(PuppetResult.HELD, puppet(supporter, second));
        assertEquals(PuppetResult.HELD, puppet(supporter, main));

        assertFalse(api.any(SNAPSHOT));
        assertEquals(supportersOwn, Character.get(supporter).getKey());
    }

    @Test
    void aCharacterTakenByTheTimeTheLoadArrivesIsHeld() {
        deferTasks = true;
        CompletableFuture<PuppetResult> first = Puppets.puppet(third, second);
        CompletableFuture<PuppetResult> late = Puppets.puppet(supporter, second);
        runDeferred();

        assertEquals(PuppetResult.PUPPETED, first.join());
        assertEquals(PuppetResult.HELD, late.join());
        assertEquals(supportersOwn, Character.get(supporter).getKey());
    }

    // F5 Not found

    @Test
    void aMissingOrDisabledCharacterIsNotFound() {
        assertEquals(PuppetResult.NOT_FOUND, puppet(supporter, new CharacterKey(ownerId, "gone")));
        assertEquals(PuppetResult.NOT_FOUND, puppet(supporter, new CharacterKey(ownerId, "retired")));

        assertEquals(supportersOwn, Character.get(supporter).getKey());
        verifyNoInteractions(plugin.selectionMenu);
    }

    // F6 The ticket's in-flight drop: the owner logs in or out mid-load

    @Test
    void theOwnerLoggingInMidLoadIsReportedAsInterrupted() {
        deferTasks = true;
        CompletableFuture<PuppetResult> result = Puppets.puppet(supporter, second); // owner offline
        bukkit.when(() -> Bukkit.getPlayer(ownerId)).thenReturn(owner);
        runDeferred();

        assertEquals(PuppetResult.INTERRUPTED, result.join());
        assertEquals(supportersOwn, Character.get(supporter).getKey());
        assertNull(CharacterHolders.holderOf(second));
    }

    @Test
    void theOwnerLoggingOutMidLoadIsReportedAsInterrupted() {
        join(owner);
        deferTasks = true;
        CompletableFuture<PuppetResult> result = Puppets.puppet(supporter, second);
        bukkit.when(() -> Bukkit.getPlayer(ownerId)).thenReturn(null);
        runDeferred();

        assertEquals(PuppetResult.INTERRUPTED, result.join());
        assertEquals(supportersOwn, Character.get(supporter).getKey());
    }

    // F7 The body's side

    @Test
    void theBodyQuittingMidLoadIsReportedAsInterrupted() {
        deferTasks = true;
        CompletableFuture<PuppetResult> result = Puppets.puppet(supporter, second);
        backend.disconnect(supporter);
        bukkit.when(() -> Bukkit.getPlayer(supporterId)).thenReturn(null);
        runDeferred();

        assertEquals(PuppetResult.INTERRUPTED, result.join());
        assertNull(CharacterHolders.holderOf(second));
    }

    @Test
    void aNewerLoadForTheBodyReplacesTheOlderOne() {
        deferTasks = true;
        CompletableFuture<PuppetResult> older = Puppets.puppet(supporter, second);
        CompletableFuture<PuppetResult> newer = Puppets.puppet(supporter, main);
        runDeferred();

        assertEquals(PuppetResult.INTERRUPTED, older.join());
        assertEquals(PuppetResult.PUPPETED, newer.join());
        assertEquals(main, Character.get(supporter).getKey());
        assertNull(CharacterHolders.holderOf(second));
    }

    // F8 Stale read

    @Test
    void theOwnersRecordChangingMidLoadIsReportedAsInterrupted() {
        deferTasks = true;
        CompletableFuture<PuppetResult> result = Puppets.puppet(supporter, second);
        backend.submit(owner, new CharacterRequest.Update(ownerId.toString(), "second", 2, new Object[]{"Renamed"}));
        runDeferred();

        assertEquals(PuppetResult.INTERRUPTED, result.join());
        assertEquals(supportersOwn, Character.get(supporter).getKey());
    }

    // F9 Cancelled

    @Test
    void aCancelledLoadIsRefused() {
        doAnswer(call -> {
            if (call.getArgument(0) instanceof LoadCharacterEvent event) event.setCancelled(true);
            return null;
        }).when(events).callEvent(any());

        assertEquals(PuppetResult.REFUSED, puppet(supporter, second));
        assertEquals(supportersOwn, Character.get(supporter).getKey());
    }

    // F10 Storage

    @Test
    void aStorageFailureIsReportedAsFailed() {
        api.failSnapshots = true;

        assertEquals(PuppetResult.FAILED, puppet(supporter, second));
        assertEquals(supportersOwn, Character.get(supporter).getKey());
    }

    @Test
    void scmClosingMidLoadIsReportedAsFailed() {
        deferTasks = true;
        CompletableFuture<PuppetResult> result = Puppets.puppet(supporter, second);

        backend.close();

        assertEquals(PuppetResult.FAILED, result.getNow(null), "completed when SCM closes, not left pending");
        runDeferred();
        assertEquals(supportersOwn, Character.get(supporter).getKey());
    }

    // E1 release

    @Test
    void releaseSavesTheBorrowedCharacterWhereItStandsAndReturnsTheBodyToItsOwn() {
        puppet(supporter, second);
        when(supporter.getLocation()).thenAnswer(call -> new Location(null, 55, 5, 0));
        writes.clear(); api.forget();
        deferTasks = true; // the body's own character arrives on a later tick, as on a server

        assertEquals(ReleaseResult.RELEASED, Puppets.release(supporter));

        assertEquals(file(second), writes.getFirst().target());
        assertEquals(new Location(null, 55, 5, 0), location(writes.getFirst()));
        verify(plugin.nametagManager).unnicknamePlayer(supporter);
        // Fired once the borrowed character was off, before the body's own arrived: the body wore nothing.
        assertLeft(supporter, second, Reason.RELEASED, null);
        assertNull(left.getFirst()[4], "nobody wears the borrowed character when the event fires");
        assertNull(Character.get(supporter));
        runDeferred();
        assertEquals(supportersOwn, Character.get(supporter).getKey(), "its own last-played character, as a normal switch");
        verify(supporter).teleport(new Location(null, 30, 65, 0));
        assertEquals(List.of(supporterId + "/own"), api.pointerWrites()); // unchanged; the owner's never written
        assertNull(Puppets.borrowedBy(supporter));
    }

    @Test
    void releaseClearsTheBodysInventorySoTheBorrowedItemsAreNotOnTwoBodies() {
        plugin.getConfig().set("manageInventories", true);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(supporter.getInventory()).thenReturn(inventory);
        try (MockedStatic<InventoryUtility> items = mockStatic(InventoryUtility.class)) {
            items.when(() -> InventoryUtility.inventoryToPaperYaml(any(org.bukkit.inventory.Inventory.class))).thenReturn("items");
            puppet(supporter, second);
            clearInvocations(inventory);
            deferTasks = true;

            Puppets.release(supporter);

            verify(inventory).clear();
            assertTrue(writes.stream().anyMatch(w -> w.target().equals(file(second)) && w.content().contains("items")),
                    "saved with its items before they were cleared");
        }
    }

    @Test
    void releasingABodyThatIsNotPuppetingDoesNothing() {
        assertEquals(ReleaseResult.NOT_PUPPETED, Puppets.release(supporter));
        assertEquals(ReleaseResult.NOT_PUPPETED, Puppets.release(third)); // wears nothing

        assertEquals(supportersOwn, Character.get(supporter).getKey());
        assertEquals(List.of(), writes);
        assertFalse(api.any(SNAPSHOT));
        assertEquals(List.of(), left);
    }

    // E2 Another load onto the body

    @Test
    void theBodysOwnCharSwitchEndsThePuppet() {
        puppet(supporter, second);
        writes.clear();

        backend.submit(supporter, new CharacterRequest.Select(supporterId.toString(), "own"));

        assertEquals(List.of(file(second)), writes.stream().map(PendingWrite::target).toList()); // saved to the owner
        assertLeft(supporter, second, Reason.LOADED_OTHER, supportersOwn);
        assertNull(CharacterHolders.holderOf(second));
    }

    @Test
    void puppetingAnotherCharacterEndsTheFirstPuppet() {
        puppet(supporter, second);

        assertEquals(PuppetResult.PUPPETED, puppet(supporter, main));

        assertLeft(supporter, second, Reason.LOADED_OTHER, main);
    }

    @Test
    void aPuppetFoundOfflineByTheAutosaveCountsAsAQuit() {
        puppet(supporter, second);
        when(supporter.isOnline()).thenReturn(false); // missed its quit event

        Character.saveAll();

        assertLeft(supporter, second, Reason.QUIT, null);
        assertNull(CharacterHolders.holderOf(second));
    }

    @Test
    void aListenerThatTakesTheNewCharacterOffAgainMeansThePuppetDidNotHappen() {
        puppet(supporter, second);
        doAnswer(call -> {
            if (call.getArgument(0) instanceof PuppetLeaveEvent event && event.getReason() == Reason.LOADED_OTHER)
                Puppets.release(event.getPlayer()); // e.g. ending the old dance releases the body
            return null;
        }).when(events).callEvent(any());

        assertEquals(PuppetResult.INTERRUPTED, puppet(supporter, main));
        assertNull(CharacterHolders.holderOf(main));
    }

    // E4 Owner joins as it

    @Test
    void theOwnerJoiningAsItEndsThePuppet() {
        puppet(supporter, second);
        api.lastPlayed.put(ownerId.toString(), "second");
        deferTasks = true;

        join(owner);
        runDeferred();

        assertLeft(supporter, second, Reason.OWNER_JOINED, null);
        assertNull(left.getFirst()[4], "released before the owner loads it");
        assertEquals(second, Character.get(owner).getKey());
        assertEquals(supportersOwn, Character.get(supporter).getKey());
    }

    // E5 chardisable

    @Test
    void chardisableEndsThePuppet() {
        puppet(supporter, second);
        bukkit.when(() -> Bukkit.getPlayer("Supporter")).thenReturn(supporter);

        new ConsoleCommandCharDisable().execute(mock(CommandSender.class), "chardisable", new String[]{"Supporter"});

        assertLeft(supporter, second, Reason.LOCKED_OUT, null);
    }

    // E6 Not leaving

    @Test
    void reloadingTheSameBorrowedCharacterOrSwitchingOwnCharactersFiresNothing() {
        puppet(supporter, second);
        assertEquals(PuppetResult.PUPPETED, puppet(supporter, second));

        join(owner);
        backend.submit(owner, new CharacterRequest.Select(ownerId.toString(), "main")); // own character, reloaded
        Character.remove(owner, Reason.QUIT); // quitting on an own character
        Character.remove(third, Reason.QUIT); // wearing nothing

        assertEquals(List.of(), left);
    }

    // L1-L3 Character lists

    @Test
    void anOfflineOwnersListLeavesOutDisabledCharactersAndMarksLastPlayed() {
        List<ListedCharacter> list = CharacterLists.of(ownerId).join();

        assertEquals(List.of(main, second), list.stream().map(ListedCharacter::key).toList());
        assertEquals(List.of(true, false), list.stream().map(ListedCharacter::current).toList());
        assertEquals("Main Hero", list.getFirst().name());
        assertEquals("stored-texture", list.getFirst().texture());
        assertFalse(api.any("INSERT INTO scm.owners"), "listing must not create the owner");
        assertEquals(List.of(), Character.getPlayerCharacters(ownerId), "no Character list entries are built");
        assertEquals(List.of(), CharacterLists.of(UUID.randomUUID()).join());
    }

    @Test
    void anOnlineOwnersCurrentCharacterIsTheOneTheyWearUnlessItIsBorrowed() {
        join(owner);
        backend.submit(owner, new CharacterRequest.Select(ownerId.toString(), "second")); // the fake keeps last-played at main
        assertEquals(List.of(false, true), CharacterLists.of(ownerId).join().stream().map(ListedCharacter::current).toList());

        assertEquals(PuppetResult.PUPPETED, puppet(owner, thirdsOwn)); // the owner wears someone else's character
        assertEquals(List.of(true, false), CharacterLists.of(ownerId).join().stream().map(ListedCharacter::current).toList());
    }

    @Test
    void aListThatCannotBeReadFails() {
        api.failSnapshots = true;
        assertThrows(CompletionException.class, () -> CharacterLists.of(ownerId).join());

        api.failSnapshots = false;
        deferTasks = true;
        CompletableFuture<List<ListedCharacter>> pending = CharacterLists.of(supporterId);
        backend.close();
        assertTrue(pending.isCompletedExceptionally(), "completed when SCM closes, not left pending");
    }

    private void assertLeft(Player body, CharacterKey key, Reason reason, CharacterKey wornAfter) {
        assertEquals(1, left.size(), "exactly one leave event");
        Object[] event = left.getFirst();
        assertSame(body, event[0]);
        assertEquals(key, event[1]);
        assertEquals(reason, event[2]);
        assertEquals(wornAfter, event[3], "what the body wore when the event fired");
    }

    private PuppetResult puppet(Player body, CharacterKey key) {
        return Puppets.puppet(body, key).join();
    }

    /** Runs the queued main-thread tasks in order, like later ticks; tasks they schedule run after them. */
    private void runDeferred() {
        while (!deferred.isEmpty()) deferred.remove(0).run();
        deferTasks = false;
    }

    /** {@code player} is online and asks for its last-played character. */
    private void join(Player player) {
        bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
        backend.submit(player, new CharacterRequest.Join(player.getUniqueId().toString()));
    }

    private Path file(CharacterKey key) {
        return data.resolve(key.owner().toString()).resolve(key.characterId() + ".yml");
    }

    private static Location location(PendingWrite write) {
        YamlConfiguration yaml = new YamlConfiguration();
        try { yaml.loadFromString(write.content()); } catch (Exception e) { throw new AssertionError(e); }
        return yaml.getLocation("location");
    }

    private Player player(UUID id, String name, double x) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenAnswer(call -> new Location(null, x, 0, 0));
        when(player.hasPermission("sneakycharacters.admin.bypass.characterlocks")).thenReturn(true);
        bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
        return player;
    }

    private static Map<String,Object> row(String id, String name, String lastPlayed, boolean enabled) {
        Map<String,Object> row = new HashMap<>();
        row.put("last_played_character", lastPlayed);
        row.put("revision", 0L);
        row.put("character_id", id);
        row.put("enabled", enabled);
        row.put("name", name);
        row.put("skin", "https://skins/" + id + ".png"); // a skin, so loads skip default-skin initialisation
        row.put("texture", "stored-texture");
        row.put("signature", "stored-signature");
        return row;
    }

    private void gameplayFile(CharacterKey key, double x) throws Exception {
        File file = file(key).toFile();
        Files.createDirectories(file.getParentFile().toPath());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("location", new Location(null, x, 64, 0));
        yaml.save(file);
    }
}
