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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.character.CharacterHolders;
import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.api.character.PuppetLeaveEvent;
import net.sneakycharactermanager.paper.api.character.HeldCharacterSelectionHandler;
import net.sneakycharactermanager.paper.api.character.StorageMode;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.CharacterSelectionMenu;
import net.sneakycharactermanager.paper.handlers.character.LoadCharacterEvent;
import net.sneakycharactermanager.paper.handlers.nametags.NametagManager;
import net.sneakycharactermanager.paper.handlers.skins.SkinCache;
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;
import net.sneakycharactermanager.paper.handlers.skins.SkinStateManager;
import net.sneakycharactermanager.paper.handlers.titles.TitleRegistry;
import net.sneakycharactermanager.paper.util.SkinUtil;

/**
 * The holder rule on every path that can put a character on a body, end to end on the SneakyDB path. The owner's
 * character "second" is held by the supporter (through the borrow seam) unless a test says otherwise.
 *
 * <p>Cases (the README's "Holder rule" table):
 * <ol>
 * <li>Own join, not held: loads as before. Held: the holder's state is saved, the holder is released (it no longer
 *     wears the character and gets its own last-played), and the owner loads it where the holder stood, while the
 *     holder's save is still queued (save-before-load).</li>
 * <li>{@code /char} menu pick ({@code Select}) of a held character: no load and no last-played write; the selection
 *     handler gets it. Without a handler, or if it declines or throws, the owner is told it's being played. The
 *     owner's character-lock permission is checked first, as for any pick.</li>
 * <li>{@code /char <name>} ({@code SelectByName}): same as 2.</li>
 * <li>A pick that reaches Paper while held although the storage check passed (a race, or legacy mode, where the proxy
 *     moves last-played itself): the same handler, no load.</li>
 * <li>A pick the storage check refused, but the holder left before the result arrived: selected normally.</li>
 * <li>Borrow seam: refused onto another body while held, and refused while the owner wears it.</li>
 * <li>Staff and other plugins (a direct {@code Character.load()}, a forced load onto a non-owner): refused.</li>
 * <li>A load started from inside another load's events (a {@code LoadCharacterEvent} listener) can't put the same
 *     character on a second body, and a nested load of the same character (even onto the same body) is refused, so
 *     it can't end the outer load's reservation. The same holds for a load started by the owner-join release itself
 *     (for example from the holder's nameplate removal).</li>
 * <li>A released holder getting its own last-played character back doesn't take it from a third body; it gets the
 *     character menu. A {@code /char} pick queued behind a borrow of the same character doesn't move last-played.</li>
 * <li>The registry follows switches, quits ({@code Character.remove}, also used by {@code chardisable}) and the
 *     offline purge in {@code saveAll}.</li>
 * <li>An owner disabling a held character leaves it on the holder, whose saves still reach the owner's file.</li>
 * <li>Create gets a new ID, so it's never held; swap (ticket 05) is not built yet. Both are covered by the rule table
 *     in {@code HolderRuleTest}.</li>
 * </ol>
 */
class HolderRegistryTest {
    static final String TEXTURE_URL = "http://textures.minecraft.net/texture/stored";

    @TempDir Path data;
    final UUID ownerId = UUID.randomUUID();
    final UUID supporterId = UUID.randomUUID();
    final UUID thirdId = UUID.randomUUID();
    final CharacterKey main = new CharacterKey(ownerId, "main");
    final CharacterKey borrowed = new CharacterKey(ownerId, "second");
    final CharacterKey supportersOwn = new CharacterKey(supporterId, "own");
    final SneakyCharacterManager plugin = mock(SneakyCharacterManager.class);
    final Plugin dancing = mock(Plugin.class);
    final Database api = new Database();
    final List<PendingWrite> writes = new ArrayList<>();
    final List<Object[]> handled = new ArrayList<>();
    boolean holdWrites;
    Runnable beforeNextTask;
    /** While set, main-thread tasks wait in {@link #deferred} until the test runs them. */
    boolean deferTasks;
    final List<Runnable> deferred = new ArrayList<>();
    PluginManager events;
    Player owner, supporter, third;
    DatabaseBackend backend;
    MockedStatic<SneakyCharacterManager> plugins;
    MockedStatic<Bukkit> bukkit;
    MockedStatic<SkinUtil> skins;
    MockedStatic<SkinCache> cache;

    record PendingWrite(Path target, String content, CompletableFuture<Void> future) {}

    /** Fake SneakyDB: one snapshot per owner, with a settable last-played character. */
    static class Database extends SqlStoreTest.Api {
        final Map<String, List<String[]>> characters = new HashMap<>();
        final Map<String, String> lastPlayed = new HashMap<>();
        /** Owners whose next snapshot waits until the gate completes, like a slow database. */
        final Map<String, CompletableFuture<Void>> gates = new HashMap<>();
        @Override public CompletableFuture<List<Map<String,Object>>> query(String sql, List<?> args) {
            if (sql.contains("o.last_played_character, o.revision, c.*")) {
                statements.add(sql); bindings.add(args);
                String owner = (String) args.getFirst();
                List<Map<String,Object>> rows = new ArrayList<>();
                for (String[] c : characters.getOrDefault(owner, List.of())) rows.add(row(c[0], c[1], lastPlayed.get(owner)));
                CompletableFuture<Void> gate = gates.remove(owner);
                return gate == null ? CompletableFuture.completedFuture(rows) : gate.thenApply(unused -> rows);
            }
            if (sql.startsWith("SELECT * FROM scm.uniform_variants")) return CompletableFuture.completedFuture(List.of());
            return super.query(sql, args);
        }
        boolean selected() { return statements.stream().anyMatch(s -> s.contains("last_played_character = ?")); }
        void forget() { statements.clear(); bindings.clear(); }
    }

    @BeforeEach
    void setUp() throws Exception {
        api.ready.complete(null);
        api.characters.put(ownerId.toString(), List.of(new String[]{"main", "Main Hero"}, new String[]{"second", "Second Self"}));
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
        skins.when(() -> SkinUtil.getTextureUrl(any())).thenReturn(TEXTURE_URL);
        YamlConfiguration config = new YamlConfiguration();
        config.set("manageInventories", false);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getStorageMode()).thenReturn(StorageMode.SNEAKYDB);
        when(plugin.writeFile(any(), anyString())).thenAnswer(call -> {
            CompletableFuture<Void> future = holdWrites ? new CompletableFuture<>() : CompletableFuture.completedFuture(null);
            writes.add(new PendingWrite(call.getArgument(0), call.getArgument(1), future));
            return future;
        });
        plugin.skinStateManager = new SkinStateManager();
        plugin.skinQueue = mock(SkinQueue.class);
        plugin.nametagManager = mock(NametagManager.class);
        plugin.selectionMenu = mock(CharacterSelectionMenu.class);
        plugin.titleRegistry = mock(TitleRegistry.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            if (deferTasks) {
                deferred.add(call.getArgument(1));
                return null;
            }
            Runnable before = beforeNextTask;
            beforeNextTask = null;
            if (before != null) before.run();
            ((Runnable) call.getArgument(1)).run();
            return null;
        });
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call -> List.of());
        events = mock(PluginManager.class);
        Server server = mock(Server.class);
        when(server.getPluginManager()).thenReturn(events);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(Bukkit::getPluginManager).thenReturn(events);
        when(dancing.getName()).thenReturn("SneakyShadowDancing");
        owner = player(ownerId, "Driver", 1);
        supporter = player(supporterId, "Supporter", 2);
        third = player(thirdId, "Staff", 3);
        gameplayFile(main, 10);
        gameplayFile(borrowed, 20);
        gameplayFile(supportersOwn, 30);
        gameplayFile(new CharacterKey(thirdId, "third"), 40);
        backend = new DatabaseBackend(plugin, new SqlStore(api, 5));
        when(plugin.getCharacterBackend()).thenReturn(backend);
    }

    @AfterEach
    void tearDown() {
        for (PendingWrite write : writes) write.future().complete(null); // leave nothing in GameplayFile's queue
        CharacterHolders.clearSelectionHandler(dancing);
        backend.close();
        Character.remove(owner, PuppetLeaveEvent.Reason.QUIT); Character.remove(supporter, PuppetLeaveEvent.Reason.QUIT); Character.remove(third, PuppetLeaveEvent.Reason.QUIT);
        cache.close(); skins.close(); bukkit.close(); plugins.close();
    }

    // 1. Own join

    @Test
    void ownJoinOfAFreeCharacterLoadsAsBefore() {
        join(owner);

        assertEquals(main, Character.get(owner).getKey());
        assertSame(owner, CharacterHolders.holderOf(main));
        verify(owner).teleport(new Location(null, 10, 65, 0));
    }

    @Test
    void ownJoinOnAHeldCharacterSavesAndReleasesTheHolderThenLoadsItWhereTheHolderStood() {
        holdBorrowed();
        when(supporter.getLocation()).thenAnswer(call -> new Location(null, 77, 5, 0));
        api.lastPlayed.put(ownerId.toString(), "second"); // e.g. a Swap whose pointer write failed
        holdWrites = true; // nothing lands on disk during this test
        CompletableFuture<Void> holdersOwnArrives = new CompletableFuture<>();
        api.gates.put(supporterId.toString(), holdersOwnArrives); // the holder's own character comes back later

        join(owner);

        assertEquals(borrowed, Character.get(owner).getKey());
        assertSame(owner, CharacterHolders.holderOf(borrowed));
        // The owner appears where the holder stood (loads add one block), read from the save still queued.
        verify(owner).teleport(new Location(null, 77, 6, 0));
        assertTrue(writes.stream().allMatch(w -> !w.future().isDone()), "the holder's save must not have landed");
        assertEquals(new Location(null, 77, 5, 0), queuedLocation(borrowed));
        // Released before its own character arrives: it wears nothing and its plate is gone.
        assertNull(Character.get(supporter));
        verify(plugin.nametagManager).unnicknamePlayer(supporter);

        when(owner.getLocation()).thenAnswer(call -> new Location(null, 88, 5, 0));
        Character.get(owner).save();
        holdersOwnArrives.complete(null);

        // Back on its own last-played character, without writing the borrowed character again from its body.
        assertEquals(supportersOwn, Character.get(supporter).getKey());
        assertSame(supporter, CharacterHolders.holderOf(supportersOwn));
        assertSame(owner, CharacterHolders.holderOf(borrowed));
        assertEquals(new Location(null, 88, 5, 0), queuedLocation(borrowed));
    }

    @Test
    void aReleasedHolderDoesNotTakeItsOwnCharacterBackFromAnotherBody() {
        holdBorrowed();
        backend.loadBorrowed(third, supportersOwn); // someone else wears the supporter's own last-played character
        api.lastPlayed.put(ownerId.toString(), "second");

        join(owner);

        assertEquals(borrowed, Character.get(owner).getKey());
        assertSame(third, CharacterHolders.holderOf(supportersOwn), "the released holder's return is not a login");
        assertNull(Character.get(supporter));
        verify(plugin.selectionMenu).openMenu(supporter);
    }

    // 2. /char menu pick

    @Test
    void menuPickOfAHeldCharacterGoesToTheHandlerWithoutLoadingOrSelecting() {
        wearMain();
        holdBorrowed();
        CharacterHolders.setSelectionHandler(dancing, recordingHandler(true));
        api.forget();

        backend.submit(owner, new CharacterRequest.Select(ownerId.toString(), "second"));

        assertEquals(1, handled.size());
        assertArrayEquals(new Object[]{owner, borrowed, supporter}, handled.getFirst());
        assertEquals(main, Character.get(owner).getKey());
        assertSame(supporter, CharacterHolders.holderOf(borrowed));
        assertFalse(api.selected(), "the owner's last-played must not move to a held character");
        verify(owner, never()).sendMessage(any(Component.class));
    }

    @Test
    void menuPickOfAHeldCharacterWithoutAHandlerIsRefused() {
        wearMain();
        holdBorrowed();
        api.forget();

        backend.submit(owner, new CharacterRequest.Select(ownerId.toString(), "second"));

        assertEquals(main, Character.get(owner).getKey());
        assertSame(supporter, CharacterHolders.holderOf(borrowed));
        assertFalse(api.selected());
        verify(owner).sendMessage(text("Someone else is playing that character right now."));
    }

    @Test
    void aHandlerThatDeclinesOrThrowsLeavesTheRefusal() {
        wearMain();
        holdBorrowed();
        CharacterHolders.setSelectionHandler(dancing, recordingHandler(false));
        backend.submit(owner, new CharacterRequest.Select(ownerId.toString(), "second"));
        CharacterHolders.setSelectionHandler(dancing, (o, k, h) -> { throw new IllegalStateException("boom"); });
        backend.submit(owner, new CharacterRequest.Select(ownerId.toString(), "second"));

        assertEquals(1, handled.size());
        verify(owner, times(2)).sendMessage(text("Someone else is playing that character right now."));
        assertSame(supporter, CharacterHolders.holderOf(borrowed));
    }

    @Test
    void theHandlerGoesAwayWithItsPluginOnly() {
        wearMain();
        holdBorrowed();
        CharacterHolders.setSelectionHandler(dancing, recordingHandler(true));
        CharacterHolders.clearSelectionHandler(mock(Plugin.class)); // someone else's disable

        backend.submit(owner, new CharacterRequest.Select(ownerId.toString(), "second"));
        CharacterHolders.clearSelectionHandler(dancing);
        backend.submit(owner, new CharacterRequest.Select(ownerId.toString(), "second"));

        assertEquals(1, handled.size());
        verify(owner).sendMessage(text("Someone else is playing that character right now."));
    }

    @Test
    void aLockedHeldCharacterIsRefusedBeforeTheHandler() {
        wearMain();
        holdBorrowed();
        CharacterHolders.setSelectionHandler(dancing, recordingHandler(true));
        when(owner.hasPermission("sneakycharacters.admin.bypass.characterlocks")).thenReturn(false); // no lock permission

        backend.submit(owner, new CharacterRequest.Select(ownerId.toString(), "second"));

        assertEquals(List.of(), handled);
        verify(owner).sendMessage(text("You cannot access this character right now."));
    }

    // 3. /char <name>

    @Test
    void pickByNameOfAHeldCharacterGoesToTheHandlerWithoutSelecting() {
        wearMain();
        holdBorrowed();
        CharacterHolders.setSelectionHandler(dancing, recordingHandler(true));
        api.forget();

        backend.submit(owner, new CharacterRequest.SelectByName(ownerId.toString(), "second"));

        assertEquals(1, handled.size());
        assertEquals(main, Character.get(owner).getKey());
        assertFalse(api.selected());
    }

    // 4. A pick that reaches Paper while held (race, or legacy mode)

    @Test
    void aPickArrivingWhileHeldGoesToTheHandlerWithoutLoading() {
        wearMain();
        holdBorrowed();
        CharacterHolders.setSelectionHandler(dancing, recordingHandler(true));

        CharacterResultEffects.load(character(borrowed, owner), false);

        assertEquals(1, handled.size());
        assertEquals(main, Character.get(owner).getKey());
        assertSame(supporter, CharacterHolders.holderOf(borrowed));
    }

    // 5. Freed before the result arrived

    @Test
    void aPickRefusedByStorageButFreedSinceIsSelectedNormally() {
        wearMain();
        holdBorrowed();
        CharacterHolders.setSelectionHandler(dancing, recordingHandler(true));
        beforeNextTask = () -> Character.remove(supporter, PuppetLeaveEvent.Reason.QUIT); // the holder quits before the result reaches the main thread
        api.forget();

        backend.submit(owner, new CharacterRequest.Select(ownerId.toString(), "second"));

        assertEquals(List.of(), handled);
        assertEquals(borrowed, Character.get(owner).getKey());
        assertTrue(api.selected());
    }

    @Test
    void aPickQueuedBehindABorrowOfTheSameCharacterDoesNotMoveLastPlayed() {
        wearMain();
        CharacterHolders.setSelectionHandler(dancing, recordingHandler(true));
        deferTasks = true;
        backend.loadBorrowed(supporter, borrowed); // storage step done, not applied on the main thread yet
        api.forget();

        backend.submit(owner, new CharacterRequest.Select(ownerId.toString(), "second"));
        runDeferred();

        assertFalse(api.selected(), "the owner's last-played must not move to a character on its way to another body");
        assertSame(supporter, CharacterHolders.holderOf(borrowed));
        assertEquals(main, Character.get(owner).getKey());
        assertEquals(1, handled.size());
    }

    // 6. Borrow seam

    @Test
    void borrowingAHeldCharacterOntoAnotherBodyIsRefused() {
        holdBorrowed();

        backend.loadBorrowed(third, borrowed);

        assertNull(Character.get(third));
        assertSame(supporter, CharacterHolders.holderOf(borrowed));
        verify(third, never()).teleport(any(Location.class));
    }

    @Test
    void borrowingTheCharacterTheOwnerWearsIsRefused() {
        wearMain();

        backend.loadBorrowed(supporter, main);

        assertNull(Character.get(supporter));
        assertSame(owner, CharacterHolders.holderOf(main));
    }

    // 7. Staff and other plugins

    @Test
    void aDirectLoadByAnotherPluginOntoAnotherBodyIsRefused() {
        holdBorrowed();

        character(borrowed, third).load();

        assertNull(Character.get(third));
        assertSame(supporter, CharacterHolders.holderOf(borrowed));
    }

    @Test
    void aForcedLoadOntoANonOwnerIsRefused() {
        holdBorrowed();

        CharacterResultEffects.load(character(borrowed, third), true);

        assertNull(Character.get(third));
        assertSame(supporter, CharacterHolders.holderOf(borrowed));
    }

    @Test
    void aLoadStartedFromAnotherLoadsEventCannotTakeTheSameCharacter() {
        doAnswer(call -> {
            if (call.getArgument(0) instanceof LoadCharacterEvent event && event.getPlayer() == supporter)
                character(borrowed, third).load(); // a listener loading the same character onto another body
            return null;
        }).when(events).callEvent(any());

        backend.loadBorrowed(supporter, borrowed);

        assertSame(supporter, CharacterHolders.holderOf(borrowed));
        assertNull(Character.get(third));
        verify(third, never()).teleport(any(Location.class));
    }

    @Test
    void aCancelledNestedLoadOfTheSameCharacterKeepsTheOuterReservation() {
        AtomicInteger supporterEvents = new AtomicInteger();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof LoadCharacterEvent event && event.getPlayer() == supporter) {
                if (supporterEvents.getAndIncrement() == 0) {
                    character(borrowed, supporter).load(); // nested, same body; a listener cancels it
                    character(borrowed, third).load();     // then another body tries while the outer load runs
                } else {
                    event.setCancelled(true);
                }
            }
            return null;
        }).when(events).callEvent(any());

        backend.loadBorrowed(supporter, borrowed);

        assertSame(supporter, CharacterHolders.holderOf(borrowed));
        assertNull(Character.get(third));
        verify(third, never()).teleport(any(Location.class));
    }

    @Test
    void nothingTriggeredByReleasingTheHolderCanTakeTheCharacterBeforeTheOwner() {
        holdBorrowed();
        api.lastPlayed.put(ownerId.toString(), "second");
        doAnswer(call -> {
            character(borrowed, third).load(); // e.g. a listener on the holder's nameplate removal
            return null;
        }).when(plugin.nametagManager).unnicknamePlayer(supporter);

        join(owner);

        assertSame(owner, CharacterHolders.holderOf(borrowed));
        assertNull(Character.get(third));
        verify(third, never()).teleport(any(Location.class));
    }

    // 8. The registry follows what each body wears

    @Test
    void theRegistryFollowsSwitchesQuitsAndTheOfflinePurge() {
        holdBorrowed();
        backend.submit(supporter, new CharacterRequest.Select(supporterId.toString(), "own"));
        assertNull(CharacterHolders.holderOf(borrowed));
        assertSame(supporter, CharacterHolders.holderOf(supportersOwn));

        Character.remove(supporter, PuppetLeaveEvent.Reason.QUIT); // quit, chardisable
        assertFalse(CharacterHolders.isHeld(supportersOwn));

        wearMain();
        when(owner.isOnline()).thenReturn(false);
        Character.saveAll();
        assertFalse(CharacterHolders.isHeld(main));
    }

    // 9. Disabled while held

    @Test
    void aHeldCharacterItsOwnerDisablesStaysOnItsHolder() {
        holdBorrowed();
        writes.clear();

        backend.submit(owner, new CharacterRequest.Update(ownerId.toString(), "second", 3, new Object[]{false}));
        Character.get(supporter).save();

        assertEquals(borrowed, Character.get(supporter).getKey());
        assertSame(supporter, CharacterHolders.holderOf(borrowed));
        assertEquals(List.of(data.resolve(ownerId.toString()).resolve("second.yml")), writes.stream().map(PendingWrite::target).toList());
    }

    private void runDeferred() {
        deferTasks = false;
        while (!deferred.isEmpty()) deferred.remove(0).run();
    }

    private void join(Player player) {
        backend.submit(player, new CharacterRequest.Join(player.getUniqueId().toString()));
    }

    private void wearMain() {
        backend.submit(owner, new CharacterRequest.Select(ownerId.toString(), "main"));
        assertEquals(main, Character.get(owner).getKey());
    }

    private void holdBorrowed() {
        backend.loadBorrowed(supporter, borrowed);
        assertSame(supporter, CharacterHolders.holderOf(borrowed));
    }

    private HeldCharacterSelectionHandler recordingHandler(boolean result) {
        return (o, k, h) -> {
            handled.add(new Object[]{o, k, h});
            return result;
        };
    }

    private Location queuedLocation(CharacterKey key) {
        Path target = data.resolve(key.owner().toString()).resolve(key.characterId() + ".yml");
        String content = writes.stream().filter(w -> w.target().equals(target)).reduce((a, b) -> b).orElseThrow().content();
        YamlConfiguration yaml = new YamlConfiguration();
        try { yaml.loadFromString(content); } catch (Exception e) { throw new AssertionError(e); }
        return yaml.getLocation("location");
    }

    private static Component text(String plain) {
        return argThat(c -> c != null && PlainTextComponentSerializer.plainText().serialize(c).equals(plain));
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

    private static Character character(CharacterKey key, Player wearer) {
        return new Character(key, wearer, "Name", "", "", "", "", false, "", "", "");
    }

    private static Map<String,Object> row(String id, String name, String lastPlayed) {
        Map<String,Object> row = new HashMap<>();
        row.put("last_played_character", lastPlayed);
        row.put("revision", 0L);
        row.put("character_id", id);
        row.put("enabled", true);
        row.put("name", name);
        row.put("skin", "https://skins/" + id + ".png"); // a skin, so loads skip default-skin initialisation
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
