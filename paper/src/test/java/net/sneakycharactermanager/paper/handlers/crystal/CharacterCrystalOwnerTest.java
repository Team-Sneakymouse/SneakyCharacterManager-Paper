package net.sneakycharactermanager.paper.handlers.crystal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.CharacterSlots;
import net.sneakycharactermanager.paper.util.LuckPermsNodes;

/**
 * The crystal is an item in the worn character's inventory (the owner's), while the slot goes to the player's account:
 * a player wearing someone else's character must not spend it.
 */
class CharacterCrystalOwnerTest {
    final MockedStatic<Character> characters = mockStatic(Character.class);
    final MockedStatic<LuckPermsNodes> luckPerms = mockStatic(LuckPermsNodes.class);
    final MockedStatic<CharacterSlots> slots = mockStatic(CharacterSlots.class);
    final SneakyCharacterManager plugin = mock(SneakyCharacterManager.class);
    final Player player = mock(Player.class);
    final UUID playerId = UUID.randomUUID();
    final Character worn = mock(Character.class);

    CharacterCrystalOwnerTest() {
        plugin.luckPermsActive = true;
        when(player.getUniqueId()).thenReturn(playerId);
        characters.when(() -> Character.get(player)).thenReturn(worn);
        characters.when(() -> Character.isPlayedMapped(player)).thenReturn(true);
    }

    @AfterEach
    void close() {
        slots.close(); luckPerms.close(); characters.close();
    }

    @Test
    void onlyTheWornCharactersOwnerOwnsIt() {
        when(worn.getOwnerUUID()).thenReturn(playerId);
        assertTrue(CharacterCrystal.ownsWornCharacter(player));

        when(worn.getOwnerUUID()).thenReturn(UUID.randomUUID());
        assertFalse(CharacterCrystal.ownsWornCharacter(player));

        characters.when(() -> Character.get(player)).thenReturn(null);
        assertFalse(CharacterCrystal.ownsWornCharacter(player));
    }

    // Guards: a Supporter wearing someone else's character could spend that owner's crystal on their own slots.
    @Test
    void borrowedCharacterCannotSpendTheOwnersCrystal() {
        when(worn.getOwnerUUID()).thenReturn(UUID.randomUUID());
        when(worn.getKey()).thenReturn(new CharacterKey(UUID.randomUUID(), "borrowed"));
        CharacterCrystal crystal = new CharacterCrystal(plugin);

        crystal.onUse(rightClickWithCrystal());
        crystal.onUse(rightClickWithCrystal()); // the confirming second click

        luckPerms.verify(() -> LuckPermsNodes.addPermanent(any(), anyString()), never());
        verify(player, atLeast(2)).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void ownCharacterReachesTheConfirmStep() {
        when(worn.getOwnerUUID()).thenReturn(playerId);
        luckPerms.when(() -> LuckPermsNodes.user(player)).thenReturn(mock(net.luckperms.api.model.user.User.class));
        CharacterCrystal crystal = new CharacterCrystal(plugin);

        crystal.onUse(rightClickWithCrystal()); // first click only asks to confirm

        slots.verify(() -> CharacterSlots.isUnlimited(player)); // got past the ownership check
        luckPerms.verify(() -> LuckPermsNodes.addPermanent(any(), anyString()), never());
    }

    private PlayerInteractEvent rightClickWithCrystal() {
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(data.get(CharacterCrystal.MAGIC_ITEM_KEY, PersistentDataType.STRING)).thenReturn(CharacterCrystal.MAGIC_ITEM_NAME);
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        ItemStack item = mock(ItemStack.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(event.getItem()).thenReturn(item);
        when(event.useItemInHand()).thenReturn(Event.Result.DEFAULT);
        when(event.getPlayer()).thenReturn(player);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        return event;
    }
}
