package net.sneakycharactermanager.paper.handlers.character;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * A worn character's live state on its body, as a swap moves it to the other body: everything onlookers or the
 * character's player would notice, read straight off the body. Effects are Bukkit's own copies and every setter copies
 * again, so a state captured before the other body's was applied stays intact. The items aren't part of it: the
 * inventory moves live ({@link #handOverItems}), after the swap put the cursor item and crafting grid into it.
 *
 * <p>Stays with the body (not here): game mode and flight, XP, ender chest, spawn point, permissions, the account
 * name, the skin undo history's chat buttons, and anything another plugin keeps per player.
 */
@ApiStatus.Internal
public record LiveState(Location location, Vector velocity, float fallDistance, double health, double absorption,
        int foodLevel, float saturation, float exhaustion, Collection<PotionEffect> effects, int fireTicks,
        int freezeTicks, int remainingAir, List<AttributeState> attributes, int heldSlot) {

    /**
     * One attribute: its base value, and the modifiers that move with it, or null if only the base moves.
     * Minecraft's own modifiers (equipment, effects, sprinting) never move: the game adds and removes those itself.
     */
    record AttributeState(Attribute attribute, double base, @Nullable List<AttributeModifier> modifiers) {}

    /** Every attribute type. A seam: tests without a server registry replace it. */
    public static Iterable<Attribute> attributeTypes() {
        return Registry.ATTRIBUTE;
    }

    /**
     * Reads {@code body}'s live state. Attribute base values always move (scale, maximum health: a swap must not
     * change how a character looks or how much health it can have). Plugin attribute modifiers move only when SCM
     * manages them, as for a normal load: otherwise they belong to the body.
     */
    static LiveState capture(Player body, boolean attributeModifiers) {
        List<AttributeState> attributes = new ArrayList<>();
        for (Attribute type : attributeTypes()) {
            AttributeInstance instance = body.getAttribute(type);
            if (instance == null) continue;
            List<AttributeModifier> modifiers = null;
            if (attributeModifiers) {
                modifiers = new ArrayList<>();
                for (AttributeModifier modifier : instance.getModifiers()) if (moves(modifier)) modifiers.add(modifier);
            }
            attributes.add(new AttributeState(type, instance.getBaseValue(), modifiers));
        }
        return new LiveState(body.getLocation(), body.getVelocity(), body.getFallDistance(), body.getHealth(),
                body.getAbsorptionAmount(), body.getFoodLevel(), body.getSaturation(), body.getExhaustion(),
                body.getActivePotionEffects(), body.getFireTicks(), body.getFreezeTicks(), body.getRemainingAir(),
                attributes, body.getInventory().getHeldItemSlot());
    }

    /**
     * Hands the inventories over both ways at once, read off both bodies right now: {@code a} gets {@code b}'s and
     * {@code b} gets {@code a}'s. Neither call fires an event, so no listener runs while an item is on both bodies or
     * on neither. The swap put the cursor item and crafting grid into the inventory before its save, so this is every
     * item the character has: what its save holds, plus anything a listener added since.
     */
    static void handOverItems(Player a, Player b) {
        ItemStack[] fromA = a.getInventory().getContents(), fromB = b.getInventory().getContents();
        // Each slot write stores a copy; what it replaces is left as it was, so fromA still reads a's items.
        a.getInventory().setContents(fromB);
        b.getInventory().setContents(fromA);
    }

    /**
     * Puts this state, except the items, the location and the skin, on {@code body}, which the swap already teleported
     * to {@link #location()}. Effects and attributes go first, so maximum health and absorption are as right as they
     * can be this tick before health and absorption are set; velocity and fall distance go after the teleport, which
     * resets them. Bonuses from the incoming equipment only reach the body on its next tick, so health above the
     * body's maximum now is capped ({@link #topUpHealth}).
     *
     * <p>Removing and adding effects fire {@code EntityPotionEffectEvent}, and setting the air fires
     * {@code EntityAirChangeEvent}: a listener may take the body off the character (a release, a quit). Nothing more
     * goes on once {@code stillWearing} says it doesn't wear the character: it is checked after every call that fires
     * an event, and the air goes last.
     */
    void apply(Player body, BooleanSupplier stillWearing) {
        // The removals take off the effects this body hands to the other: they run even if a listener released the
        // body before or during them, so the outgoing effects aren't left on both bodies.
        body.clearActivePotionEffects();
        // An effect a listener keeps or refuses stays as the listener decided (how a plugin forbids an effect on a body).
        for (PotionEffect effect : effects) {
            if (!stillWearing.getAsBoolean()) return;
            body.addPotionEffect(effect);
        }
        if (!stillWearing.getAsBoolean()) return;
        for (AttributeState state : attributes) {
            AttributeInstance instance = body.getAttribute(state.attribute());
            if (instance == null) continue;
            instance.setBaseValue(state.base());
            if (state.modifiers() == null) continue;
            for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
                if (moves(modifier)) instance.removeModifier(modifier);
            }
            for (AttributeModifier modifier : state.modifiers()) instance.addModifier(modifier);
        }
        setHealth(body, health);
        body.setAbsorptionAmount(absorption);
        body.setFoodLevel(foodLevel); // before saturation, which can't exceed it
        body.setSaturation(saturation);
        body.setExhaustion(exhaustion);
        body.setFireTicks(fireTicks);
        body.setFreezeTicks(freezeTicks);
        body.setFallDistance(fallDistance);
        body.getInventory().setHeldItemSlot(heldSlot);
        body.setVelocity(velocity);
        body.setRemainingAir(remainingAir); // last: it fires an event
    }

    /** True if {@link #apply} had to cap health below this state's, so {@link #topUpHealth} has work to do. */
    boolean healthCapped(Player body) {
        return body.getHealth() < health;
    }

    /**
     * Sets health again, once the incoming equipment's bonuses have reached the body. Absorption isn't set again: it
     * was set in {@link #apply}, and the body may have spent some of it since.
     */
    void topUpHealth(Player body) {
        setHealth(body, health);
    }

    /** Velocity and fall distance again, after a refused swap teleported the body back (a teleport resets both). */
    void restoreMotion(Player body) {
        body.setVelocity(velocity);
        body.setFallDistance(fallDistance);
    }

    /** {@code body}'s maximum health now. A seam: tests without a server registry replace it. */
    public static double maxHealth(Player body) {
        AttributeInstance maximum = body.getAttribute(Attribute.MAX_HEALTH);
        return maximum == null ? body.getHealth() : maximum.getValue();
    }

    /** Plugin modifiers move; Minecraft's own (and unnamed ones) are the game's to manage. Same rule as a save. */
    private static boolean moves(AttributeModifier modifier) {
        NamespacedKey key = modifier.getKey();
        return key != null && !NamespacedKey.MINECRAFT.equals(key.getNamespace());
    }

    private static void setHealth(Player body, double health) {
        body.setHealth(Math.min(health, maxHealth(body)));
    }
}
