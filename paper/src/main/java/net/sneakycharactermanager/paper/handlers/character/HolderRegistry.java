package net.sneakycharactermanager.paper.handlers.character;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import com.sneakyutils.util.TextUtility;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.api.character.HeldCharacterSelectionHandler;
import net.sneakycharactermanager.paper.util.ProxyMessagingUtil;

/**
 * The holder rule: at most one body wears a character. Every load reserves the character ({@link #reserve}) and then
 * passes {@link #admit} before it touches the body, and keeps the reservation until it finishes. So the rule holds
 * whichever path the load takes, including a load started from the rule's own side effects or another load's events.
 * The index itself is what each body wears ({@link Character#wearerOf}).
 *
 * <p>When another body (the holder) already wears the character:
 * <ul>
 * <li>the owner joining as it ({@link LoadReason#OWN_JOIN}) wins: the holder's live state is saved, the holder is
 *     released, and the owner loads the character where the holder stood;</li>
 * <li>the owner picking it in {@code /char} ({@link LoadReason#OWN_SELECT}) loads nothing and goes to the selection
 *     handler, or is refused without one;</li>
 * <li>anything else (a borrow, a released holder returning to its own character, staff tools, another plugin, a
 *     non-owner, forced or not) is refused.</li>
 * </ul>
 * All of this runs on the main thread, except {@link #heldAwayFromOwner}.
 */
@ApiStatus.Internal
public final class HolderRegistry {

    enum Decision { LOAD, TAKE_OVER, SELECTION_HANDLER, REFUSE }

    static final String BEING_PLAYED = "&cSomeone else is playing that character right now.";

    private record Registration(Plugin plugin, HeldCharacterSelectionHandler handler) {}

    private static @Nullable Registration selection;
    /** Characters being put on a body right now, by that body. */
    private static final Map<CharacterKey, Player> loading = new HashMap<>();
    /** Holders released for an owner, until their own last-played character comes back. */
    private static final Set<Player> returning = Collections.newSetFromMap(new IdentityHashMap<>());

    private HolderRegistry() {}

    /** The rule as a table; see the class comment. {@code holder} is null when nobody wears the character. */
    static Decision decide(LoadReason reason, UUID body, CharacterKey key, @Nullable UUID holder) {
        if (holder == null || holder.equals(body)) return Decision.LOAD;
        boolean owner = body.equals(key.owner());
        if (owner && reason == LoadReason.OWN_JOIN) return Decision.TAKE_OVER;
        if (owner && reason == LoadReason.OWN_SELECT) return Decision.SELECTION_HANDLER;
        return Decision.REFUSE;
    }

    /**
     * Applies the rule to {@code character} about to be put on its wearer. Returns true if the load may go ahead,
     * after taking the character off its holder if the owner is joining as it.
     */
    static boolean admit(Character character, LoadReason reason) {
        Player body = character.getPlayer();
        CharacterKey key = character.getKey();
        // A released holder's join is not the owner logging in: it must not take anything over in turn.
        if (reason == LoadReason.OWN_JOIN && returning.remove(body)) reason = LoadReason.RETURN;
        Player holder = Character.wearerOf(key);
        switch (decide(reason, body.getUniqueId(), key, holder == null ? null : holder.getUniqueId())) {
            case LOAD:
                return true;
            case TAKE_OVER:
                takeOver(holder);
                return true;
            case SELECTION_HANDLER:
                offerToHandler(body, key, holder);
                return false;
            default:
                refuse(body, key, reason, holder);
                return false;
        }
    }

    /**
     * Holds {@code key} for {@code body} while its load runs, from before {@link #admit} to the end of the load; see
     * {@link Character#load(LoadReason)}. Returns false if the character is already being loaded, even onto this
     * body: a nested load must not end the outer load's reservation.
     */
    static boolean reserve(CharacterKey key, Player body) {
        Player midLoad = loading.putIfAbsent(key, body);
        if (midLoad == null) return true;
        SneakyCharacterManager.getInstance().getLogger().info("Refused to load " + key + " onto " + body.getName()
                + ": it is being loaded onto " + midLoad.getName());
        return false;
    }

    static void unreserve(CharacterKey key, Player body) {
        loading.remove(key, body);
    }

    /** {@code body} left or was locked out: drop what the registry tracks for it. */
    static void forget(Player body) {
        returning.remove(body);
    }

    private static void refuse(Player body, CharacterKey key, LoadReason reason, Player holder) {
        SneakyCharacterManager plugin = SneakyCharacterManager.getInstance();
        plugin.getLogger().info("Refused to load " + key + " onto " + body.getName() + " (" + reason + "): "
                + holder.getName() + " is wearing it");
        if (reason == LoadReason.RETURN) {
            // The released holder wears nothing; let it pick another of its characters.
            body.sendMessage(TextUtility.INSTANCE.convertToComponent("&cSomeone else is playing your last character. Pick another one."));
            plugin.selectionMenu.openMenu(body);
        }
    }

    /**
     * True if a body other than the owner wears {@code key}. Safe to call from any thread; the answer can be out of
     * date by the time it's used, so {@link #admit} checks again on the main thread.
     */
    public static boolean heldAwayFromOwner(CharacterKey key) {
        Player holder = Character.wearerOf(key);
        return holder != null && !holder.getUniqueId().equals(key.owner());
    }

    /**
     * A storage result says {@code owner} picked {@code key} while another body wore it, so nothing was selected.
     * Checks again now: returns false if nobody wears it any more, so the caller can select it normally.
     */
    public static boolean ownerPickedHeld(Player owner, CharacterKey key) {
        Player holder = Character.wearerOf(key);
        if (holder == null) return false;
        if (holder == owner) {
            owner.sendMessage(TextUtility.INSTANCE.convertToComponent("&aYou are already playing that character."));
        } else {
            offerToHandler(owner, key, holder);
        }
        return true;
    }

    private static void offerToHandler(Player owner, CharacterKey key, Player holder) {
        Registration current = selection;
        boolean handled = false;
        if (current != null) {
            try {
                handled = current.handler().onOwnerSelect(owner, key, holder);
            } catch (RuntimeException | LinkageError e) {
                SneakyCharacterManager.getInstance().getLogger().log(Level.SEVERE,
                        "Held-character selection handler of " + current.plugin().getName() + " failed", e);
            }
        }
        if (!handled) owner.sendMessage(TextUtility.INSTANCE.convertToComponent(BEING_PLAYED));
    }

    /** The owner is joining as the character {@code holder} wears: save the holder's live state, then release it. */
    private static void takeOver(Player holder) {
        Character worn = Character.get(holder);
        if (worn != null) worn.save();
        releaseForOwner(holder);
    }

    /**
     * Takes the holder off the character right away, so the owner can load it in the same tick, then loads the
     * holder's own last-played character. That load is a {@link LoadReason#RETURN}: if someone else wears it, it is
     * refused and the holder gets its character menu. Stand-in until ticket 04's {@code release(body)}, which must
     * also free the character before it returns. Until the holder's own character arrives it wears nothing, like after
     * {@code chardisable}; its inventory is cleared so the borrowed items aren't left on two bodies.
     */
    private static void releaseForOwner(Player holder) {
        SneakyCharacterManager plugin = SneakyCharacterManager.getInstance();
        Character.takeOff(holder);
        plugin.nametagManager.unnicknamePlayer(holder);
        if (plugin.getConfig().getBoolean("manageInventories", true)) holder.getInventory().clear();
        returning.add(holder);
        ProxyMessagingUtil.sendByteArray(holder, "playerJoin", holder.getUniqueId().toString());
    }

    public static void setSelectionHandler(Plugin plugin, HeldCharacterSelectionHandler handler) {
        Registration current = selection;
        if (current != null && current.plugin() != plugin) {
            SneakyCharacterManager.getInstance().getLogger().warning("Held-character selection handler of "
                    + current.plugin().getName() + " replaced by " + plugin.getName());
        }
        selection = new Registration(plugin, handler);
    }

    public static void clearSelectionHandler(Plugin plugin) {
        Registration current = selection;
        if (current != null && current.plugin() == plugin) selection = null;
    }
}
