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
        List<Map<String,Object>> rows = List.of(Map.of("version", 1));
        CompletableFuture<Void> ready = new CompletableFuture<>();
        public CompletableFuture<Void> whenReady() { return ready; }
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
        api.ready.complete(null);
        assertThrows(CompletionException.class, () -> store.ready().join());
        assertTrue(api.statements.isEmpty());
        assertThrows(CompletionException.class, () -> store.ensureOwner("owner", "Name").join());
        assertTrue(api.statements.isEmpty());
    }
    @Test void pendingReadinessIsBoundedAndNeverMutatesProviderFuture() {
        Api api = new Api(); SqlStore store = new SqlStore(api, 1);
        assertThrows(CompletionException.class, () -> store.ready().join());
        assertFalse(api.ready.isDone());
        assertTrue(api.statements.isEmpty());
    }
    @Test void readinessDoesNotQueryUntilProviderReadyAndRejectsWrongSchema() {
        Api api = new Api();
        SqlStore store = new SqlStore(api, 5);
        assertTrue(api.statements.isEmpty());
        assertFalse(store.ready().isDone());
        api.rows = List.of(Map.of("version", 2));
        api.ready.complete(null);
        assertThrows(CompletionException.class, () -> store.ready().join());
    }
    @Test void initializationIsSingleAtomicStatementWithBoundLiteralOwner() {
        Api api = new Api(); api.ready.complete(null);
        SqlStore store = new SqlStore(api, 5); store.ready().join(); api.statements.clear();
        store.ensureOwner("template", "template").join();
        assertEquals(1, api.statements.size());
        assertTrue(api.statements.getFirst().contains("WITH new_owner AS"));
        assertTrue(api.statements.getFirst().contains("ON CONFLICT (owner_id) DO NOTHING"));
        assertEquals("template", api.bindings.getLast().getFirst());
    }
    @Test void updateParametersNeverBecomeSqlAndRevisionLocksOwner() {
        Api api = new Api(); api.ready.complete(null);
        SqlStore store = new SqlStore(api, 5);
        store.update("owner", "character", "name", "x'; DROP TABLE scm.characters; --").join();
        String sql = api.statements.getLast();
        assertFalse(sql.contains("DROP TABLE"));
        assertTrue(sql.contains("revision = revision + 1"));
        assertEquals("x'; DROP TABLE scm.characters; --", api.bindings.getLast().get(1));
        assertThrows(IllegalArgumentException.class, () -> store.update("a", "b", "untrusted", "bad"));
    }
}
