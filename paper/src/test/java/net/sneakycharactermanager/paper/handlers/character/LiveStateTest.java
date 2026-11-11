package net.sneakycharactermanager.paper.handlers.character;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.sneakycharactermanager.paper.util.SkinUtil;

/**
 * {@link LiveState}, the part of a swap that moves attributes and inventory between bodies. {@code SwapTest} covers
 * the rest end to end; this covers what needs SCM's attribute setting, which {@code SwapTest}'s saves can't run
 * without a server registry.
 */
class LiveStateTest {

    /** Attribute types can't be built without a server registry; each body answers any type with its one instance. */
    final List<Attribute> attributes = Arrays.asList((Attribute) null);
    final AttributeModifier buffA = new AttributeModifier(new NamespacedKey("sneaky", "buff"), 0.2, AttributeModifier.Operation.ADD_NUMBER);
    final AttributeModifier buffB = new AttributeModifier(new NamespacedKey("sneaky", "buff"), -0.1, AttributeModifier.Operation.ADD_NUMBER);
    final AttributeModifier armourA = new AttributeModifier(NamespacedKey.minecraft("armor.chest"), 3, AttributeModifier.Operation.ADD_NUMBER);
    final AttributeModifier armourB = new AttributeModifier(NamespacedKey.minecraft("armor.chest"), 5, AttributeModifier.Operation.ADD_NUMBER);
    MockedStatic<LiveState> types;
    MockedStatic<SkinUtil> skins;
    Player a, b;
    AttributeInstance scaleA, scaleB;
    CraftingInventory gridA, gridB;

    @BeforeEach
    void setUp() {
        types = mockStatic(LiveState.class, call -> switch (call.getMethod().getName()) {
            case "attributeTypes" -> attributes;
            case "maxHealth" -> 20.0;
            default -> call.callRealMethod();
        });
        skins = mockStatic(SkinUtil.class);
        scaleA = attribute(0.8, buffA, armourA);
        scaleB = attribute(1.1, buffB, armourB);
        gridA = grid(4);
        gridB = grid(4);
        a = body(scaleA, gridA);
        b = body(scaleB, gridB);
    }

    @AfterEach
    void tearDown() {
        skins.close();
        types.close();
    }

    @Test
    void pluginModifiersMoveWhenManagedAndMinecraftsStayWithTheBody() {
        LiveState.capture(a, true).apply(b, () -> true);

        verify(scaleB).setBaseValue(0.8);
        verify(scaleB).removeModifier(buffB);
        verify(scaleB, never()).removeModifier(armourB);
        verify(scaleB).addModifier(buffA);
        verify(scaleB, never()).addModifier(armourA);
    }

    @Test
    void withoutManagedAttributesOnlyTheBaseValueMoves() {
        LiveState.capture(a, false).apply(b, () -> true);

        verify(scaleB).setBaseValue(0.8);
        verify(scaleB, never()).removeModifier(any(AttributeModifier.class));
        verify(scaleB, never()).addModifier(any());
    }

    @Test
    void theStateCarriesNoItems() {
        LiveState.capture(a, false).apply(b, () -> true);

        verify(b.getInventory(), never()).setContents(any());
        verify(b, never()).setItemOnCursor(any());
        verify(gridB, never()).setItem(anyInt(), any());
        verify(b.getInventory()).setHeldItemSlot(3);
    }

    @Test
    void theInventoryAndCursorMoveBothWaysAsTheyAreNow() {
        ItemStack[] contentsA = a.getInventory().getContents(), contentsB = b.getInventory().getContents();
        ItemStack cursorA = a.getItemOnCursor(), cursorB = b.getItemOnCursor();

        LiveState.handOverItems(a, b);

        verify(b.getInventory()).setContents(contentsA);
        verify(a.getInventory()).setContents(contentsB);
        verify(b).setItemOnCursor(cursorA);
        verify(a).setItemOnCursor(cursorB);
    }

    @Test
    void takingAGridEmptiesItsSlotsOneAtATime() {
        ItemStack stick = item("stick");
        when(gridA.getItem(2)).thenReturn(stick); // matrix slot 1: the result slot comes first

        LiveState.TakenGrid taken = LiveState.takeGrid(a);

        assertSame(gridA, taken.grid());
        assertArrayEquals(new ItemStack[]{null, stick, null, null}, taken.items());
        verify(gridA).setItem(2, null);
        verify(gridA, times(1)).setItem(anyInt(), any());
    }

    @Test
    void aGivenGridFillsOnlyEmptySlotsAndTheRestGoesIntoTheInventory() {
        ItemStack stick = item("stick"), rope = item("rope"), placed = item("placed by a listener");
        when(gridB.getItem(2)).thenReturn(placed);
        LiveState.TakenGrid own = new LiveState.TakenGrid(gridB, new ItemStack[4]);

        LiveState.giveGrid(b, own, new LiveState.TakenGrid(gridA, new ItemStack[]{stick, rope, null, null}));

        verify(gridB).setItem(1, stick);
        verify(gridB, never()).setItem(2, rope);
        verify(b.getInventory()).addItem(rope);
    }

    @Test
    void withoutItsOwnGridABodyGetsTheGridIntoItsInventory() {
        ItemStack stick = item("stick");

        LiveState.giveGrid(b, null, new LiveState.TakenGrid(gridA, new ItemStack[]{stick, null, null, null}));

        verify(b.getInventory()).addItem(stick);
        verify(gridB, never()).setItem(anyInt(), any());
    }

    @Test
    void onlyThePlayersOwnGridIsTheirs() {
        assertSame(gridA, LiveState.ownGrid(a));
        CraftingInventory table = grid(9);
        when(b.getOpenInventory().getTopInventory()).thenReturn(table);
        assertNull(LiveState.ownGrid(b), "a crafting table is a container, closed before a swap");
    }

    private static AttributeInstance attribute(double base, AttributeModifier... modifiers) {
        AttributeInstance instance = mock(AttributeInstance.class);
        when(instance.getBaseValue()).thenReturn(base);
        when(instance.getModifiers()).thenReturn(List.of(modifiers));
        return instance;
    }

    private static CraftingInventory grid(int size) {
        CraftingInventory grid = mock(CraftingInventory.class);
        when(grid.getMatrix()).thenReturn(new ItemStack[size]);
        when(grid.getSize()).thenReturn(size + 1);
        return grid;
    }

    private static ItemStack item(String name) {
        ItemStack item = mock(ItemStack.class, name);
        when(item.clone()).thenReturn(item);
        return item;
    }

    private static Player body(AttributeInstance scale, CraftingInventory grid) {
        Player body = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        InventoryView view = mock(InventoryView.class);
        when(body.getAttribute(any())).thenReturn(scale);
        when(body.getLocation()).thenReturn(new Location(null, 0, 0, 0));
        when(body.getVelocity()).thenReturn(new Vector());
        when(body.getHealth()).thenReturn(10.0);
        when(body.getInventory()).thenReturn(inventory);
        when(inventory.getHeldItemSlot()).thenReturn(3);
        when(inventory.getContents()).thenReturn(new ItemStack[41]);
        when(body.getItemOnCursor()).thenReturn(mock(ItemStack.class));
        when(body.getOpenInventory()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(grid);
        return body;
    }
}
