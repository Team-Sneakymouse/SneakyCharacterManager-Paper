package net.sneakycharactermanager.paper.commands;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;
import net.sneakycharactermanager.paper.handlers.skins.SkinState;
import net.sneakycharactermanager.paper.handlers.skins.SkinStateManager;
import net.sneakycharactermanager.paper.util.ProxyMessagingUtil;
import net.sneakycharactermanager.paper.util.SkinUtil;

/** Picking an older skin (/skin state, /skin revert) makes it the newest: older queued skin jobs must not land later. */
class CommandSkinSupersedeTest {
    final MockedStatic<SneakyCharacterManager> plugins = mockStatic(SneakyCharacterManager.class);
    final MockedStatic<Character> characters = mockStatic(Character.class);
    final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
    final MockedStatic<SkinUtil> skins = mockStatic(SkinUtil.class);
    final MockedStatic<ProxyMessagingUtil> proxy = mockStatic(ProxyMessagingUtil.class);
    final SneakyCharacterManager plugin = mock(SneakyCharacterManager.class);
    final Player player = mock(Player.class);
    final UUID playerId = UUID.randomUUID();
    final CharacterKey key = new CharacterKey(UUID.randomUUID(), "worn");
    final Character worn = mock(Character.class);

    CommandSkinSupersedeTest() {
        plugins.when(SneakyCharacterManager::getInstance).thenReturn(plugin);
        plugin.skinQueue = mock(SkinQueue.class);
        plugin.skinStateManager = new SkinStateManager();
        when(player.getUniqueId()).thenReturn(playerId);
        when(worn.getKey()).thenReturn(key);
        when(worn.getSkin()).thenReturn("");
        when(worn.getSignature()).thenReturn("");
        characters.when(() -> Character.get(player)).thenReturn(worn);
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        bukkit.when(Bukkit::getAsyncScheduler).thenReturn(mock(AsyncScheduler.class)); // the Mojang fetch never runs
    }

    @AfterEach
    void close() {
        proxy.close(); skins.close(); bukkit.close(); characters.close(); plugins.close();
    }

    // Guards: a slow earlier /skin job finished after /skin state and overwrote the skin picked from history.
    @Test
    void skinStateSupersedesOlderQueuedJobs() {
        SkinState older = plugin.skinStateManager.record(player, "Regular", "t0", "s0", key, "url0", false);
        plugin.skinStateManager.record(player, "Regular", "t1", "s1", key, "url1", false);

        new CommandSkin().execute(player, "skin", new String[]{"state", String.valueOf(older.id())});

        verify(plugin.skinQueue).supersede(player, key);
    }

    // Guards: the same for /skin revert, which bypassed CharacterLoader.updateSkin's supersession.
    @Test
    void skinRevertSupersedesOlderQueuedJobs() {
        new CommandSkin().execute(player, "skin", new String[]{"revert"});

        verify(plugin.skinQueue).supersede(player, key);
    }
}
