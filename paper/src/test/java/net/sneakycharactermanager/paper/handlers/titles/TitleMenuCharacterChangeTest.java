package net.sneakycharactermanager.paper.handlers.titles;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.nametags.NametagManager;
import net.sneakycharactermanager.paper.util.ProxyMessagingUtil;

/** A /title menu is built for one character; a click must not equip onto a character loaded while it was open. */
class TitleMenuCharacterChangeTest {
    final MockedStatic<SneakyCharacterManager> plugins = mockStatic(SneakyCharacterManager.class);
    final MockedStatic<Character> characters = mockStatic(Character.class);
    final MockedStatic<ProxyMessagingUtil> proxy = mockStatic(ProxyMessagingUtil.class);
    final SneakyCharacterManager plugin = mock(SneakyCharacterManager.class);
    final Player player = mock(Player.class);
    final CharacterKey opened = new CharacterKey(UUID.randomUUID(), "opened");
    final Character worn = mock(Character.class);
    final InventoryClickEvent event = mock(InventoryClickEvent.class);
    TitleSelectionMenu menu;

    @BeforeEach
    void setUp() {
        plugins.when(SneakyCharacterManager::getInstance).thenReturn(plugin);
        when(plugin.namespace()).thenReturn("sneakycharacters");
        plugin.nametagManager = mock(NametagManager.class);
        plugin.titleRegistry = new TitleRegistry();
        plugin.titleRegistry.replaceAll(Map.of("knight", "Knight"));
        menu = new TitleSelectionMenu();

        TitleSelectionMenu.TitleMenuHolder holder = new TitleSelectionMenu.TitleMenuHolder(player, opened, "");
        Inventory inventory = mock(Inventory.class);
        when(inventory.getHolder()).thenReturn(holder);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getWhoClicked()).thenReturn(player);

        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.get(new NamespacedKey(plugin, "title_key"), PersistentDataType.STRING)).thenReturn("knight");
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        ItemStack item = mock(ItemStack.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(event.getCurrentItem()).thenReturn(item);

        when(player.hasPermission(TitleRegistry.permissionNode("knight"))).thenReturn(true);
        characters.when(() -> Character.get(player)).thenReturn(worn);
    }

    @AfterEach
    void close() {
        proxy.close(); characters.close(); plugins.close();
    }

    // Guards: clicking a title after switching character equipped it on (and saved it to) the newly loaded character.
    @Test
    void clickAfterCharacterSwitchClosesMenuWithoutEquipping() {
        when(worn.getKey()).thenReturn(new CharacterKey(opened.owner(), "switched-to"));
        characters.when(() -> Character.isWearing(player, opened)).thenReturn(false);

        menu.onClick(event);

        verify(event).setCancelled(true);
        verify(player).closeInventory();
        verify(worn, never()).setTitle(anyString());
        proxy.verifyNoInteractions();
        verifyNoInteractions(plugin.nametagManager);
    }

    // Guards: the re-check must not block the normal case; still on the opened character, the title is equipped and saved.
    @Test
    void clickWhileStillWearingEquipsAndSavesTheTitle() {
        when(worn.getKey()).thenReturn(opened);
        when(worn.getDisplayName()).thenReturn("Knight Bob");
        characters.when(() -> Character.isWearing(player, opened)).thenReturn(true);

        menu.onClick(event);

        verify(worn).setTitle("knight");
        proxy.verify(() -> ProxyMessagingUtil.sendByteArray(player, "updateCharacter",
                opened.owner().toString(), "opened", 7, "knight"));
        verify(plugin.nametagManager).nicknamePlayer(player, "Knight Bob");
        verify(player).closeInventory();
    }
}
