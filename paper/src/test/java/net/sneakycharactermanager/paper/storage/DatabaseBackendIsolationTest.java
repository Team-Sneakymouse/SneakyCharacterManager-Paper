package net.sneakycharactermanager.paper.storage;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.admincommands.CommandTitleGrant;
import net.sneakycharactermanager.paper.handlers.character.CharacterSelectionMenu;
import net.sneakycharactermanager.paper.handlers.titles.TitleRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseBackendIsolationTest {
    static class Api extends SqlStoreTest.Api {
        CompletableFuture<Integer> registration = new CompletableFuture<>();
        int registrations;
        final Map<String,String> catalog = new LinkedHashMap<>();
        @Override public CompletableFuture<Integer> execute(String sql, List<?> args) {
            if (sql.contains("INSERT INTO scm.skins")) { registrations++; return registration; }
            return super.execute(sql, args);
        }
        @Override public CompletableFuture<List<Map<String,Object>>> query(String sql, List<?> args) {
            if (sql.contains("INSERT INTO scm.titles")) {
                statements.add(sql); bindings.add(args);
                catalog.putIfAbsent((String) args.get(0), (String) args.get(1));
                return CompletableFuture.completedFuture(List.of(Map.of("code", args.get(0), "display", catalog.get(args.get(0)))));
            }
            if (sql.contains("SELECT code, display FROM scm.titles")) return CompletableFuture.completedFuture(
                    catalog.entrySet().stream().map(e -> Map.<String,Object>of("code", e.getKey(), "display", e.getValue())).toList());
            return super.query(sql, args);
        }
    }
    @Test void failedRegistrationDoesNotPoisonOtherOwnersOrGlobalOperations() {
        for (Throwable failure : List.of(new IllegalStateException("uncertain"), new TimeoutException("expired"),
                new RejectedExecutionException("full"))) {
            try (Fixture f = new Fixture()) {
                var skin = new CharacterRequest.RegisterSkin("skin", "url", "texture", "signature", "source");
                f.backend.submit(f.player, skin);
                f.api.registration.completeExceptionally(failure);
                // The original registration requester must see its failure before any retry.
                f.deliveries.getFirst().run();
                verify(f.player).sendMessage(any(net.kyori.adventure.text.Component.class));
                f.backend.submit(f.player, skin); // uncertain write must remain fenced, never retried
                f.backend.submit(f.player, new CharacterRequest.Join(f.id));
                f.backend.submit(f.player, new CharacterRequest.Select(f.id, "char"));
                f.backend.submit(f.player, new CharacterRequest.Update(f.id, "char", 6, new Object[]{"other-skin", false}));
                f.backend.submit(f.player, new CharacterRequest.CreateTitle(f.id, "target", "hero", "Hero"));
                f.backend.submit(f.player, new CharacterRequest.ResolveSkin(f.id, "resolve", "source", false));
                assertEquals(1, f.api.registrations);
                assertEquals(2, f.api.statements.stream().filter(s -> s.contains("o.last_played_character, o.revision, c.*")).count());
                assertTrue(f.api.statements.stream().anyMatch(s -> s.contains("SET skin_id")), "unrelated owner edit must reach SQL");
                assertTrue(f.api.statements.stream().anyMatch(s -> s.contains("INSERT INTO scm.titles")), "title creation must reach SQL");
                assertTrue(f.api.statements.stream().anyMatch(s -> s.contains("SELECT s.* FROM scm.skins")), "resolve must reach SQL");
            }
        }
    }
    @Test void rejectedRetryCannotReleaseAnotherPendingRegistrationBarrier() {
        try (Fixture f = new Fixture()) {
            var failed = new CharacterRequest.RegisterSkin("failed", "url", "texture", "signature", "source");
            f.backend.submit(f.player, failed);
            f.api.registration.completeExceptionally(new TimeoutException("uncertain"));
            f.api.registration = new CompletableFuture<>();
            f.backend.submit(f.player, new CharacterRequest.RegisterSkin("pending", "url", "texture", "signature", "source"));
            f.backend.submit(f.player, failed); // rejected immediately by the failed skin's quarantine
            f.backend.submit(f.player, new CharacterRequest.Update(f.id, "char", 6, new Object[]{"pending", false}));
            assertFalse(f.api.statements.stream().anyMatch(s -> s.contains("SET skin_id")));
            f.api.registration.complete(1);
            assertTrue(f.api.statements.stream().anyMatch(s -> s.contains("SET skin_id")));
            assertEquals(2, f.api.registrations);
        }
    }
    @Test void registrationStillOrdersDependentUpdateAndResolve() {
        try (Fixture f = new Fixture()) {
            f.backend.submit(f.player, new CharacterRequest.RegisterSkin("skin", "url", "texture", "signature", "source"));
            f.backend.submit(f.player, new CharacterRequest.Update(f.id, "char", 6, new Object[]{"skin", false}));
            f.backend.submit(f.player, new CharacterRequest.ResolveSkin(f.id, "resolve", "source", false));
            assertFalse(f.api.statements.stream().anyMatch(s -> s.contains("SET skin_id") || s.contains("SELECT s.*")));
            f.api.registration.complete(1);
            assertTrue(f.api.statements.stream().anyMatch(s -> s.contains("SET skin_id")));
            assertTrue(f.api.statements.stream().anyMatch(s -> s.contains("SELECT s.*")));
        }
    }
    @Test void independentTitleGrantsBothCompleteIncludingSameTargetAndReverseDelivery() { grants(false, false); }
    @Test void disconnectRejectsAllOutstandingTitleGrants() { grants(true, false); }
    @Test void disableRejectsAllOutstandingTitleGrants() { grants(false, true); }
    private void grants(boolean disconnect, boolean disable) {
        for (boolean sameTarget : List.of(false, true)) {
            try (Fixture f = new Fixture(); var handler = mockStatic(CommandTitleGrant.class);
                 var effects = mockStatic(CharacterResultEffects.class)) {
                effects.when(() -> CharacterResultEffects.titles(anyMap())).thenAnswer(i -> {
                    f.plugin.titleRegistry.replaceAll(i.getArgument(0)); return null;
                });
                String target = sameTarget ? "first-target" : "second-target";
                f.backend.submit(f.player, new CharacterRequest.CreateTitle(f.id, "first-target", "hero", "Hero"));
                f.backend.submit(f.player, new CharacterRequest.CreateTitle(f.id, target, "sage", "Sage"));
                if (disconnect) f.backend.disconnect(f.player);
                if (disable) f.backend.close();
                assertEquals(2, f.deliveries.size());
                f.deliveries.getLast().run(); f.deliveries.getFirst().run();
                int count = disconnect || disable ? 0 : 1;
                handler.verify(() -> CommandTitleGrant.handleCreateResult(f.id, "first-target", true, "hero", "Hero", ""), times(count));
                handler.verify(() -> CommandTitleGrant.handleCreateResult(f.id, target, true, "sage", "Sage", ""), times(count));
                assertEquals(disconnect || disable ? Map.of() : Map.of("hero", "Hero", "sage", "Sage"), f.plugin.titleRegistry.all());
            }
        }
    }
    static class Fixture implements AutoCloseable {
        final Api api = new Api();
        final SneakyCharacterManager plugin = mock(SneakyCharacterManager.class);
        final Player player = mock(Player.class);
        final UUID uuid = UUID.randomUUID();
        final String id = uuid.toString();
        final List<Runnable> deliveries = new ArrayList<>();
        final org.mockito.MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final DatabaseBackend backend;
        Fixture() {
            api.ready.complete(null);
            when(plugin.getLogger()).thenReturn(mock(Logger.class));
            plugin.selectionMenu = mock(CharacterSelectionMenu.class);
            plugin.titleRegistry = new TitleRegistry();
            when(player.getUniqueId()).thenReturn(uuid); when(player.getName()).thenReturn("Admin");
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(i -> { deliveries.add(i.getArgument(1)); return null; });
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
            backend = new DatabaseBackend(plugin, new SqlStore(api, 5));
        }
        public void close() { backend.close(); bukkit.close(); }
    }
}
