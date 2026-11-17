package net.sneakycharactermanager.paper.storage;

import com.sneakydb.SneakyDBApi;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SqlStoreTest {
    static class Api implements SneakyDBApi {
        List<String> statements = new ArrayList<>();
        List<List<?>> bindings = new ArrayList<>();
        List<Map<String,Object>> rows = List.of();
        CompletableFuture<Integer> migrated = new CompletableFuture<>();
        public CompletableFuture<Void> whenReady() { return migrated.thenApply(v -> null); }
        public CompletableFuture<Integer> migrate(String plugin, Class<?> anchor) { return migrated; }
        public boolean isAvailable() { return true; }
        public CompletableFuture<List<Map<String,Object>>> query(String sql, List<?> args) {
            statements.add(sql); bindings.add(args); return CompletableFuture.completedFuture(rows);
        }
        public CompletableFuture<Integer> execute(String sql, List<?> args) {
            statements.add(sql); bindings.add(args); return CompletableFuture.completedFuture(1);
        }
    }
    @Test void closeStopsFollowupStatementsWithoutCancellingAnUncertainProviderCall() {
        Api api = new Api();
        SqlStore store = new SqlStore(api, 1);
        store.close();
        api.migrated.complete(2);
        assertThrows(CompletionException.class, () -> store.ready().join());
        assertTrue(api.statements.isEmpty());
        assertThrows(CompletionException.class, () -> store.ensureOwner("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "Name").join());
        assertTrue(api.statements.isEmpty());
    }
    @Test void readinessWaitsForMigrationsPastTheRequestTimeoutAndNeverMutatesProviderFuture() throws Exception {
        Api api = new Api(); SqlStore store = new SqlStore(api, 1);
        // SneakyDB finishes migrations after every plugin has enabled: longer than the request timeout.
        assertThrows(TimeoutException.class, () -> store.ready().get(1500, TimeUnit.MILLISECONDS));
        assertFalse(store.ready().isDone());
        assertFalse(api.migrated.isDone());
        assertTrue(api.statements.isEmpty());
        api.migrated.complete(2);
        store.ready().join();
        assertEquals(1, api.statements.size());
    }
    @Test void failedMigrationFailsReadinessWithoutTouchingTables() {
        Api api = new Api(); SqlStore store = new SqlStore(api, 1);
        api.migrated.completeExceptionally(new IllegalStateException("migrations failed"));
        assertThrows(CompletionException.class, () -> store.ready().join());
        assertTrue(api.statements.isEmpty());
    }
    @Test void readinessQueriesSmokeOnlyAfterMigrateCompletes() {
        Api api = new Api();
        SqlStore store = new SqlStore(api, 5);
        assertTrue(api.statements.isEmpty());
        assertFalse(store.ready().isDone());
        api.migrated.complete(2);
        store.ready().join();
        assertEquals(1, api.statements.size());
        assertTrue(api.statements.getFirst().contains("FROM scm.owners"));
        assertFalse(api.statements.getFirst().contains("schema_version"));
    }
    @Test void initializationIsSingleAtomicStatementWithBoundLiteralOwner() {
        Api api = new Api(); api.migrated.complete(2);
        SqlStore store = new SqlStore(api, 5); store.ready().join(); api.statements.clear();
        store.ensureOwner("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "Name").join();
        assertEquals(1, api.statements.size());
        assertTrue(api.statements.getFirst().contains("WITH new_owner AS"));
        assertTrue(api.statements.getFirst().contains("ON CONFLICT (owner_id) DO NOTHING"));
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", api.bindings.getLast().getFirst());
    }
    @Test void updateParametersNeverBecomeSqlAndRevisionLocksOwner() {
        Api api = new Api(); api.migrated.complete(2);
        SqlStore store = new SqlStore(api, 5);
        store.update("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                "name", "x'; DROP TABLE scm.characters; --").join();
        String sql = api.statements.getLast();
        assertFalse(sql.contains("DROP TABLE"));
        assertTrue(sql.contains("revision = revision + 1"));
        assertEquals("x'; DROP TABLE scm.characters; --", api.bindings.getLast().get(1));
        assertThrows(IllegalArgumentException.class, () -> store.update("a", "b", "untrusted", "bad"));
    }
}
