package net.sneakycharactermanager.paper.handlers.nametags;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.sneakycharactermanager.paper.api.nameplate.AttachResult;
import net.sneakycharactermanager.paper.api.nameplate.NameplateAttachments;
import net.sneakycharactermanager.paper.api.nameplate.NameplateHandle;
import net.sneakycharactermanager.paper.api.nameplate.ReleaseReason;
import net.sneakycharactermanager.paper.handlers.nametags.NameplateFixture.Plate;
import net.sneakycharactermanager.paper.handlers.titles.TitleRegistry;

/** The real NametagManager and the registered API object, driven through the fixture's Bukkit boundary. */
class NametagManagerAttachmentTest {

    private NameplateFixture f;
    private NameplateAttachments api;
    private Player owner;
    private Player a;
    private Player b;
    private Plugin racials;
    private Plugin other;

    @BeforeEach
    void setUp() {
        f = new NameplateFixture();
        api = new NameplateAttachmentsImpl(f.manager);
        owner = f.character("Owner");
        a = f.player("A");
        b = f.player("B");
        racials = plugin("SneakyRacials");
        other = plugin("Other");
    }

    @AfterEach
    void tearDown() {
        f.close();
    }

    private static Plugin plugin(String name) {
        Plugin plugin = mock(Plugin.class, name);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getName()).thenReturn(name);
        return plugin;
    }

    private static NameplateHandle attached(AttachResult result) {
        return result.attachedHandle().orElseThrow(() -> new AssertionError("refused: " + result));
    }

    private static AttachResult.Reason refusal(AttachResult result) {
        return result.refusedReason().orElseThrow(() -> new AssertionError("attached: " + result));
    }

    // ---------------------------------------------------------------------------------------------------- rebuilds

    @Test
    void rebuildGoesBackOntoTheAnchorWithBlankAndExclusionsKept() {
        f.nick(owner);
        Entity shell = f.anchor("shell");
        NameplateHandle handle = attached(api.attach(racials, owner, shell));
        assertSame(shell, f.plate(owner).vehicle);
        assertTrue(handle.setBlank(true));
        assertTrue(handle.setExcludedViewers(List.of(b.getUniqueId())));

        Plate first = f.plate(owner);
        f.manager.unnicknamePlayer(owner);
        assertTrue(first.removed);
        assertTrue(handle.isActive(), "a rebuild is not a release");

        f.nick(owner);
        Plate second = f.plate(owner);
        assertNotSame(first, second);
        assertSame(shell, second.vehicle);
        verify(b).hideEntity(f.plugin, second.display);
        verify(a, never()).hideEntity(f.plugin, second.display);

        second.trackers.addAll(List.of(a, b));
        f.ticks((int) NametagManager.INITIAL_TEXT_DELAY_TICKS);
        verify(second.entity).sendOff(a);
        verify(second.entity, never()).sendCharacter(any());
        verify(second.entity, never()).sendOff(b);
    }

    @Test
    void attachingWithoutAPlateAppliesWhenThePlateIsBuilt() {
        Entity shell = f.anchor("shell");
        NameplateHandle handle = attached(api.attach(racials, owner, shell));
        assertNull(f.plate(owner));

        f.nick(owner);
        assertSame(shell, f.plate(owner).vehicle);
        assertTrue(handle.isActive());
    }

    @Test
    void delayedInitialTextIsDroppedAfterUnnickOrNewerNick() {
        f.nick(owner);
        Plate plate = f.plate(owner);
        plate.trackers.add(a);
        f.manager.unnicknamePlayer(owner);
        f.ticks(10);
        verify(plate.entity, never()).sendCharacter(a);

        f.nick(owner);
        Plate rebuilt = f.plate(owner);
        rebuilt.trackers.add(a);
        f.ticks(2);
        f.nick(owner); // same display, text updated; the first refresh is now stale
        f.ticks(3);
        verify(rebuilt.entity, never()).sendCharacter(a);
        f.ticks(2);
        verify(rebuilt.entity, times(1)).sendCharacter(a);
    }

    @Test
    void failedConstructionLeavesNoMappedPlateAndNoEntity() {
        f.failNextPlate = new IllegalStateException("packet build");
        assertThrows(IllegalStateException.class, () -> f.nick(owner));
        assertNull(f.manager.getNickname(owner));
        assertTrue(f.plates.get(0).removed, "Nickname destroyed its entity");
    }

    @Test
    void unnickAllClearsEverythingAndReleasesEveryAttachment() {
        f.nick(owner);
        Plate plate = f.plate(owner);
        NameplateHandle handle = attached(api.attach(racials, owner, f.anchor("shell")));

        f.manager.unnickAll();
        assertTrue(plate.removed);
        assertNull(f.manager.getNickname(owner));
        assertEquals(Optional.of(ReleaseReason.SCM_DISABLED), handle.releaseReason());
        assertEquals(AttachResult.Reason.SCM_DISABLED, refusal(api.attach(racials, owner, f.anchor("other"))));

        f.manager.unnickAll();
        verify(plate.entity, times(1)).destroy();
    }

    // ---------------------------------------------------------------------------------------------------- text

    @Test
    void textGoesToTheDisplaysTrackersNotTheOwners() {
        f.nick(owner);
        Plate plate = f.plate(owner);
        plate.trackers.add(a);
        when(owner.getTrackedBy()).thenReturn(Set.of(b));

        Set<java.util.UUID> sent = f.manager.refreshNicknames(owner, plate.nickname, null);
        assertEquals(Set.of(a.getUniqueId()), sent);
        verify(plate.entity).sendCharacter(a);
        verify(plate.entity, never()).sendCharacter(b);

        f.manager.refreshNicknames(owner, plate.nickname, sent);
        verify(plate.entity, times(1)).sendCharacter(a);
    }

    @Test
    void preferencePathsAreUnchangedWithoutAnAttachment() {
        f.nick(owner);
        Plate plate = f.plate(owner);
        f.manager.refreshNickname(plate.nickname, a, NamesPreference.OFF);
        verify(plate.entity).sendOff(a);
        f.manager.refreshNickname(plate.nickname, a, NamesPreference.ON);
        verify(plate.entity).sendOn(a);
        f.manager.refreshNickname(plate.nickname, a, NamesPreference.CHARACTER);
        verify(plate.entity).sendCharacter(a);

        f.manager.setHidingOwnName(owner, true);
        f.manager.refreshNickname(plate.nickname, b, NamesPreference.ON);
        verify(plate.entity).sendHiddenOn(b);
        f.manager.refreshNickname(plate.nickname, b, NamesPreference.CHARACTER);
        verify(plate.entity).sendOff(b);
    }

    @Test
    void titleIsResolvedForTheBuiltPlate() {
        TitleRegistry titles = mock(TitleRegistry.class);
        when(titles.getDisplay("knight")).thenReturn("Sir");
        f.plugin.titleRegistry = titles;
        when(f.characterOf(owner).getTitle()).thenReturn("knight");
        when(owner.hasPermission(TitleRegistry.permissionNode("knight"))).thenReturn(true);

        f.nick(owner);
        verify(f.plate(owner).entity).updatePackets("Owner (character)", "Sir");
    }

    private TitleRegistry knightTitleEquipped() {
        TitleRegistry titles = new TitleRegistry();
        titles.replaceAll(java.util.Map.of("knight", "Sir"));
        f.plugin.titleRegistry = titles;
        when(f.characterOf(owner).getTitle()).thenReturn("knight");
        return titles;
    }

    @Test
    void existingTitleIsNotShownWithoutItsPermission() {
        knightTitleEquipped();

        f.nick(owner);
        verify(f.plate(owner).entity).updatePackets("Owner (character)", null);
        verify(f.plate(owner).entity, never()).updatePackets(any(), eq("Sir"));
    }

    @Test
    void deletedTitleIsNotShownEvenWithItsPermission() {
        knightTitleEquipped().replaceAll(java.util.Map.of());
        when(owner.hasPermission(TitleRegistry.permissionNode("knight"))).thenReturn(true);

        f.nick(owner);
        verify(f.plate(owner).entity).updatePackets("Owner (character)", null);
    }

    @Test
    void permittedTitleIsShownAndStoredTitleIsKept() {
        knightTitleEquipped();
        when(owner.hasPermission(TitleRegistry.permissionNode("knight"))).thenReturn(true);

        f.nick(owner);
        verify(f.plate(owner).entity).updatePackets("Owner (character)", "Sir");
        verify(f.characterOf(owner), never()).setTitle(any());
    }

    @Test
    void permissionChangesRefreshTheExistingPlatesTitleOnTheNextTick() {
        knightTitleEquipped();
        when(owner.hasPermission(TitleRegistry.permissionNode("knight"))).thenReturn(true);
        when(f.characterOf(owner).getDisplayName()).thenReturn("Owner (character)");
        f.nick(owner);
        Plate plate = f.plate(owner);
        verify(plate.entity).updatePackets("Owner (character)", "Sir");

        when(owner.hasPermission(TitleRegistry.permissionNode("knight"))).thenReturn(false);
        f.manager.refreshTitleSoon(owner.getUniqueId());
        verify(plate.entity, never()).updatePackets("Owner (character)", null);
        f.tick();
        assertSame(plate, f.plate(owner), "updated in place, not rebuilt");
        verify(plate.entity).updatePackets("Owner (character)", null);

        when(owner.hasPermission(TitleRegistry.permissionNode("knight"))).thenReturn(true);
        f.manager.refreshTitleSoon(owner.getUniqueId());
        f.tick();
        verify(plate.entity, times(2)).updatePackets("Owner (character)", "Sir");
        verify(f.characterOf(owner), never()).setTitle(any());
    }

    @Test
    void permissionRefreshBuildsNoPlateAndSkipsUntitledOrOfflineOwners() {
        knightTitleEquipped();
        f.manager.refreshTitleSoon(owner.getUniqueId());
        f.tick();
        assertNull(f.plate(owner), "no plate is created by a permission change");

        f.nick(owner);
        Plate plate = f.plate(owner);
        when(f.characterOf(owner).getTitle()).thenReturn("");
        f.manager.refreshTitleSoon(owner.getUniqueId());
        f.tick();
        verify(plate.entity, times(1)).updatePackets(any(), any());

        when(f.characterOf(owner).getTitle()).thenReturn("knight");
        f.manager.refreshTitleSoon(owner.getUniqueId());
        f.quit(owner);
        f.tick();
        verify(plate.entity, times(1)).updatePackets(any(), any());
    }

    @Test
    void permissionRefreshSendsTheNewTextToCurrentTrackersOnTheNextTick() {
        knightTitleEquipped();
        when(owner.hasPermission(TitleRegistry.permissionNode("knight"))).thenReturn(true);
        f.nick(owner);
        Plate plate = f.plate(owner);
        NameplateHandle handle = attached(api.attach(racials, owner, f.anchor("shell")));
        handle.setExcludedViewers(List.of(b.getUniqueId()));
        plate.trackers.addAll(List.of(a, b));
        f.ticks((int) NametagManager.INITIAL_TEXT_DELAY_TICKS);
        verify(plate.entity, atLeastOnce()).sendCharacter(a); // a already sees the titled plate
        assertEquals(0, f.pendingTasks());
        clearInvocations(plate.entity);

        when(owner.hasPermission(TitleRegistry.permissionNode("knight"))).thenReturn(false);
        f.manager.refreshTitleSoon(owner.getUniqueId());
        f.tick();

        var order = inOrder(plate.entity);
        order.verify(plate.entity).updatePackets("Owner (character)", null);
        order.verify(plate.entity).sendCharacter(a);
        verify(plate.entity, times(1)).sendCharacter(a);
        verify(plate.entity, never()).sendCharacter(b);
        verify(plate.entity, never()).sendOff(b);
        verify(plate.entity, never()).sendOn(b);
        assertSame(plate, f.plate(owner), "updated in place, not rebuilt");
        assertSame(handle, f.manager.currentAttachment(racials, owner).orElseThrow());
    }

    @Test
    void aSwapReTextsTheSamePlateNowAndAgainNextTickForNewTrackers() {
        f.nick(owner);
        Plate plate = f.plate(owner);
        plate.trackers.add(a);
        f.ticks((int) NametagManager.INITIAL_TEXT_DELAY_TICKS);
        clearInvocations(plate.entity);
        when(f.characterOf(owner).getDisplayName()).thenReturn("Swapped In");

        f.manager.showWornCharacter(owner);

        verify(plate.entity).updatePackets("Swapped In", null);
        verify(plate.entity).sendCharacter(a); // now, to who already tracks it
        plate.trackers.add(b); // starts tracking the plate at its new spot
        f.tick();
        verify(plate.entity).sendCharacter(b);
        assertSame(plate, f.plate(owner), "the same plate, not rebuilt");
        assertEquals(1, f.plates.size());
    }

    @Test
    void aSwapShowsNoNameOnABodyAPlacementListenerReleased() {
        f.nick(owner);
        Plate plate = f.plate(owner);
        // A mount listener releases the owner while the swap re-places the plate.
        when(plate.entity.mountOn(any(Entity.class))).thenAnswer(invocation -> {
            f.removeCharacter(owner);
            f.manager.unnicknamePlayer(owner);
            return true;
        });

        f.manager.showWornCharacter(owner);

        assertNull(f.manager.getNickname(owner), "no plate rebuilt with the character it no longer wears");
        assertEquals(1, f.plates.size());
    }

    @Test
    void aSwapBuildsAPlateForAnOwnerWithout() {
        f.manager.showWornCharacter(owner);

        assertNotNull(f.plate(owner));
        verify(f.plate(owner).entity).updatePackets("Owner (character)", null);
    }

    @Test
    void permissionRefreshDoesNotCancelPendingInitialText() {
        knightTitleEquipped();
        f.nick(owner);
        Plate plate = f.plate(owner);
        plate.trackers.add(a);
        long generation = f.manager.lifecycle(owner.getUniqueId());

        f.manager.refreshTitleSoon(owner.getUniqueId());
        f.tick();
        assertEquals(generation, f.manager.lifecycle(owner.getUniqueId()));
        verify(plate.entity, times(1)).sendCharacter(a); // the refresh itself
        f.ticks((int) NametagManager.INITIAL_TEXT_DELAY_TICKS);
        verify(plate.entity, times(2)).sendCharacter(a); // the plate's initial text still arrives
    }

    @Test
    void permissionRefreshSkipsASuppressedOwner() {
        knightTitleEquipped();
        f.nick(owner);
        Plate plate = f.plate(owner);
        plate.trackers.add(a);
        f.vanish(owner, "V");

        f.manager.refreshTitleSoon(owner.getUniqueId());
        f.tick();
        verify(plate.entity, times(1)).updatePackets(any(), any());
        verify(plate.entity, never()).sendCharacter(a);
    }

    @Test
    void permissionRefreshIgnoresSchedulerRejectionOnlyWhileDisabling() {
        org.bukkit.scheduler.BukkitScheduler scheduler = org.bukkit.Bukkit.getScheduler();
        doThrow(new org.bukkit.plugin.IllegalPluginAccessException("Plugin attempted to register task while disabled"))
                .when(scheduler).runTask(any(Plugin.class), any(Runnable.class));

        when(f.plugin.isEnabled()).thenReturn(true, false); // disabled between the check and the scheduling
        assertDoesNotThrow(() -> f.manager.refreshTitleSoon(owner.getUniqueId()));

        when(f.plugin.isEnabled()).thenReturn(true);
        assertThrows(org.bukkit.plugin.IllegalPluginAccessException.class,
                () -> f.manager.refreshTitleSoon(owner.getUniqueId()));

        doThrow(new IllegalStateException("unrelated")).when(scheduler).runTask(any(Plugin.class), any(Runnable.class));
        when(f.plugin.isEnabled()).thenReturn(true, false);
        assertThrows(IllegalStateException.class, () -> f.manager.refreshTitleSoon(owner.getUniqueId()));
    }

    @Test
    void excludedViewerGetsNoTextEvenFromPreferenceChanges() {
        f.nick(owner);
        Plate plate = f.plate(owner);
        NameplateHandle handle = attached(api.attach(racials, owner, f.anchor("shell")));
        handle.setExcludedViewers(List.of(a.getUniqueId()));

        f.manager.applyNamesPreference(a, NamesPreference.ON);
        verify(plate.entity, never()).sendOn(a);
        verify(a).hideEntity(f.plugin, plate.display);
    }

    // ---------------------------------------------------------------------------------------------------- API

    @Test
    void refusalsChangeNothing() {
        f.nick(owner);
        Plate plate = f.plate(owner);
        Entity shell = f.anchor("shell");

        Plugin disabled = plugin("Disabled");
        when(disabled.isEnabled()).thenReturn(false);
        assertEquals(AttachResult.Reason.CONSUMER_DISABLED, refusal(api.attach(disabled, owner, shell)));
        assertEquals(AttachResult.Reason.CONSUMER_DISABLED, refusal(api.attach(null, owner, shell)));

        assertEquals(AttachResult.Reason.ANCHOR_INVALID, refusal(api.attach(racials, owner, null)));
        Entity dead = f.anchor("dead");
        when(dead.isValid()).thenReturn(false);
        assertEquals(AttachResult.Reason.ANCHOR_INVALID, refusal(api.attach(racials, owner, dead)));
        Entity otherPlate = f.anchor("plate");
        when(otherPlate.getScoreboardTags()).thenReturn(Set.of(NicknameEntity.SCOREBOARD_TAG));
        assertEquals(AttachResult.Reason.ANCHOR_INVALID, refusal(api.attach(racials, owner, otherPlate)));
        Entity elsewhere = f.anchor("elsewhere");
        when(elsewhere.getWorld()).thenReturn(f.otherWorld);
        assertEquals(AttachResult.Reason.DIFFERENT_WORLD, refusal(api.attach(racials, owner, elsewhere)));
        assertEquals(AttachResult.Reason.ANCHOR_IS_OWNER, refusal(api.attach(racials, owner, owner)));
        Entity refuses = f.anchor("refuses");
        f.refusesPassengers.add(refuses.getUniqueId());
        assertEquals(AttachResult.Reason.ANCHOR_INVALID, refusal(api.attach(racials, owner, refuses)));

        assertSame(owner, plate.vehicle);
        assertFalse(f.manager.isAttached(owner));
        assertEquals(Optional.empty(), api.current(racials, owner));

        f.quit(b);
        assertEquals(AttachResult.Reason.OWNER_OFFLINE, refusal(api.attach(racials, b, shell)));

        when(f.plugin.isEnabled()).thenReturn(false);
        assertEquals(AttachResult.Reason.SCM_DISABLED, refusal(api.attach(racials, owner, shell)));
    }

    @Test
    void firstPluginWinsAndTheSameConsumerGetsItsHandleMoved() {
        f.nick(owner);
        Entity shell = f.anchor("shell");
        Entity ogre = f.anchor("ogre");
        NameplateHandle handle = attached(api.attach(racials, owner, shell));

        assertEquals(AttachResult.Reason.CONFLICT, refusal(api.attach(other, owner, ogre)));
        assertSame(shell, f.plate(owner).vehicle);

        assertSame(handle, attached(api.attach(racials, owner, ogre)));
        assertSame(ogre, f.plate(owner).vehicle);
        assertEquals(Optional.of(handle), api.current(racials, owner));
        assertEquals(Optional.empty(), api.current(other, owner));
    }

    @Test
    void staleHandleCannotTouchALaterAttachment() {
        f.nick(owner);
        Entity shell = f.anchor("shell");
        NameplateHandle first = attached(api.attach(racials, owner, shell));
        first.close();
        first.close(); // idempotent
        assertEquals(Optional.of(ReleaseReason.CLOSED), first.releaseReason());

        NameplateHandle second = attached(api.attach(racials, owner, shell));
        assertNotSame(first, second);
        assertFalse(first.setBlank(true));
        assertFalse(first.park());
        assertFalse(first.moveTo(f.anchor("ogre")));
        assertFalse(first.setExcludedViewers(List.of(a.getUniqueId())));
        first.close();

        assertTrue(second.isActive());
        assertSame(shell, f.plate(owner).vehicle);
    }

    @Test
    void attachShowsThePlateToViewersOfTheAnchorWhileTheOwnerIsHidden() {
        f.hide(a, owner); // e.g. SneakyRacials hides the turtle player itself
        f.nick(owner);
        Plate plate = f.plate(owner);
        verify(a).hideEntity(f.plugin, plate.display);

        Entity shell = f.anchor("shell");
        f.hide(b, shell);
        attached(api.attach(racials, owner, shell));
        verify(a).showEntity(f.plugin, plate.display);
        verify(plate.entity).sendCharacter(a);
        verify(b).hideEntity(f.plugin, plate.display);

        plate.trackers.add(a);
        f.tick(); // trackers of the moved display get text next tick
        verify(plate.entity, times(2)).sendCharacter(a);
    }

    @Test
    void closeReturnsThePlateToTheOwnerAndReHidesIt() {
        f.hide(a, owner);
        f.nick(owner);
        Plate plate = f.plate(owner);
        plate.trackers.add(b);
        NameplateHandle handle = attached(api.attach(racials, owner, f.anchor("shell")));
        handle.setBlank(true);
        verify(plate.entity).sendOff(b);

        handle.close();
        assertSame(owner, plate.vehicle);
        assertFalse(handle.isActive());
        verify(a, times(2)).hideEntity(f.plugin, plate.display);
        verify(plate.entity).sendCharacter(b); // blank cleared
        assertFalse(f.manager.isAttached(owner));
    }

    @Test
    void parkPutsThePlateOnTheOwnerHiddenFromEveryone() {
        f.nick(owner);
        Plate plate = f.plate(owner);
        Entity shell = f.anchor("shell");
        NameplateHandle handle = attached(api.attach(racials, owner, shell));

        assertTrue(handle.park());
        assertSame(owner, plate.vehicle);
        verify(a).hideEntity(f.plugin, plate.display);
        verify(b).hideEntity(f.plugin, plate.display);
        assertTrue(handle.isActive());
        assertFalse(f.manager.hasActiveAnchor(owner));

        assertTrue(handle.moveTo(shell));
        assertSame(shell, plate.vehicle);
        verify(a).showEntity(f.plugin, plate.display);
    }

    @Test
    void refusedMoveKeepsThePreviousAnchor() {
        f.nick(owner);
        Plate plate = f.plate(owner);
        Entity shell = f.anchor("shell");
        NameplateHandle handle = attached(api.attach(racials, owner, shell));
        Entity refuses = f.anchor("refuses");
        f.refusesPassengers.add(refuses.getUniqueId());

        assertFalse(handle.moveTo(refuses));
        assertSame(shell, plate.vehicle);
        assertTrue(handle.isActive());
        Entity elsewhere = f.anchor("elsewhere");
        when(elsewhere.getWorld()).thenReturn(f.otherWorld);
        assertFalse(handle.moveTo(elsewhere));
        assertSame(shell, plate.vehicle);
    }

    @Test
    void anchorThatBreaksDuringARebuildEndsTheAttachment() {
        Entity shell = f.anchor("shell");
        NameplateHandle handle = attached(api.attach(racials, owner, shell));
        when(shell.isValid()).thenReturn(false);

        f.nick(owner);
        assertSame(owner, f.plate(owner).vehicle);
        assertEquals(Optional.of(ReleaseReason.ANCHOR_INVALID), handle.releaseReason());
    }

    @Test
    void talkingFromTheVoiceThreadIsAppliedOnTheMainThread() {
        f.nick(owner);
        Plate plate = f.plate(owner);
        f.primaryThread = false;
        f.manager.setTalking(owner, true);
        verify(plate.entity, never()).setTalking(anyBoolean());

        f.primaryThread = true;
        f.tick();
        verify(plate.entity).setTalking(true);
    }

    @Test
    void offMainThreadIsRejected() {
        f.nick(owner);
        NameplateHandle handle = attached(api.attach(racials, owner, f.anchor("shell")));
        f.primaryThread = false;
        assertThrows(IllegalStateException.class, () -> api.attach(racials, owner, f.anchor("x")));
        assertThrows(IllegalStateException.class, () -> api.current(racials, owner));
        assertThrows(IllegalStateException.class, () -> handle.setBlank(true));
        assertThrows(IllegalStateException.class, handle::close);
        assertTrue(handle.isActive());
        assertEquals(owner.getUniqueId(), handle.owner());
        assertSame(racials, handle.consumer());
    }
}
