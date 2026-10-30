package net.sneakycharactermanager.paper.admincommands;

import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.util.ProxyMessagingUtil;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CommandSaveTemplateCharTest {
    @Test void sendsSkinUuidBeforeSlimForExistingProxyProtocolAndTypedBackend() {
        Player player = mock(Player.class);
        Character character = mock(Character.class);
        when(character.getName()).thenReturn("Name");
        when(character.getSkin()).thenReturn("url");
        when(character.getSkinUUID()).thenReturn("skin-uuid");
        when(character.isSlim()).thenReturn(true);
        try (var characters = mockStatic(Character.class); var messages = mockStatic(ProxyMessagingUtil.class)) {
            characters.when(() -> Character.get(player)).thenReturn(character);
            assertTrue(new CommandSaveTemplateChar().execute(player, "savetemplatechar", new String[]{"template-id"}));
            messages.verify(() -> ProxyMessagingUtil.sendByteArray(player, "saveTemplateChar", "template-id", "Name", "url", "skin-uuid", true));
        }
    }
}
