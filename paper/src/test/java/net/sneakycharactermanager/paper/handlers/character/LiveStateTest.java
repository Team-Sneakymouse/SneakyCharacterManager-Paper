package net.sneakycharactermanager.paper.handlers.character;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
        LiveState.capture(a, true, true).apply(b);

        verify(scaleB).setBaseValue(0.8);
        verify(scaleB).removeModifier(buffB);
        verify(scaleB, never()).removeModifier(armourB);
        verify(scaleB).addModifier(buffA);
        verify(scaleB, never()).addModifier(armourA);
    }

    @Test
    void withoutManagedAttributesOnlyTheBaseValueMoves() {
        LiveState.capture(a, true, false).apply(b);

        verify(scaleB).setBaseValue(0.8);
        verify(scaleB, never()).removeModifier(any(AttributeModifier.class));
        verify(scaleB, never()).addModifier(any());
    }

    @Test
    void anUnmanagedInventoryStaysWithTheBody() {
        LiveState.capture(a, false, false).apply(b);

        verify(b.getInventory(), never()).setContents(any());
        verify(b, never()).setItemOnCursor(any());
        verify(gridB, never()).setMatrix(any());
        verify(b.getInventory()).setHeldItemSlot(3);
    }

    @Test
    void aManagedInventoryMovesWithItsCursorAndGrid() {
        LiveState.capture(a, true, false).apply(b);

        verify(b.getInventory()).setContents(any());
        verify(b).setItemOnCursor(any());
        verify(gridB).setMatrix(any());
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
        return grid;
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
