package net.sneakycharactermanager.paper.handlers.skins;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.destroystokyo.paper.profile.ProfileProperty;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.CharacterLoader;
import net.sneakycharactermanager.paper.util.ProxyMessagingUtil;
import net.sneakycharactermanager.paper.util.SkinUtil;

/** A newer skin for a character wins over older history and older queued jobs for that same character. */
class SkinSupersedeTest {
    static final String URL = "https://skins/new.png";

    final UUID wearerId = UUID.randomUUID();
    final UUID ownerId = UUID.randomUUID();
    final CharacterKey key = new CharacterKey(ownerId, "char");
    final CharacterKey otherKey = new CharacterKey(wearerId, "char"); // same ID, different owner
    final Player wearer = mock(Player.class);
    final SneakyCharacterManager plugin = mock(SneakyCharacterManager.class);
    final ProfileProperty property = new ProfileProperty("textures", "texture", "signature");
    MockedStatic<SneakyCharacterManager> plugins;
    MockedStatic<Bukkit> bukkit;
    MockedStatic<SkinCache> cache;
    MockedStatic<SkinUtil> skins;
    MockedStatic<Character> characters;
    MockedStatic<ProxyMessagingUtil> proxy;

    @BeforeEach
    void setUp() {
        plugins = mockStatic(SneakyCharacterManager.class);
        bukkit = mockStatic(Bukkit.class);
        cache = mockStatic(SkinCache.class);
        skins = mockStatic(SkinUtil.class);
        characters = mockStatic(Character.class);
        proxy = mockStatic(ProxyMessagingUtil.class);
        plugins.when(SneakyCharacterManager::getInstance).thenReturn(plugin);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        plugin.skinStateManager = spy(new SkinStateManager());
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            ((Runnable) call.getArgument(1)).run();
            return null;
        });
        plugin.skinQueue = new SkinQueue();
        skins.when(() -> SkinUtil.getTextureUrl(any())).thenReturn("https://textures/new");
        when(wearer.getUniqueId()).thenReturn(wearerId);
        when(wearer.isOnline()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        proxy.close(); characters.close(); skins.close(); cache.close(); bukkit.close(); plugins.close();
    }

    // --- SkinStateManager: history stays per body, but never outlives a newer skin for the same character ---

    /** Guards: another body's older session state for a character was restored over that character's newer skin. */
    @Test
    void newSkinForACharacterDropsThatCharacterFromOtherBodiesHistoryOnly() {
        Player a = body(), b = body();
        UUID aId = a.getUniqueId(), bId = b.getUniqueId();
        SkinStateManager history = new SkinStateManager();
        history.record(a, "Regular", "a-other", "s", otherKey, "a-other-url", false);
        history.record(a, "Regular", "a-k", "s", key, "a-k-url", false);

        // A uniform doesn't change the character's record, so A's base skin history for K stays.
        history.record(b, "Uniform", "b-u", "s", key, "b-u-url", true);
        assertNotNull(history.latestForCharacter(aId, key));

        history.record(b, "Regular", "b-other", "s", otherKey, "b-other-url", false);
        // B's otherKey change drops A's otherKey entry, but not A's K.
        assertNull(history.latestForCharacter(aId, otherKey));
        assertNotNull(history.latestForCharacter(aId, key));

        history.record(a, "Regular", "a-other2", "s", otherKey, "a-other2-url", false); // A's otherKey back
        history.record(b, "Regular", "b-k", "s", key, "b-k-url", false);

        assertNull(history.latestForCharacter(aId, key), "A's older skin for K must not be restored any more");
        assertEquals("b-k", history.latestForCharacter(bId, key).texture());
        assertEquals("a-other2", history.latestForCharacter(aId, otherKey).texture(), "other characters are untouched");
    }

    /** Guards: a skin saved to a character nobody wears left stale session states for it on every body. */
    @Test
    void forgettingACharacterClearsItFromEveryBodyAndKeepsOtherCharacters() {
        Player a = body(), b = body();
        UUID aId = a.getUniqueId(), bId = b.getUniqueId();
        SkinStateManager history = new SkinStateManager();
        history.record(a, "Regular", "a-other", "s", otherKey, "a-other-url", false);
        history.record(b, "Regular", "b-k", "s", key, "b-k-url", false);
        history.record(a, "Uniform", "a-u", "s", key, "a-u-url", true); // both bodies now hold K
        assertNotNull(history.latestForCharacter(aId, key));
        assertNotNull(history.latestForCharacter(bId, key));

        history.forgetCharacter(key);

        assertNull(history.latestForCharacter(aId, key));
        assertNull(history.latestForCharacter(bId, key));
        assertNull(history.current(aId), "A's current state was K's and is gone");
        assertNull(history.current(bId), "B's current state was K's and is gone");
        assertEquals("a-other", history.latestForCharacter(aId, otherKey).texture());
    }

    // --- SkinQueue.supersede / SkinData.apply ---

    /** Guards: an older preload/load/skin job for a character finished after a newer /skin and overwrote it. */
    @Test
    void supersedeDropsOnlyThisRequestersOlderBaseSkinJobsForThatCharacter() {
        Player other = body();
        SkinData online = job("https://skins/1.png", SkinQueue.PRIO_ONLINE, wearer, key);
        SkinData load = job("https://skins/2.png", SkinQueue.PRIO_LOAD, wearer, key);
        SkinData skin = job("https://skins/3.png", SkinQueue.PRIO_SKIN, wearer, key);
        SkinData uniformPriority = job("https://skins/4.png", SkinQueue.PRIO_UNIFORM, wearer, key);
        SkinData uniform = job("https://skins/5.png", SkinQueue.PRIO_LOAD, wearer, key);
        uniform.setUniformCacheInfo("https://skins/base.png", "hash");
        SkinData otherOwner = job("https://skins/6.png", SkinQueue.PRIO_LOAD, wearer, otherKey);
        SkinData otherRequester = job("https://skins/7.png", SkinQueue.PRIO_LOAD, other, key);

        plugin.skinQueue.supersede(wearer, key);

        assertFalse(plugin.skinQueue.contains(online));
        assertFalse(plugin.skinQueue.contains(load));
        assertFalse(plugin.skinQueue.contains(skin));
        assertTrue(plugin.skinQueue.contains(uniformPriority));
        assertTrue(plugin.skinQueue.contains(uniform));
        assertTrue(plugin.skinQueue.contains(otherOwner));
        assertTrue(plugin.skinQueue.contains(otherRequester));
    }

    /** Guards: a job superseded while its MineSkin request ran still applied and saved its (older) skin. */
    @Test
    void supersededJobFinishingLaterIsNeitherAppliedNorSaved() throws Exception {
        characters.when(() -> Character.isWearing(wearer, key)).thenReturn(true);
        SkinData old = job(URL, SkinQueue.PRIO_LOAD, wearer, key);
        old.setPendingSkinId("old-skin-id");
        finished(old);

        plugin.skinQueue.supersede(wearer, key);
        old.apply();

        skins.verify(() -> SkinUtil.applySkin(any(), any()), never());
        cache.verify(() -> SkinCache.updateCharacterSkin(any(), any(), anyString(), anyBoolean()), never());
        cache.verify(() -> SkinCache.register(any(), anyString(), any(), any(), any(), any()), never());
        verify(plugin.skinStateManager, never()).record(any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void stillQueuedJobFinishingIsAppliedAndSaved() throws Exception {
        characters.when(() -> Character.isWearing(wearer, key)).thenReturn(true);
        SkinData current = job(URL, SkinQueue.PRIO_LOAD, wearer, key);
        current.setPendingSkinId("skin-id");
        finished(current);

        current.apply();

        skins.verify(() -> SkinUtil.applySkin(wearer, property));
        cache.verify(() -> SkinCache.updateCharacterSkin(wearer, key, "skin-id", false));
        assertEquals(key, plugin.skinStateManager.current(wearerId).characterKey());
        assertFalse(plugin.skinQueue.contains(current));
    }

    // --- CharacterLoader ---

    /** Guards: /skin queued a new job while older jobs for the same character stayed queued and could win. */
    @Test
    void updateSkinSupersedesOlderJobsBeforeRequestingTheNewSkin() {
        List<String> calls = new ArrayList<>();
        plugin.skinQueue = mock(SkinQueue.class);
        doAnswer(call -> calls.add("supersede")).when(plugin.skinQueue).supersede(wearer, key);
        try (MockedStatic<SkinApplyService> service = mockStatic(SkinApplyService.class)) {
            service.when(() -> SkinApplyService.requestSkin(any(), any(), any(), any(), anyInt(), any()))
                    .thenAnswer(call -> calls.add("request:" + call.getArgument(4)));

            CharacterLoader.updateSkin(wearer, key, URL, false, "Label");
            CharacterLoader.updateSkin(wearer, key, URL, true, null, SkinQueue.PRIO_LOAD);

            service.verify(() -> SkinApplyService.requestSkin(eq(wearer), eq(key), eq(URL), eq(false),
                    eq(SkinQueue.PRIO_SKIN), argThat(ctx -> "Label".equals(ctx.skinStateLabel()))));
            service.verify(() -> SkinApplyService.requestSkin(eq(wearer), eq(key), eq(URL), eq(true),
                    eq(SkinQueue.PRIO_LOAD), any()));
        }
        assertEquals(List.of("supersede", "request:" + SkinQueue.PRIO_SKIN, "supersede", "request:" + SkinQueue.PRIO_LOAD), calls);
        verify(plugin.skinQueue, never()).supersede(wearer, otherKey);
    }

    /** Guards: restoring a session skin on load left preload jobs queued that later painted the record skin over it. */
    @Test
    void loadRestoringASessionSkinDropsPendingPreloadsForThatCharacter() {
        plugin.skinQueue = mock(SkinQueue.class);
        PluginManager events = mock(PluginManager.class);
        Server server = mock(Server.class);
        when(server.getPluginManager()).thenReturn(events);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        SkinState saved = plugin.skinStateManager.record(wearer, "Regular", "session-texture", "session-sig", key, "url", false);
        plugin.skinStateManager.record(wearer, "Regular", "own-texture", "own-sig", otherKey, "own-url", false);
        Character character = mock(Character.class);
        when(character.getKey()).thenReturn(key);
        when(character.getPlayer()).thenReturn(wearer);
        when(character.getName()).thenReturn("Name");

        assertTrue(CharacterLoader.loadCharacter(character));

        verify(plugin.skinQueue).removePendingForCharacter(wearer, key);
        verify(plugin.skinQueue, never()).removePendingForCharacter(wearer, otherKey);
        skins.verify(() -> SkinUtil.applySkin(wearer, new ProfileProperty("textures", "session-texture", "session-sig")));
        assertEquals(saved.id(), plugin.skinStateManager.current(wearerId).id());
    }

    private static Player body() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    private static SkinData job(String url, int priority, Player requester, CharacterKey key) {
        return SkinData.getOrCreate(url, "", false, priority, requester, key, "Name");
    }

    /** Marks a job as finished by MineSkin, as its HTTP poll would. */
    private static void finished(SkinData job) throws Exception {
        set(job, "texture", "texture");
        set(job, "signature", "signature");
        set(job, "isValid", true);
    }

    private static void set(SkinData job, String name, Object value) throws Exception {
        Field field = SkinData.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(job, value);
    }
}
