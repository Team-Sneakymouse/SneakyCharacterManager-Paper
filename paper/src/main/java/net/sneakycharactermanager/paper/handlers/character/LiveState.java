package net.sneakycharactermanager.paper.handlers.character;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
 * @param cursor       the item on the cursor, or null when SCM doesn't manage inventories
 * @param craftingGrid the 2x2 crafting grid, or null when SCM doesn't manage inventories or no grid was open
 */
@ApiStatus.Internal
public record LiveState(Location location, Vector velocity, float fallDistance, double health, double absorption,
        int foodLevel, float saturation, float exhaustion, Collection<PotionEffect> effects, int fireTicks,
        int freezeTicks, int remainingAir, List<AttributeState> attributes, int heldSlot, @Nullable ItemStack[] inventory,
        @Nullable ItemStack cursor, @Nullable ItemStack[] craftingGrid) {

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
        return new LiveState(body.getLocation(), body.getVelocity(), body.getFallDistance(), body.getHealth(),
                body.getAbsorptionAmount(), body.getFoodLevel(), body.getSaturation(), body.getExhaustion(),
                body.getActivePotionEffects(), body.getFireTicks(), body.getFreezeTicks(), body.getRemainingAir(),
                attributes, body.getInventory().getHeldItemSlot(),
                inventories ? body.getInventory().getContents() : null,
                inventories ? body.getItemOnCursor() : null,
                inventories ? craftingGrid(body) : null);
    }

    /**
     * Puts this state on {@code body}, which the swap already teleported to {@link #location()}: everything except
     * the location and the skin (the swap puts the skin on last). Attributes, inventory and effects go first, so
     * maximum health and absorption are as right as they can be this tick before health and absorption are set;
     * velocity and fall distance go after the teleport, which resets them. Bonuses from the incoming equipment only
     * reach the body on its next tick, so health above the body's maximum now is capped ({@link #topUpHealth}).
     */
    void apply(Player body) {
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
        if (inventory != null) body.getInventory().setContents(inventory);
        if (cursor != null) body.setItemOnCursor(cursor);
        if (craftingGrid != null && ownGrid(body) instanceof CraftingInventory grid) grid.setMatrix(craftingGrid);
        body.clearActivePotionEffects();
        body.addPotionEffects(effects);
        setHealth(body, health);
        body.setAbsorptionAmount(absorption);
        body.setFoodLevel(foodLevel); // before saturation, which can't exceed it
        body.setSaturation(saturation);
        body.setExhaustion(exhaustion);
        body.setFireTicks(fireTicks);
        body.setFreezeTicks(freezeTicks);
        body.setRemainingAir(remainingAir);
        body.setFallDistance(fallDistance);
        body.getInventory().setHeldItemSlot(heldSlot);
        body.setVelocity(velocity);
    }

    /** True if {@link #apply} had to cap health below this state's, so {@link #topUpHealth} has work to do. */
    boolean healthCapped(Player body) {
        return body.getHealth() < health;
    }

    /** Sets health and absorption again, once the incoming equipment's bonuses have reached the body. */
    void topUpHealth(Player body) {
        setHealth(body, health);
        body.setAbsorptionAmount(absorption);
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

    private static @Nullable ItemStack[] craftingGrid(Player body) {
        CraftingInventory grid = ownGrid(body);
        return grid == null ? null : grid.getMatrix();
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
