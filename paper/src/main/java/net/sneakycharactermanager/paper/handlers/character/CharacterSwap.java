package net.sneakycharactermanager.paper.handlers.character;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import com.destroystokyo.paper.profile.ProfileProperty;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.api.character.SwapResult;
import net.sneakycharactermanager.paper.storage.DatabaseBackend;
import net.sneakycharactermanager.paper.util.SkinUtil;

/**
 * A swap: two online bodies trade the characters they wear, in one main-thread call (one tick), in memory. Body
 * {@code a} wears X and {@code b} wears Y; afterwards {@code a} wears Y where {@code b} stood and {@code b} wears X
 * where {@code a} stood, each with the other's live state ({@link LiveState}). Nothing is read from storage.
 *
 * <p>Steps, in order (the README's "Swap" section has the same list):
 * <ol>
 * <li>Check: both online, distinct, alive, out of bed, in one world, each wearing a character, neither in a load or
 *     another swap.</li>
 * <li>Lock: X is reserved for {@code b} and Y for {@code a}, so no load can put either on any body, and both bodies
 *     are marked as swapping, so nothing can load onto them or save them, and their teleports don't rebuild their
 *     nameplates, until the swap returns. Everything else that could save, load or quit them (autosave, quits,
 *     commands, storage results) runs on the main thread too, so it can't run in between.</li>
 * <li>Close any open container (its cursor item goes back into the inventory, as in vanilla), then check again.
 *     Fire {@code CharacterSkinAppliedEvent} for both skins: a veto refuses the swap before anything moves.</li>
 * <li>Read both bodies' live state and poses, and save X from {@code a} and Y from {@code b}: the one save each
 *     character gets, of exactly the state that moves. No later save can land in the middle (step 2).</li>
 * <li>Teleport first: get both off any vehicle, then each to the other's exact location and facing (no +1 Y). If a
 *     listener refuses or redirects either teleport, or a body goes offline, both go back where they stood, moving
 *     as they were: {@code REFUSED}.</li>
 * <li>Exchange what each body wears ({@link Character#exchange}): the holder registry and puppet bookkeeping, with no
 *     call-out in between, so listeners see either the old pairs (during the teleports) or the new ones.</li>
 * <li>Hand the items over, both ways at once ({@link LiveState#handOverItems}), then apply the rest of each live
 *     state to the other body, then the worn skin last (the live signed property, so uniforms move too and nothing
 *     waits on the network), then hand over each character's skin history. Health capped by a maximum that the
 *     incoming equipment only raises next tick is set again then. From the items on, a listener of the swap's own
 *     events may take a body off its new character (a release, a quit): that body gets nothing more of it.</li>
 * <li>Put poses back (SneakyPoses) and re-seat bodies that rode a vehicle, then show the new names on the same
 *     nameplates.</li>
 * <li>Unlock, then write the last-played pointer of each body that now wears its own character, through its owner's
 *     queue. A body wearing someone else's character keeps its pointer.</li>
 * </ol>
 * Fires no {@code LoadCharacterEvent} (its listeners treat it as a switch: MagicSpells' load spells cure effects and
 * reset XP, scale and hunger, which would undo what a swap moves) and no {@code PuppetLeaveEvent}.
 */
@ApiStatus.Internal
public final class CharacterSwap {

    /** Bodies in the middle of a swap. Main thread only. */
    private static final Set<Player> swapping = Collections.newSetFromMap(new IdentityHashMap<>());

    private CharacterSwap() {}

    /** True while {@code body} is in the middle of a swap: nothing may load onto it or save it. */
    public static boolean isSwapping(@Nullable Player body) {
        return body != null && swapping.contains(body);
    }

    /** Swaps what {@code a} and {@code b} wear; see the class comment. Main thread, SneakyDB mode. */
    public static SwapResult swap(Player a, Player b, DatabaseBackend storage) {
        if (a == b || a.getUniqueId().equals(b.getUniqueId())) return SwapResult.SAME_BODY;
        if (!online(a) || !online(b)) return SwapResult.BODY_OFFLINE;
        Character x = Character.get(a);
        Character y = Character.get(b);
        if (x == null || y == null) return SwapResult.NOT_WEARING;
        SwapResult blocked = blocked(a, b);
        if (blocked != null) return blocked;
        // A body mid-swap has its new character reserved, so this also refuses a swap from inside a swap.
        if (HolderRegistry.isLoadingOnto(a) || HolderRegistry.isLoadingOnto(b)) return SwapResult.BUSY;
        if (!HolderRegistry.reserve(y.getKey(), a)) return SwapResult.BUSY;
        if (!HolderRegistry.reserve(x.getKey(), b)) {
            HolderRegistry.unreserve(y.getKey(), a);
            return SwapResult.BUSY;
        }
        swapping.add(a);
        swapping.add(b);
        SwapResult result;
        try {
            result = exchange(a, b, x, y);
        } finally {
            swapping.remove(a);
            swapping.remove(b);
            HolderRegistry.unreserve(x.getKey(), b);
            HolderRegistry.unreserve(y.getKey(), a);
        }
        // After the tick's work, and only for a body that ended up in its own character: only the pointer moves, so a
        // body that rejoins comes back as what it wears now.
        writeOwnPointer(a, y, storage);
        writeOwnPointer(b, x, storage);
        return result;
    }

    private static SwapResult exchange(Player a, Player b, Character x, Character y) {
        closeContainer(a);
        closeContainer(b);
        // A close listener may have done anything; nothing has changed yet, so just stop.
        if (!ready(a, b, x, y)) return SwapResult.REFUSED;
        // Skin listeners may veto a skin: ask before anything moves, so a veto refuses the whole swap.
        ProfileProperty skinX = SkinUtil.wornTextures(a);
        ProfileProperty skinY = SkinUtil.wornTextures(b);
        if (skinY != null && !SkinUtil.skinAllowed(a, skinY) || skinX != null && !SkinUtil.skinAllowed(b, skinX)
                || !ready(a, b, x, y)) return SwapResult.REFUSED;

        SneakyCharacterManager plugin = SneakyCharacterManager.getInstance();
        boolean inventories = plugin.getConfig().getBoolean("manageInventories", true);
        boolean attributeModifiers = plugin.getConfig().getBoolean("manageAttributes", false);
        LiveState stateX = LiveState.capture(a, inventories, attributeModifiers);
        LiveState stateY = LiveState.capture(b, inventories, attributeModifiers);
        PoseTransfer.Pose poseX = PoseTransfer.capture(a);
        PoseTransfer.Pose poseY = PoseTransfer.capture(b);
        Entity rideX = ride(a);
        Entity rideY = ride(b);
        x.write();
        y.write();

        // Teleport first: the skin change that comes last re-adds each body for its onlookers at its new spot.
        a.leaveVehicle();
        b.leaveVehicle();
        boolean movedA = a.teleport(stateY.location());
        boolean movedB = movedA && b.teleport(stateX.location());
        // A listener can also send a teleport somewhere else (PlayerTeleportEvent.setTo): that is a refusal too.
        if (!movedA || !movedB || !online(a) || !online(b) || !arrived(a, stateY.location()) || !arrived(b, stateX.location())
                || !Character.isWearing(a, x.getKey()) || !Character.isWearing(b, y.getKey())) {
            if (movedA && online(a)) putBack(a, stateX);
            if (movedB && online(b)) putBack(b, stateY);
            restore(a, poseX, rideX, b, () -> online(a));
            restore(b, poseY, rideY, a, () -> online(b));
            return SwapResult.REFUSED;
        }

        Character.exchange(a, b);
        // From here a listener of the swap's own events (crafting grids, effects, air, poses, vehicles, the nameplate)
        // may take a body off its new character (a release, a quit). The items move first, both ways at once, so none
        // is ever on both bodies; after that each step for a body runs only while it still wears its new character.
        BooleanSupplier aWears = () -> wears(a, y);
        BooleanSupplier bWears = () -> wears(b, x);
        LiveState.handOverItems(a, stateY, b, stateX);
        stateY.apply(a, aWears);
        stateX.apply(b, bWears);
        // Skin last, where each body now stands; both were allowed above. No skin moves as no skin.
        if (aWears.getAsBoolean()) SkinUtil.putSkin(a, skinY);
        if (bWears.getAsBoolean()) SkinUtil.putSkin(b, skinX);
        // The history moves with each character either way, as a release leaves a body's history where it is.
        plugin.skinStateManager.swapped(a.getUniqueId(), y.getKey(), b.getUniqueId(), x.getKey());

        restore(a, poseY, rideY, b, aWears);
        restore(b, poseX, rideX, a, bWears);
        if (aWears.getAsBoolean()) plugin.nametagManager.showWornCharacter(a);
        if (bWears.getAsBoolean()) plugin.nametagManager.showWornCharacter(b);
        topUpHealthLater(a, y.getKey(), stateY);
        topUpHealthLater(b, x.getKey(), stateX);

        // A quit takes the character off, so this also covers a body that left.
        boolean done = aWears.getAsBoolean() && bWears.getAsBoolean();
        if (!done) plugin.getLogger().warning("A swap of " + a.getName() + " and " + b.getName()
                + " was interrupted by another plugin; both characters were saved where they stood before it");
        return done ? SwapResult.SWAPPED : SwapResult.INTERRUPTED;
    }

    /** A refused swap moves {@code body} back where it stood, moving as it was (a teleport stops it). */
    private static void putBack(Player body, LiveState own) {
        if (!body.teleport(own.location())) {
            SneakyCharacterManager.getInstance().getLogger().warning("A refused swap couldn't move " + body.getName()
                    + " back where it stood; it keeps its own character where it is");
        }
        own.restoreMotion(body);
    }

    /**
     * If {@code body}'s health had to be capped (the incoming equipment's health bonus arrives on its next tick), sets
     * it again then, unless the body changed character or its health changed meanwhile.
     */
    private static void topUpHealthLater(Player body, CharacterKey key, LiveState state) {
        if (!online(body) || !state.healthCapped(body)) return;
        double capped = body.getHealth();
        Bukkit.getScheduler().runTaskLater(SneakyCharacterManager.getInstance(), () -> {
            if (online(body) && Character.isWearing(body, key) && body.getHealth() == capped) state.topUpHealth(body);
        }, 1);
    }

    /** True if {@code body} stands at {@code spot}'s position, in its world. Facing isn't compared. */
    private static boolean arrived(Player body, Location spot) {
        Location at = body.getLocation();
        if (at.getWorld() != spot.getWorld()) return false;
        double dx = at.getX() - spot.getX(), dy = at.getY() - spot.getY(), dz = at.getZ() - spot.getZ();
        return dx * dx + dy * dy + dz * dz < 1.0E-6;
    }

    /** {@code body} is online and wears {@code character}. */
    private static boolean wears(Player body, Character character) {
        return online(body) && Character.isWearing(body, character.getKey());
    }

    /** Both still online, still wearing what they wore, and still able to swap where they are. */
    private static boolean ready(Player a, Player b, Character x, Character y) {
        return online(a) && online(b) && Character.isWearing(a, x.getKey()) && Character.isWearing(b, y.getKey())
                && blocked(a, b) == null;
    }

    /**
     * Puts {@code pose} on {@code body}, or seats it on {@code ride}, where the character it wears now was; only while
     * {@code still} holds (the pose command may run any listener).
     */
    private static void restore(Player body, @Nullable PoseTransfer.Pose pose, @Nullable Entity ride, Player other,
            BooleanSupplier still) {
        if (!still.getAsBoolean()) return;
        PoseTransfer.apply(pose, body);
        if (ride != null && still.getAsBoolean() && ride.isValid() && ride != other && !body.isInsideVehicle()) {
            ride.addPassenger(body);
        }
    }

    /** What {@code body} rides that isn't a pose seat (a pose is put back as a pose), or null. */
    @Nullable
    private static Entity ride(Player body) {
        Entity vehicle = body.getVehicle();
        return vehicle == null || PoseTransfer.isSeat(vehicle) ? null : vehicle;
    }

    /** Why the two bodies can't swap where they are, or null if they can. */
    @Nullable
    private static SwapResult blocked(Player a, Player b) {
        if (a.isDead() || b.isDead()) return SwapResult.DEAD;
        if (a.getWorld() != b.getWorld()) return SwapResult.DIFFERENT_WORLDS;
        if (a.isSleeping() || b.isSleeping()) return SwapResult.SLEEPING;
        return null;
    }

    /** Closes any container view; the player's own inventory view (with its crafting grid) stays. */
    private static void closeContainer(Player body) {
        if (LiveState.ownGrid(body) == null) body.closeInventory();
    }

    /** {@code body} owns {@code character} and still wears it: its last-played character becomes this one. */
    private static void writeOwnPointer(Player body, Character character, DatabaseBackend storage) {
        if (!online(body) || !Character.isWearing(body, character.getKey())) return;
        if (!character.getOwnerUUID().equals(body.getUniqueId())) return;
        storage.writeLastPlayed(body, character.getKey());
    }

    private static boolean online(Player body) {
        return Bukkit.getPlayer(body.getUniqueId()) == body;
    }
}
