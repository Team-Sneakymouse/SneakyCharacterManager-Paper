package net.sneakycharactermanager.paper.storage;

import org.junit.jupiter.api.Test;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.CharacterSelectionMenu;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseBackendTest {
    static class Api extends SqlStoreTest.Api {
        List<CompletableFuture<List<Map<String,Object>>>> snapshots = new ArrayList<>();
        @Override public CompletableFuture<List<Map<String,Object>>> query(String sql, List<?> args) {
            if (sql.contains("o.last_played_character, o.revision, c.*")) {
                var pending = new CompletableFuture<List<Map<String,Object>>>(); snapshots.add(pending); return pending;
            }
            return super.query(sql,args);
        }
    }
    @Test void oldOfflineOwnerMenuCannotPopulateNewMenuForAnotherOwner() {
        Api api = new Api(); api.ready.complete(null);
        SneakyCharacterManager plugin = mock(SneakyCharacterManager.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        plugin.selectionMenu = mock(CharacterSelectionMenu.class);
        plugin.selectionMenu.activeMenus = new HashMap<>();
        when(plugin.selectionMenu.menuExists(anyString())).thenReturn(true);
        Player player = mock(Player.class); UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        List<Runnable> deliveries = new ArrayList<>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> { deliveries.add(invocation.getArgument(1)); return null; });
        String a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString();
        String routing = uuid + "|openGUI";
        try (var bukkit = mockStatic(Bukkit.class); var effects = mockStatic(CharacterResultEffects.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
            DatabaseBackend backend = new DatabaseBackend(plugin, new SqlStore(api,5));
            backend.submit(player, new CharacterRequest.ListCharacters(a, routing));
            backend.submit(player, new CharacterRequest.ListCharacters(b, routing));
            api.snapshots.getLast().complete(List.of()); api.snapshots.getFirst().complete(List.of());
            deliveries.forEach(Runnable::run);
            effects.verify(() -> CharacterResultEffects.handleListCompletion("syncCharacters", b, routing, List.of()));
            effects.verify(() -> CharacterResultEffects.handleListCompletion("syncCharacters", a, routing, List.of()), never());
            backend.close();
        }
    }
    @Test void staleSelectionCompletionCannotOpenMenuAfterNewerSelectionOrReconnect() {
        check(false, false);
    }
    @Test void disconnectRejectsCompletionEvenIfOldPlayerObjectIsStillVisible() {
        check(true, false);
    }
    @Test void disableRejectsAlreadyScheduledCompletions() {
        check(false, true);
    }
    @Test void delayedLoadedCannotReplaceSkinAfterNewerUpdateWithoutListEffect() { loadedAfterChange(false); }
    @Test void delayedLoadedCannotReplaceNewerObservedDatabaseRevision() { loadedAfterChange(true); }
    private void loadedAfterChange(boolean newerSnapshot) {
        Api api = new Api(); api.ready.complete(null);
        SneakyCharacterManager plugin = mock(SneakyCharacterManager.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        Player player = mock(Player.class); UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid); when(player.getName()).thenReturn("Player");
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        List<Runnable> deliveries = new ArrayList<>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> { deliveries.add(invocation.getArgument(1)); return null; });
        try (var bukkit = mockStatic(Bukkit.class); var effects = mockStatic(CharacterResultEffects.class);
             var characters = mockConstruction(net.sneakycharactermanager.paper.handlers.character.Character.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
            DatabaseBackend backend = new DatabaseBackend(plugin, new SqlStore(api,5));
            backend.submit(player, new CharacterRequest.Select(uuid.toString(), "char"));
            api.snapshots.getFirst().complete(List.of(Map.of("character_id", "char", "revision", 0L)));
            if (newerSnapshot) {
                backend.submit(player, new CharacterRequest.ListCharacters(uuid.toString(), uuid.toString()));
                api.snapshots.getLast().complete(List.of(Map.of("character_id", "char", "skin_id", "remote-custom", "revision", 2L)));
            } else backend.submit(player, new CharacterRequest.Update(uuid.toString(), "char", 6, new Object[]{"custom", false}));
            // SQL completed in order; deliberately deliver the older snapshot last.
            deliveries.getLast().run(); deliveries.getFirst().run();
            effects.verify(() -> CharacterResultEffects.load(any(), anyBoolean()), never());
            effects.verify(() -> CharacterResultEffects.defaultSkin(anyString(), anyString()), never());
            backend.close();
        }
    }
    private void check(boolean quit, boolean disable) {
        Api api = new Api(); api.ready.complete(null);
        SneakyCharacterManager plugin = mock(SneakyCharacterManager.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        plugin.selectionMenu = mock(CharacterSelectionMenu.class);
        Player player = mock(Player.class); UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid); when(player.getName()).thenReturn("Player");
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        List<Runnable> deliveries = new ArrayList<>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> { deliveries.add(invocation.getArgument(1)); return null; });
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
            DatabaseBackend backend = new DatabaseBackend(plugin, new SqlStore(api,5));
            backend.submit(player, new CharacterRequest.Select(uuid.toString(), "old"));
            backend.submit(player, new CharacterRequest.Select(uuid.toString(), "new"));
            assertEquals(1, api.snapshots.size());
            api.snapshots.getFirst().complete(List.of());
            assertEquals(2, api.snapshots.size());
            api.snapshots.getLast().complete(List.of());
            if (quit) backend.disconnect(player);
            if (disable) backend.close();
            deliveries.forEach(Runnable::run);
            verify(plugin.selectionMenu, times(quit || disable ? 0 : 1)).openMenu(player);
            backend.close();
        }
    }
}
