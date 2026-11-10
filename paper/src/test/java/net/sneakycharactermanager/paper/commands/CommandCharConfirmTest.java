package net.sneakycharactermanager.paper.commands;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.util.ProxyMessagingUtil;

/** {@code /char confirm} disables (deletes) one of the sender's own characters, re-checked at confirm time. */
class CommandCharConfirmTest {
    final Player player = mock(Player.class);
    final UUID owner = UUID.randomUUID();
    final CommandChar command = new CommandChar();

    @AfterEach
    void clearPending() {
        CommandChar.deleteConfirmationMap.remove(player);
    }

    private void startDeletion(String characterId) {
        when(player.getUniqueId()).thenReturn(owner);
        CommandChar.deleteConfirmationMap.put(player, System.currentTimeMillis() + ";" + characterId);
    }

    // Guards: a character loaded (by its owner or a borrower) after "delete" was clicked got disabled while being played.
    @Test
    void confirmRefusesWhenTheCharacterIsBeingPlayedNow() {
        startDeletion("char-1");
        try (MockedStatic<Character> characters = mockStatic(Character.class);
             MockedStatic<ProxyMessagingUtil> proxy = mockStatic(ProxyMessagingUtil.class)) {
            characters.when(() -> Character.wearerOf(new CharacterKey(owner, "char-1"))).thenReturn(mock(Player.class));

            assertFalse(command.execute(player, "char", new String[]{"confirm"}));

            proxy.verifyNoInteractions();
            assertFalse(CommandChar.deleteConfirmationMap.containsKey(player), "pending deletion must be dropped");
            verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
        }
    }

    // Guards: the delete must address (sender, id), the owner's own record, and still go through when nobody wears it.
    @Test
    void confirmDisablesTheOwnersCharacterWhenNobodyWearsIt() {
        startDeletion("char-1");
        try (MockedStatic<Character> characters = mockStatic(Character.class);
             MockedStatic<ProxyMessagingUtil> proxy = mockStatic(ProxyMessagingUtil.class)) {
            characters.when(() -> Character.wearerOf(any())).thenReturn(null);

            assertTrue(command.execute(player, "char", new String[]{"confirm"}));

            characters.verify(() -> Character.wearerOf(new CharacterKey(owner, "char-1")));
            proxy.verify(() -> ProxyMessagingUtil.sendByteArray(player, "updateCharacter",
                    owner.toString(), "char-1", 3, false));
            assertFalse(CommandChar.deleteConfirmationMap.containsKey(player));
        }
    }
}
