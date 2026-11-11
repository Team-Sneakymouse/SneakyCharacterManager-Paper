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
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * A worn character's live state on its body, as a swap moves it to the other body: everything onlookers or the
 * character's player would notice, read straight off the body. Item and effect objects are Bukkit's own copies and
 * every setter copies again, so a state captured before the other body's was applied stays intact.
 *
 * <p>Stays with the body (not here): game mode and flight, XP, ender chest, spawn point, permissions, the account
 * name, the skin undo history's chat buttons, and anything another plugin keeps per player.
 *
 * @param inventory    the 41 inventory slots (main, armour, off hand), or null when SCM doesn't manage inventories
 * @param cursor       the item on the cursor (null or empty for none); only moves with the inventory
 * @param craftingGrid the 2x2 crafting grid, or null when SCM doesn't manage inventories or no grid was open
 * @param ownGrid      the body's own crafting grid that {@code craftingGrid} was read from, or null likewise
 */
@ApiStatus.Internal
public record LiveState(Location location, Vector velocity, float fallDistance, double health, double absorption,
        int foodLevel, float saturation, float exhaustion, Collection<PotionEffect> effects, int fireTicks,
        int freezeTicks, int remainingAir, List<AttributeState> attributes, int heldSlot, @Nullable ItemStack[] inventory,
        @Nullable ItemStack cursor, @Nullable ItemStack[] craftingGrid, @Nullable CraftingInventory ownGrid) {

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
     * change how a character looks or how much health it can have). Plugin attribute modifiers and the inventory move
     * only when SCM manages them, as for a normal load: otherwise they belong to the body.
     */
    static LiveState capture(Player body, boolean inventories, boolean attributeModifiers) {
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
        CraftingInventory grid = inventories ? ownGrid(body) : null;
        return new LiveState(body.getLocation(), body.getVelocity(), body.getFallDistance(), body.getHealth(),
                body.getAbsorptionAmount(), body.getFoodLevel(), body.getSaturation(), body.getExhaustion(),
                body.getActivePotionEffects(), body.getFireTicks(), body.getFreezeTicks(), body.getRemainingAir(),
                attributes, body.getInventory().getHeldItemSlot(),
                inventories ? body.getInventory().getContents() : null,
                inventories ? body.getItemOnCursor() : null,
                grid == null ? null : grid.getMatrix(), grid);
    }

    /**
     * Hands the items over both ways at once, before anything else moves: {@code a} gets {@code toA}'s and {@code b}
     * gets {@code toB}'s (each read off the other body). Setting the inventory and the cursor fires no event, so no
     * listener runs while an item is on both bodies or on neither. Setting a crafting grid fires
     * {@code PrepareItemCraftEvent}, so both grids are emptied before either is filled (an item is never on both
     * bodies), and both are filled whatever a listener does in between (an item is never lost).
     *
     * <p>So a body taken off its new character later in the swap is left as a release right after the swap would
     * leave it: {@code Puppets.release} clears the inventory and leaves the cursor and grid. Grids move only when both
     * bodies had their own; otherwise each keeps its grid.
     */
    static void handOverItems(Player a, LiveState toA, Player b, LiveState toB) {
        if (toA.inventory != null) a.getInventory().setContents(toA.inventory);
        if (toB.inventory != null) b.getInventory().setContents(toB.inventory);
        if (toA.inventory != null) a.setItemOnCursor(toA.cursor); // the cursor moves with the inventory; null is empty
        if (toB.inventory != null) b.setItemOnCursor(toB.cursor);
        // The grid objects were read before the teleports, so a view a listener opened since doesn't hide them.
        CraftingInventory gridA = toB.ownGrid, gridB = toA.ownGrid;
        if (gridA == null || gridB == null) return;
        gridA.setMatrix(new ItemStack[toB.craftingGrid.length]);
        gridB.setMatrix(new ItemStack[toA.craftingGrid.length]);
        gridA.setMatrix(toA.craftingGrid);
        gridB.setMatrix(toB.craftingGrid);
    }

    /**
     * Puts this state, except the items ({@link #handOverItems}), the location and the skin, on {@code body}, which
     * the swap already teleported to {@link #location()}. Effects and attributes go first, so maximum health and
     * absorption are as right as they can be this tick before health and absorption are set; velocity and fall
     * distance go after the teleport, which resets them. Bonuses from the incoming equipment only reach the body on
     * its next tick, so health above the body's maximum now is capped ({@link #topUpHealth}).
     *
     * <p>Removing and adding effects fire {@code EntityPotionEffectEvent}, and setting the air fires
     * {@code EntityAirChangeEvent}: a listener may take the body off the character (a release, a quit). Nothing more
     * goes on once {@code stillWearing} says it doesn't wear the character: it is checked after every call that fires
     * an event, and the air goes last.
     */
    void apply(Player body, BooleanSupplier stillWearing) {
        if (!body.isOnline()) return;
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

    /**
     * The player's own 2x2 crafting grid, or null if another view is open (the swap closes those before the capture).
     * Told apart from a crafting table by its size.
     */
    static @Nullable CraftingInventory ownGrid(Player body) {
        Inventory top = body.getOpenInventory().getTopInventory();
        return top instanceof CraftingInventory grid && grid.getMatrix().length == 4 ? grid : null;
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
