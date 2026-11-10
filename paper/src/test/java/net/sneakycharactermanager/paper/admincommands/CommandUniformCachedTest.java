package net.sneakycharactermanager.paper.admincommands;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.skins.SkinApplyService;
import net.sneakycharactermanager.paper.handlers.skins.SkinStateManager;
import net.sneakycharactermanager.paper.util.SkinUtil;

/** A cached uniform finishes after an async file hash; it must not land on a character the player switched to. */
class CommandUniformCachedTest {
    final CommandSender sender = mock(CommandSender.class);
    final Player player = mock(Player.class);
    final Character requested = mock(Character.class);
    final CharacterKey key = new CharacterKey(UUID.randomUUID(), "requested");

    @Test
    void cachedUniformIsNotAppliedAfterTheCharacterChanged() {
        try (var plugins = mockStatic(SneakyCharacterManager.class);
             var characters = mockStatic(Character.class);
             var skins = mockStatic(SkinUtil.class);
             var service = mockStatic(SkinApplyService.class)) {
            SneakyCharacterManager plugin = setUp(plugins);
            characters.when(() -> Character.isWearing(player, key)).thenReturn(false);

            assertFalse(CommandUniform.applyCachedVariant(sender, player, requested, "police", "url", "texture", "signature"));

            skins.verify(() -> SkinUtil.applySkin(any(), any()), never());
            verifyNoInteractions(plugin.skinStateManager);
        }
    }

    @Test
    void cachedUniformIsAppliedWhileTheCharacterIsStillWorn() {
        try (var plugins = mockStatic(SneakyCharacterManager.class);
             var characters = mockStatic(Character.class);
             var skins = mockStatic(SkinUtil.class);
             var service = mockStatic(SkinApplyService.class);
             var messages = mockStatic(SkinStateManager.class)) {
            SneakyCharacterManager plugin = setUp(plugins);
            characters.when(() -> Character.isWearing(player, key)).thenReturn(true);

            assertTrue(CommandUniform.applyCachedVariant(sender, player, requested, "police", "url", "texture", "signature"));

            skins.verify(() -> SkinUtil.applySkin(eq(player), any()));
            verify(plugin.skinStateManager).record(player, null, "texture", "signature", key, "url", true);
        }
    }

    private SneakyCharacterManager setUp(org.mockito.MockedStatic<SneakyCharacterManager> plugins) {
        SneakyCharacterManager plugin = mock(SneakyCharacterManager.class);
        plugins.when(SneakyCharacterManager::getInstance).thenReturn(plugin);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        plugin.skinStateManager = mock(SkinStateManager.class);
        when(player.isOnline()).thenReturn(true);
        when(requested.getKey()).thenReturn(key);
        return plugin;
    }
}
