package net.sneakycharactermanager.paper.handlers.nametags;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.nameplate.AttachResult;
import net.sneakycharactermanager.paper.api.nameplate.NameplateHandle;
import net.sneakycharactermanager.paper.api.nameplate.ReleaseReason;
import net.sneakycharactermanager.paper.handlers.character.Character;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import me.clip.placeholderapi.PlaceholderAPI;


/**
 * Manager class for any function involving Nicknames and Nametags.
 *
 * <p>Owns every nameplate display: what it rides (the owner, or an attachment's anchor), which viewers SCM hides it
 * from, and which text each viewer receives. Main thread only.
 * */
public class NametagManager implements PlateAttachment.Operations {

    static final String VANISH_PLACEHOLDER = "%cmi_user_vanished_symbol%";
    static final long RENICK_DELAY_TICKS = 2;
    static final long INITIAL_TEXT_DELAY_TICKS = 5;
    private static final double TEXT_RANGE_SQUARED = 10000;

    @FunctionalInterface
    interface NicknameFactory {
        Nickname create(Player owner, String nickname, String title);
    }

    private final SneakyCharacterManager plugin;
    private final NicknameFactory factory;
    private final Function<Player, String> vanishPlaceholder;

    private final Set<String> hidingOwnName = new HashSet<>();
    private final Map<String, Nickname> nicknames = new HashMap<>();
    private final PlateAttachmentRegistry attachments = new PlateAttachmentRegistry();
    /** Per-owner lifecycle generation, bumped on every nick/unnick; fences delayed work. Values never repeat. */
    private final Map<UUID, Long> lifecycles = new HashMap<>();
    private long lastLifecycle;
    /** Owners whose plate SCM removed because they were suppressed (vanished); rebuilt once that ends. */
    private final Set<UUID> suppressedPlates = new HashSet<>();
    /** Set by {@link #unnickAll()} when SCM disables; no new attachments afterwards. */
    private boolean shutDown;

    public NametagManager(SneakyCharacterManager plugin) {
        this(plugin, Nickname::new,
                player -> plugin.papiActive ? PlaceholderAPI.setPlaceholders(player, VANISH_PLACEHOLDER) : null);
    }

    NametagManager(SneakyCharacterManager plugin, NicknameFactory factory, Function<Player, String> vanishPlaceholder) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.factory = Objects.requireNonNull(factory, "factory");
        this.vanishPlaceholder = Objects.requireNonNull(vanishPlaceholder, "vanishPlaceholder");
    }

    // ------------------------------------------------------------------------------------------------------------
    // Suppression and delayed work
    // ------------------------------------------------------------------------------------------------------------

    /** Dead, spectating or vanished players have no plate at all, for any viewer. */
    public boolean isSuppressed(Player player) {
        return player.isDead()
                || player.getGameMode() == GameMode.SPECTATOR
                || isVanishedPlaceholder(vanishPlaceholder.apply(player));
    }

    /** CMI's vanish symbol; an unresolved placeholder (expansion missing) is not a vanish. */
    static boolean isVanishedPlaceholder(String resolved) {
        return resolved != null && !resolved.isEmpty() && !resolved.equals(VANISH_PLACEHOLDER);
    }

    long lifecycle(UUID owner) {
        return lifecycles.getOrDefault(owner, 0L);
    }

    private void bumpLifecycle(UUID owner) {
        lifecycles.put(owner, ++lastLifecycle);
    }

    /**
     * Runs {@code task} after {@code ticks} with the owner's current Player, unless SCM was disabled, the owner went
     * offline, or the owner's plate was built or removed in the meantime.
     */
    public void runLater(Player owner, long ticks, Consumer<Player> task) {
        UUID id = owner.getUniqueId();
        long generation = lifecycle(id);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.isEnabled() || lifecycle(id) != generation) return;
            Player current = Bukkit.getPlayer(id);
            if (current == null || !current.isOnline()) return;
            task.accept(current);
        }, ticks);
    }

    /** Destroys the plate now and builds it again shortly; fenced against later nick/unnick. */
    public void rebuild(Player owner) {
        unnicknamePlayer(owner);
        renickLater(owner);
    }

    /** Re-reads the character when the delay elapses; nothing happens if the plate changed meanwhile. */
    public void renickLater(Player owner) {
        runLater(owner, RENICK_DELAY_TICKS, current -> {
            Character character = Character.get(current);
            if (character == null) return;
            nicknamePlayer(current, character.getDisplayName());
        });
    }

    // ------------------------------------------------------------------------------------------------------------
    // Nick / unnick
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Set the nickname of the supplied player to the given name
     * @param player Player to create/set the nickname for.
     * @param nickname Nickname to set onto the player
     * */
    public void nicknamePlayer(Player player, String nickname) {
        if (isSuppressed(player)) return;

        UUID id = player.getUniqueId();
        String titleDisplay = resolveTitleDisplay(player);

        Nickname name = nicknames.get(id.toString());
        if (name != null && !name.isValid()) {
            // Removed behind SCM's back (e.g. /kill): never keep updating a dead display.
            nicknames.remove(id.toString());
            name = null;
        }
        if (name == null) {
            name = factory.create(player, nickname, titleDisplay);
            nicknames.put(id.toString(), name);
        } else {
            name.setNickname(nickname, titleDisplay);
        }
        suppressedPlates.remove(id);
        bumpLifecycle(id);

        place(player, name);
        reconcile(player, name);

        runLater(player, INITIAL_TEXT_DELAY_TICKS, current -> {
            Nickname built = nicknames.get(id.toString());
            if (built == null) return;
            refreshNicknames(current, built, null);
        });
    }

    private String resolveTitleDisplay(Player player) {
        Character character = Character.get(player);
        if (character == null) {
            return null;
        }
        String titleKey = character.getTitle();
        if (titleKey == null || titleKey.isBlank()) {
            return null;
        }
        var registry = plugin.titleRegistry;
        if (registry == null) {
            return null;
        }
        return registry.getDisplay(titleKey);
    }

    /**
     * Remove the nickname of a player. An attachment survives: the next plate built for the player goes back onto
     * its anchor.
     * @param player Player to remove the nickname from
     * */
    public void unnicknamePlayer(Player player) {
        UUID id = player.getUniqueId();
        Nickname nickname = nicknames.remove(id.toString());
        suppressedPlates.remove(id);
        bumpLifecycle(id);
        if (nickname != null) {
            nickname.unNick();
        }
    }

    /** Removes a suppressed (vanished) owner's plate and remembers to rebuild it when the suppression ends. */
    public void suppress(Player owner) {
        if (getNickname(owner) == null) return;
        unnicknamePlayer(owner);
        suppressedPlates.add(owner.getUniqueId());
    }

    /** Rebuilds the plate of an owner SCM suppressed, once they are no longer suppressed. */
    public void restoreIfUnsuppressed(Player owner) {
        UUID id = owner.getUniqueId();
        if (!suppressedPlates.contains(id) || getNickname(owner) != null || isSuppressed(owner)) return;
        Character character = Character.get(owner);
        if (character == null) {
            suppressedPlates.remove(id);
            return;
        }
        nicknamePlayer(owner, character.getDisplayName());
    }

    boolean isAwaitingRestore(UUID owner) {
        return suppressedPlates.contains(owner);
    }

    /** Forgets everything held for a player who left, as owner and as viewer. Call after unnicking them. */
    public void forgetPlayer(UUID player) {
        lifecycles.remove(player);
        suppressedPlates.remove(player);
        for (Nickname name : nicknames.values()) name.hiddenFrom.remove(player);
    }

    /**
     * Remove the nickname entities of all players and release every attachment (SCM is disabling).
     * */
    public void unnickAll() {
        shutDown = true;
        attachments.removeAll(ReleaseReason.SCM_DISABLED);
        for (Nickname entry : nicknames.values()) {
            try {
                entry.unNick();
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "Failed to remove nameplate of " + entry.getOwnerUuid(), failure);
            }
        }
        nicknames.clear();
        suppressedPlates.clear();
        lifecycles.clear();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Placement and per-viewer visibility
    // ------------------------------------------------------------------------------------------------------------

    /** Puts the plate on the attachment's anchor, or on the owner. An unusable anchor ends the attachment. */
    private void place(Player owner, Nickname name) {
        PlateAttachment attachment = attachments.get(owner.getUniqueId());
        if (attachment != null && !attachment.parked()) {
            if (anchorProblem(owner, attachment.anchor()) == null && name.mountOn(attachment.anchor())) return;
            attachments.remove(owner.getUniqueId(), ReleaseReason.ANCHOR_INVALID);
            scheduleTextRefresh(owner); // viewers may still show the attachment's blank text
        }
        name.mountOn(owner);
    }

    /**
     * Keeps an existing plate where it belongs: rebuilds a removed or other-world display, remounts a plate knocked
     * off its vehicle, ends an attachment whose anchor became unusable, and re-applies per-viewer visibility.
     */
    public void ensurePlacement(Player owner) {
        Nickname name = getNickname(owner);
        if (name == null) return;
        if (!name.isValid() || !Objects.equals(name.display().getWorld(), owner.getWorld())) {
            rebuild(owner);
            return;
        }
        place(owner, name);
        reconcile(owner, name);
    }

    /** Applies the visibility policy for every online viewer of {@code owner}'s plate. */
    public void reconcile(Player owner) {
        Nickname name = getNickname(owner);
        if (name != null) reconcile(owner, name);
    }

    private void reconcile(Player owner, Nickname name) {
        for (Player viewer : List.copyOf(Bukkit.getOnlinePlayers())) reconcile(owner, name, viewer);
    }

    /** Applies the visibility policy of every plate for one viewer (e.g. on join). */
    public void reconcileViewer(Player viewer) {
        for (Nickname name : List.copyOf(nicknames.values())) {
            Player owner = Bukkit.getPlayer(name.ownerId());
            if (owner != null) reconcile(owner, name, viewer);
        }
    }

    private void reconcile(Player owner, Nickname name, Player viewer) {
        if (!plugin.isEnabled()) return; // Paper's hideEntity requires an enabled plugin
        boolean visible = PlateVisibility.visibleTo(viewer, owner, attachments.get(owner.getUniqueId()));
        TextDisplay display = name.display();
        UUID viewerId = viewer.getUniqueId();
        if (!visible) {
            if (name.hiddenFrom.add(viewerId)) viewer.hideEntity(plugin, display);
        } else if (name.hiddenFrom.remove(viewerId)) {
            viewer.showEntity(plugin, display);
            // A newly tracked display carries only the server-side (empty) text: send this viewer's text.
            refreshNickname(name, viewer);
        }
    }

    /** {@code viewer} stopped seeing {@code owner} (a per-viewer hide). */
    public void ownerHidden(Player owner, Player viewer) {
        Nickname name = getNickname(owner);
        if (name == null) return;
        if (isSuppressed(owner)) {
            suppress(owner);
            return;
        }
        reconcile(owner, name, viewer);
    }

    /** {@code viewer} can see {@code owner} again. */
    public void ownerShown(Player owner, Player viewer) {
        Nickname name = getNickname(owner);
        if (name != null) {
            reconcile(owner, name, viewer);
        } else if (!isSuppressed(owner) && Character.get(owner) != null) {
            // e.g. un-vanish: the plate was removed while suppressed. Fenced, so many show events build it once.
            renickLater(owner);
        }
    }

    /** A viewer's visibility of {@code entity} changed; re-apply it to plates anchored on that entity. */
    public void anchorVisibilityChanged(Entity entity, Player viewer) {
        for (UUID ownerId : attachments.ownersOfAnchor(entity.getUniqueId())) {
            Player owner = Bukkit.getPlayer(ownerId);
            Nickname name = nicknames.get(ownerId.toString());
            if (owner != null && name != null) reconcile(owner, name, viewer);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Text
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Sends each in-range tracker of the plate display its text, skipping viewers in {@code previous}.
     * @return the viewers now in range
     */
    public Set<UUID> refreshNicknames(Player player, Nickname name, Set<UUID> previous) {
        Set<UUID> current = new HashSet<>();
        TextDisplay display = name.display();
        Location at = display.getLocation();

        for (Player tracking : display.getTrackedBy()) {
            Location trackerAt = tracking.getLocation();
            if (!Objects.equals(trackerAt.getWorld(), at.getWorld()) || at.distanceSquared(trackerAt) > TEXT_RANGE_SQUARED) continue;
            current.add(tracking.getUniqueId());
            if (previous != null && previous.contains(tracking.getUniqueId())) continue;
            refreshNickname(name, tracking);
        }
        if (plugin.getConfig().getBoolean("see-own-nameplate", false)) {
            current.add(player.getUniqueId());
            if (previous == null || !previous.contains(player.getUniqueId())) {
                refreshNickname(name, player);
            }
        }

        return current;
    }

    public void refreshNickname(Nickname name, Player requester) {
        refreshNickname(name, requester, NamesPreferenceHandler.get(requester));
    }

    public void refreshNickname(Nickname name, Player requester, NamesPreference preference) {
        PlateAttachment attachment = attachments.get(name.ownerId());
        if (attachment != null) {
            if (attachment.excludes(requester.getUniqueId())) return;
            if (attachment.blank()) {
                name.hideName(requester);
                return;
            }
        }
        switch (preference) {
            case OFF -> name.hideName(requester);
            case ON -> {
                if (hidingOwnName.contains(name.getOwnerUuid())) {
                    name.showHiddenName(requester);
                } else {
                    name.showRealName(requester, true);
                }
            }
            case CHARACTER -> {
                if (hidingOwnName.contains(name.getOwnerUuid())) {
                    name.hideName(requester);
                } else {
                    name.showRealName(requester, false);
                }
            }
        }
    }

    private void scheduleTextRefresh(Player owner) {
        // A moved display's trackers change during the tick, so resend on the next one.
        runLater(owner, 1, current -> {
            Nickname name = getNickname(current);
            if (name != null) refreshNicknames(current, name, null);
        });
    }

    public void applyNamesPreference(Player requester) {
        applyNamesPreference(requester, NamesPreferenceHandler.get(requester));
    }

    public void applyNamesPreference(Player requester, NamesPreference preference) {
        for (Nickname name : nicknames.values()) {
            refreshNickname(name, requester, preference);
        }

        if (plugin.getConfig().getBoolean("see-own-nameplate", false)) {
            Nickname own = nicknames.get(requester.getUniqueId().toString());
            if (own != null) {
                refreshNickname(own, requester, preference);
            }
        }
    }

    public void setHidingOwnName(Player player, boolean hide) {
        String uuid = player.getUniqueId().toString();
        if (hide) {
            hidingOwnName.add(uuid);
        } else {
            hidingOwnName.remove(uuid);
        }

        Nickname nickname = nicknames.get(uuid);
        if (nickname != null) {
            refreshNicknames(player, nickname, null);
        }
    }

    public boolean isHidingOwnName(Player player) {
        return hidingOwnName.contains(player.getUniqueId().toString());
    }

    public void clearHidingOwnName(Player player) {
        hidingOwnName.remove(player.getUniqueId().toString());
    }

    /**
     * Load all active nicknames for a player.
     * Required because the nickname entities are fake, they do not exist on player connect
     * @param player Player to load names for
     * */
    public void loadNames(Player player) {
        reconcileViewer(player);
        applyNamesPreference(player);
    }

    /**
     * Get the players current Nickname!
     * @return The Players Nickname, or null if they don't have one.
     * */
    public Nickname getNickname(Player player) {
        return nicknames.get(player.getUniqueId().toString());
    }

    public void setTalking(Player player, boolean talking) {
        if (!Bukkit.isPrimaryThread()) {
            // Simple Voice Chat delivers microphone packets on its own thread; plate state is main-thread only.
            Bukkit.getScheduler().runTask(plugin, () -> setTalking(player, talking));
            return;
        }
        Nickname nickname = nicknames.get(player.getUniqueId().toString());
        if (nickname != null) {
            nickname.setTalking(talking);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Attachments
    // ------------------------------------------------------------------------------------------------------------

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("SCM nameplate attachments must be used on the server main thread");
        }
    }

    private static AttachResult refused(AttachResult.Reason reason) {
        return new AttachResult.Refused(reason);
    }

    private static AttachResult.Reason anchorProblem(Player owner, Entity anchor) {
        if (anchor == null || !anchor.isValid() || anchor.getScoreboardTags().contains(NicknameEntity.SCOREBOARD_TAG)) {
            return AttachResult.Reason.ANCHOR_INVALID;
        }
        if (anchor.getUniqueId().equals(owner.getUniqueId())) return AttachResult.Reason.ANCHOR_IS_OWNER;
        if (!Objects.equals(anchor.getWorld(), owner.getWorld())) return AttachResult.Reason.DIFFERENT_WORLD;
        return null;
    }

    /** The owner's plate if it exists and is still a live entity. */
    private Nickname livePlate(Player owner) {
        Nickname name = getNickname(owner);
        return name != null && name.isValid() ? name : null;
    }

    public AttachResult attach(Plugin consumer, Player owner, Entity anchor) {
        requireMainThread();
        if (shutDown || !plugin.isEnabled()) return refused(AttachResult.Reason.SCM_DISABLED);
        if (consumer == null || !consumer.isEnabled()) return refused(AttachResult.Reason.CONSUMER_DISABLED);
        if (owner == null || !owner.isOnline()) return refused(AttachResult.Reason.OWNER_OFFLINE);

        PlateAttachment current = attachments.get(owner.getUniqueId());
        if (current != null && !current.consumer().equals(consumer)) return refused(AttachResult.Reason.CONFLICT);
        AttachResult.Reason problem = anchorProblem(owner, anchor);
        if (problem != null) return refused(problem);

        if (current != null) {
            return moveTo(current, anchor) ? new AttachResult.Attached(current) : refused(AttachResult.Reason.ANCHOR_INVALID);
        }

        Nickname name = livePlate(owner);
        if (name != null && !name.mountOn(anchor)) {
            name.mountOn(owner);
            return refused(AttachResult.Reason.ANCHOR_INVALID);
        }
        PlateAttachment attachment = attachments.add(owner.getUniqueId(), consumer, anchor, this);
        if (name != null) placementChanged(owner, name);
        return new AttachResult.Attached(attachment);
    }

    public Optional<NameplateHandle> currentAttachment(Plugin consumer, Player owner) {
        requireMainThread();
        if (consumer == null || owner == null) return Optional.empty();
        PlateAttachment attachment = attachments.get(owner.getUniqueId());
        return attachment != null && attachment.consumer().equals(consumer) ? Optional.of(attachment) : Optional.empty();
    }

    private void placementChanged(Player owner, Nickname name) {
        reconcile(owner, name);
        scheduleTextRefresh(owner);
    }

    @Override
    public boolean moveTo(PlateAttachment attachment, Entity anchor) {
        requireMainThread();
        if (!attachments.isCurrent(attachment)) return false;
        Player owner = Bukkit.getPlayer(attachment.owner());
        if (owner == null || anchorProblem(owner, anchor) != null) return false;

        Nickname name = livePlate(owner);
        if (name != null && !name.mountOn(anchor)) {
            place(owner, name); // back where the attachment says, or onto the owner if that anchor broke too
            reconcile(owner, name);
            return false;
        }
        boolean changed = attachment.parked() || !anchor.equals(attachment.anchor());
        attachments.anchorTo(attachment, anchor);
        if (name != null && changed) placementChanged(owner, name);
        return true;
    }

    @Override
    public boolean park(PlateAttachment attachment) {
        requireMainThread();
        if (!attachments.isCurrent(attachment)) return false;
        Player owner = Bukkit.getPlayer(attachment.owner());
        if (owner == null) return false;
        attachments.park(attachment);
        Nickname name = livePlate(owner);
        if (name != null) {
            name.mountOn(owner);
            reconcile(owner, name);
        }
        return true;
    }

    @Override
    public boolean setBlank(PlateAttachment attachment, boolean blank) {
        requireMainThread();
        if (!attachments.isCurrent(attachment)) return false;
        if (attachment.blank() == blank) return true;
        attachment.blank(blank);
        Player owner = Bukkit.getPlayer(attachment.owner());
        Nickname name = owner == null ? null : livePlate(owner);
        if (name != null) refreshNicknames(owner, name, null);
        return true;
    }

    @Override
    public boolean setExcludedViewers(PlateAttachment attachment, Collection<UUID> viewers) {
        requireMainThread();
        if (!attachments.isCurrent(attachment)) return false;
        Set<UUID> excluded = new HashSet<>();
        if (viewers != null) for (UUID viewer : viewers) if (viewer != null) excluded.add(viewer);
        attachment.excluded(excluded);
        Player owner = Bukkit.getPlayer(attachment.owner());
        Nickname name = owner == null ? null : livePlate(owner);
        if (name != null) reconcile(owner, name);
        return true;
    }

    @Override
    public void close(PlateAttachment attachment) {
        requireMainThread();
        if (!attachments.isCurrent(attachment)) return;
        releaseOwner(attachment.owner(), ReleaseReason.CLOSED);
    }

    /** True while an attachment holds the owner's plate on an anchor (not parked). */
    public boolean hasActiveAnchor(Player owner) {
        PlateAttachment attachment = attachments.get(owner.getUniqueId());
        return attachment != null && !attachment.parked();
    }

    public boolean isAttached(Player owner) {
        return attachments.get(owner.getUniqueId()) != null;
    }

    /**
     * Ends the owner's attachment and returns the plate to the owner with normal visibility and text.
     * @return false when there was no attachment
     */
    public boolean releaseOwner(UUID owner, ReleaseReason reason) {
        if (!dropAttachment(owner, reason)) return false;
        Player player = Bukkit.getPlayer(owner);
        if (player != null) restoreOwnerPlate(player);
        return true;
    }

    /** Ends the owner's attachment without touching the plate; the caller rebuilds or removes it. */
    public boolean dropAttachment(UUID owner, ReleaseReason reason) {
        return attachments.remove(owner, reason) != null;
    }

    private void restoreOwnerPlate(Player owner) {
        ensurePlacement(owner);
        Nickname name = getNickname(owner);
        if (name != null) refreshNicknames(owner, name, null); // drops blank text
    }

    /** A plugin was disabled: release its attachments. */
    public void releaseConsumer(Plugin consumer) {
        for (UUID owner : attachments.ownersAttachedBy(consumer)) releaseOwner(owner, ReleaseReason.CONSUMER_DISABLED);
    }

    /**
     * An entity left its world (removed, unloaded, changed world). Attachments using it as anchor end now; the plate
     * goes back to its owner on the next tick, outside the entity-removal callback.
     */
    public void anchorRemoved(UUID entityId) {
        for (UUID ownerId : attachments.ownersOfAnchor(entityId)) {
            dropAttachment(ownerId, ReleaseReason.ANCHOR_INVALID);
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner != null) runLater(owner, 1, this::restoreOwnerPlate);
        }
    }
}
