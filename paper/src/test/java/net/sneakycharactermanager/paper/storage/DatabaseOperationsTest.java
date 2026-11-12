package net.sneakycharactermanager.paper.storage;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseOperationsTest {
    @Test void expiredOperationCannotIssueFollowupCreationAfterLateInitialization() throws Exception {
        CompletableFuture<Integer> initialization = new CompletableFuture<>();
        SqlStoreTest.Api api = new SqlStoreTest.Api() {
            @Override public CompletableFuture<Integer> execute(String sql, List<?> args) {
                statements.add(sql); return initialization;
            }
        };
        api.ready.complete(null);
        SqlStore store = new SqlStore(api, 5);
        DatabaseOperations operations = new DatabaseOperations(store, url -> CompletableFuture.completedFuture("hash"));
        var future = operations.run(new CharacterRequest.Create("owner"), "Name", System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(10));
        Thread.sleep(30);
        initialization.complete(1);
        assertThrows(CompletionException.class, future::join);
        assertEquals(1, api.statements.stream().filter(sql -> sql.contains("INSERT INTO scm.characters")).count());
    }
    @Test void listPreservesInsertionOrderFiltersDisabledAndKeepsMissingSkinReference() {
        SqlStoreTest.Api api = new SqlStoreTest.Api(); api.ready.complete(null);
        SqlStore store = new SqlStore(api, 5);
        api.rows = List.of(row("second", true, "missing"), row("first", false, ""), row("third", true, ""));
        DatabaseOperations operations = new DatabaseOperations(store, url -> CompletableFuture.completedFuture("hash"));
        var events = operations.run(new CharacterRequest.ListCharacters("owner", "requester"), "Name").join();
        var listed = (DatabaseOperations.Listed)events.getFirst();
        assertEquals(List.of("second", "third"), listed.characters().stream().map(CharacterView::id).toList());
        assertEquals("missing", listed.characters().getFirst().skinId());
        assertEquals("", listed.characters().getFirst().skin());
    }
    @Test void titleCreationReturnsCanonicalWinnerNotAttemptedDisplay() {
        SqlStoreTest.Api api = new SqlStoreTest.Api(); api.ready.complete(null);
        SqlStore store = new SqlStore(api, 5);
        api.rows = List.of(Map.of("code", "captain", "display", "Winner"));
        DatabaseOperations operations = new DatabaseOperations(store, url -> CompletableFuture.completedFuture("hash"));
        var events = operations.run(new CharacterRequest.CreateTitle("admin", "target", "CAPTAIN", "Loser"), "").join();
        var result = (DatabaseOperations.TitleCreated)events.getLast();
        assertEquals("Winner", result.display());
        assertEquals("captain", result.code());
    }
    private Map<String,Object> row(String id, boolean enabled, String skinId) {
        return Map.of("character_id", id, "enabled", enabled, "name", id, "skin_id", skinId);
    }
}
