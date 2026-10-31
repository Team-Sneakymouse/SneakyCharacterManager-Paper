package net.sneakycharactermanager.paper.listeners;

import com.destroystokyo.paper.profile.ProfileProperty;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.CharacterLoader;
import net.sneakycharactermanager.paper.handlers.nametags.NametagManager;
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;
import net.sneakycharactermanager.paper.handlers.skins.SkinStateManager;
import net.sneakycharactermanager.paper.storage.CharacterBackend;
import net.sneakycharactermanager.paper.storage.DatabaseBackend;
import net.sneakycharactermanager.paper.util.SkinUtil;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConnectionSkinSessionTest {
    @Test void databaseJoinUsesUpdatedPersistedSkinInsteadOfPreviousBackendVisit() {
        try (Fixture f = new Fixture(mock(DatabaseBackend.class))) {
            UUID other = UUID.randomUUID();
            Player otherPlayer = mock(Player.class);
            when(otherPlayer.getUniqueId()).thenReturn(other);
            f.plugin.skinStateManager.record(otherPlayer, "Other", "other", "sig", "char", "url", false);
            var old = f.plugin.skinStateManager.record(f.player, "Old uniform", "old", "old-sig", "char", "old-url", true);
            new ConnectionEventListeners().onPlayerJoin(new PlayerJoinEvent(f.player, (net.kyori.adventure.text.Component) null));
            assertTrue(CharacterLoader.loadCharacter(f.character));
            f.skins.verify(() -> SkinUtil.applySkin(f.player, new ProfileProperty("textures", "database-texture", "database-signature")));
            assertNull(f.plugin.skinStateManager.get(f.uuid, old.id()), "old undo/reapply buttons must not select a new session's state");
            assertNull(f.plugin.skinStateManager.current(f.uuid).previousId());
            assertNotNull(f.plugin.skinStateManager.current(other), "joining must not clear another player's history");
            assertEquals("database-texture", f.plugin.skinStateManager.current(f.uuid).texture());
        }
    }
    @Test void sameDatabaseSessionStillRestoresUniformOnCharacterReload() {
        try (Fixture f = new Fixture(mock(DatabaseBackend.class))) {
            new ConnectionEventListeners().onPlayerJoin(new PlayerJoinEvent(f.player, (net.kyori.adventure.text.Component) null));
            var uniform = f.plugin.skinStateManager.record(f.player, "Uniform", "uniform", "uniform-sig", "char", "url", true);
            assertTrue(CharacterLoader.loadCharacter(f.character));
            f.skins.verify(() -> SkinUtil.applySkin(f.player, new ProfileProperty("textures", "uniform", "uniform-sig")));
            assertSame(uniform, f.plugin.skinStateManager.current(f.uuid));
        }
    }
    @Test void legacyJoinKeepsExistingSkinHistoryBehavior() {
        try (Fixture f = new Fixture(null)) {
            var old = f.plugin.skinStateManager.record(f.player, "Uniform", "old", "sig", "char", "url", true);
            new ConnectionEventListeners().onPlayerJoin(new PlayerJoinEvent(f.player, (net.kyori.adventure.text.Component) null));
            assertSame(old, f.plugin.skinStateManager.current(f.uuid));
            assertTrue(CharacterLoader.loadCharacter(f.character));
            f.skins.verify(() -> SkinUtil.applySkin(f.player, new ProfileProperty("textures", "old", "sig")));
        }
    }
    private static class Fixture implements AutoCloseable {
        final UUID uuid = UUID.randomUUID();
        final Player player = mock(Player.class);
        final Character character = mock(Character.class);
        final SneakyCharacterManager plugin = mock(SneakyCharacterManager.class);
        final org.mockito.MockedStatic<SneakyCharacterManager> singleton = mockStatic(SneakyCharacterManager.class);
        final org.mockito.MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final org.mockito.MockedStatic<SkinUtil> skins = mockStatic(SkinUtil.class);
        Fixture(CharacterBackend backend) {
            singleton.when(SneakyCharacterManager::getInstance).thenReturn(plugin);
            when(plugin.getCharacterBackend()).thenReturn(backend);
            when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            plugin.skinStateManager = new SkinStateManager();
            plugin.skinQueue = mock(SkinQueue.class);
            plugin.nametagManager = mock(NametagManager.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            Server server = mock(Server.class);
            when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
            bukkit.when(Bukkit::getServer).thenReturn(server);
            when(player.getUniqueId()).thenReturn(uuid);
            when(character.getPlayer()).thenReturn(player);
            when(character.getCharacterUUID()).thenReturn("char");
            when(character.getName()).thenReturn("Character");
            when(character.getSkin()).thenReturn("http://textures.minecraft.net/texture/database");
            when(character.getTexture()).thenReturn("database-texture");
            when(character.getSignature()).thenReturn("database-signature");
        }
        @Override public void close() { skins.close(); bukkit.close(); singleton.close(); }
    }
}
