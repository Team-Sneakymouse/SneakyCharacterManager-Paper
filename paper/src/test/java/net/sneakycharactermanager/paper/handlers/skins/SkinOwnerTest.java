package net.sneakycharactermanager.paper.handlers.skins;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import com.destroystokyo.paper.profile.ProfileProperty;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.util.ProxyMessagingUtil;
import net.sneakycharactermanager.paper.util.SkinUtil;

/** A skin is for one character (owner + ID) from request to result, whatever its wearer does meanwhile. */
class SkinOwnerTest {
    static final String URL = "https://skins/b.png";
    static final String TEXTURE_URL = "https://textures/b";

    final UUID wearerId = UUID.randomUUID();
    final UUID ownerId = UUID.randomUUID();
    final CharacterKey borrowed = new CharacterKey(ownerId, "borrowed");
    final CharacterKey wearersOwn = new CharacterKey(wearerId, "borrowed"); // same ID, different owner
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
        skins.when(() -> SkinUtil.getTextureUrl(any())).thenReturn(TEXTURE_URL);
        when(wearer.getUniqueId()).thenReturn(wearerId);
        when(wearer.isOnline()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        proxy.close(); characters.close(); skins.close(); cache.close(); bukkit.close(); plugins.close();
    }

    /** Bug 1: a skin resolving after the wearer switched character went to the character worn on arrival. */
    @ParameterizedTest
    @ValueSource(strings = {"skin-id", ""})
    void skinResolvedAfterSwitchingAwayIsSavedToTheRequestedCharacterOnly(String skinId) {
        CompletableFuture<SkinCache.ResolveResult> resolve = new CompletableFuture<>();
        cache.when(() -> SkinCache.resolve(wearer, URL, false)).thenReturn(resolve);
        SkinApplyService.requestSkin(wearer, borrowed, URL, false, SkinQueue.PRIO_SKIN, SkinApplyContext.defaults());

        Character switchedTo = mock(Character.class); // the wearer's own character, same ID
        when(switchedTo.getKey()).thenReturn(wearersOwn);
        characters.when(() -> Character.get(wearer)).thenReturn(switchedTo);
        characters.when(() -> Character.isWearing(wearer, wearersOwn)).thenReturn(true);
        resolve.complete(hit(skinId));

        if (skinId.isEmpty()) {
            proxy.verify(() -> ProxyMessagingUtil.sendByteArray(wearer, "updateCharacter", ownerId.toString(), "borrowed", 1,
                    TEXTURE_URL, "", "texture", "signature", false));
        } else {
            cache.verify(() -> SkinCache.updateCharacterSkin(wearer, borrowed, "skin-id", false));
        }
        cache.verify(() -> SkinCache.updateCharacterSkin(any(), eq(wearersOwn), anyString(), anyBoolean()), never());
        skins.verify(() -> SkinUtil.applySkin(any(), any()), never());
        verify(switchedTo, never()).setSkin(anyString());
        verify(plugin.skinStateManager).forgetCharacter(wearerId, borrowed);
    }

    @Test
    void skinResolvedWhileStillWornIsAppliedAndSaved() {
        CompletableFuture<SkinCache.ResolveResult> resolve = new CompletableFuture<>();
        cache.when(() -> SkinCache.resolve(wearer, URL, false)).thenReturn(resolve);
        Character worn = mock(Character.class);
        characters.when(() -> Character.get(wearer)).thenReturn(worn);
        characters.when(() -> Character.isWearing(wearer, borrowed)).thenReturn(true);
        SkinApplyService.requestSkin(wearer, borrowed, URL, false, SkinQueue.PRIO_LOAD, SkinApplyContext.defaults());

        resolve.complete(hit("skin-id"));

        skins.verify(() -> SkinUtil.applySkin(wearer, property));
        verify(worn).setSkin(TEXTURE_URL);
        cache.verify(() -> SkinCache.updateCharacterSkin(wearer, borrowed, "skin-id", false));
        assertEquals(borrowed, plugin.skinStateManager.current(wearerId).characterKey());
    }

    /** Bug 2: a queued job matched URL + wearer + ID, so another owner's same-ID job was reused. */
    @Test
    void queuedSkinIsNeverReusedForAnotherOwnersCharacter() {
        SkinData borrowedJob = SkinData.getOrCreate(URL, "", false, SkinQueue.PRIO_LOAD, wearer, borrowed, "Name");
        SkinData ownJob = SkinData.getOrCreate(URL, "", false, SkinQueue.PRIO_ONLINE, wearer, wearersOwn, "Name");
        SkinData again = SkinData.getOrCreate(URL, "", false, SkinQueue.PRIO_ONLINE, wearer, borrowed, "Name");

        assertNotSame(borrowedJob, ownJob);
        assertEquals(borrowed, borrowedJob.getCharacterKey());
        assertEquals(wearersOwn, ownJob.getCharacterKey());
        assertSame(borrowedJob, again);
    }

    /** Bug 4: raising a job moved the first entry matching wearer + ID, which could be another owner's. */
    @Test
    void raisingASkinRaisesOnlyThatCharactersJob() {
        SkinData own = SkinData.getOrCreate(URL, "", false, SkinQueue.PRIO_ONLINE, wearer, wearersOwn, "Name");
        SkinData other = SkinData.getOrCreate(URL, "", false, SkinQueue.PRIO_ONLINE, wearer, borrowed, "Name");

        // reuse at a higher priority raises the matched job, not the first one in its bucket
        assertSame(other, SkinData.getOrCreate(URL, "", false, SkinQueue.PRIO_LOAD, wearer, borrowed, "Name"));
        assertEquals(SkinQueue.PRIO_LOAD, other.getPriority());
        assertEquals(SkinQueue.PRIO_ONLINE, own.getPriority());

        // the load-time raise matches the owner too
        plugin.skinQueue.updatePriority(wearer, wearersOwn, SkinQueue.PRIO_SKIN);
        assertEquals(SkinQueue.PRIO_SKIN, own.getPriority());
        assertEquals(SkinQueue.PRIO_LOAD, other.getPriority());
        assertTrue(plugin.skinQueue.getQueue().get(SkinQueue.PRIO_LOAD).contains(other));
        assertFalse(plugin.skinQueue.getQueue().get(SkinQueue.PRIO_ONLINE).contains(other));

        plugin.skinQueue.removePendingForCharacter(wearer, borrowed);
        assertFalse(plugin.skinQueue.getQueue().get(SkinQueue.PRIO_LOAD).contains(other));
        assertTrue(plugin.skinQueue.getQueue().get(SkinQueue.PRIO_SKIN).contains(own));
    }

    /** A character-list refresh (e.g. after switching away) drops preloads, never a pending /skin change. */
    @Test
    void droppingPendingJobsKeepsExplicitSkinChanges() {
        SkinData preload = SkinData.getOrCreate("https://skins/old.png", "", false, SkinQueue.PRIO_ONLINE, wearer, borrowed, "Name");
        SkinData requested = SkinData.getOrCreate(URL, "", false, SkinQueue.PRIO_SKIN, wearer, borrowed, "Name");

        plugin.skinQueue.removePendingForCharacter(wearer, borrowed);

        assertFalse(plugin.skinQueue.getQueue().get(SkinQueue.PRIO_ONLINE).contains(preload));
        assertTrue(plugin.skinQueue.getQueue().get(SkinQueue.PRIO_SKIN).contains(requested));
    }

    @Test
    void mineSkinResultIsSavedToTheJobsCharacter() {
        SkinData job = SkinData.getOrCreate(URL, "", false, SkinQueue.PRIO_LOAD, wearer, borrowed, "Name");
        job.setPendingSkinId("skin-id");

        SkinApplyService.onMineSkinApplied(job, property);

        cache.verify(() -> SkinCache.updateCharacterSkin(wearer, borrowed, "skin-id", false));
    }

    /** The MineSkin path follows the same rule: a /skin job finishing after a switch only updates its character. */
    @Test
    void mineSkinJobFinishingAfterASwitchIsNotPutOnTheBody() throws Exception {
        SkinData job = SkinData.getOrCreate(URL, "", false, SkinQueue.PRIO_SKIN, wearer, borrowed, "Name");
        job.setPendingSkinId("skin-id");
        finished(job);
        Character switchedTo = mock(Character.class);
        characters.when(() -> Character.get(wearer)).thenReturn(switchedTo);

        job.apply();

        skins.verify(() -> SkinUtil.applySkin(any(), any()), never());
        verify(switchedTo, never()).setSkin(anyString());
        verify(plugin.skinStateManager).forgetCharacter(wearerId, borrowed);
        cache.verify(() -> SkinCache.updateCharacterSkin(wearer, borrowed, "skin-id", false));
        assertFalse(plugin.skinQueue.getQueue().get(SkinQueue.PRIO_SKIN).contains(job));
    }

    @Test
    void skinHistoryIsOnlyRestoredOntoTheSameOwnersCharacter() {
        SkinStateManager history = new SkinStateManager();
        history.record(wearer, "Regular", "t", "s", wearersOwn, "url", false);
        assertNull(history.latestForCharacter(wearerId, borrowed));
        assertNotNull(history.latestForCharacter(wearerId, wearersOwn));

        history.forgetCharacter(wearerId, wearersOwn);
        assertNull(history.latestForCharacter(wearerId, wearersOwn));
        assertNull(history.current(wearerId));
    }

    private static SkinCache.ResolveResult hit(String skinId) {
        return new SkinCache.ResolveResult(SkinCache.ResolveStatus.HIT, skinId, TEXTURE_URL, "texture", "signature", "");
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
