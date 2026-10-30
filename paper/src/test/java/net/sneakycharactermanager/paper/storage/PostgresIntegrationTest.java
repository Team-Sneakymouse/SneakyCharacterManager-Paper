package net.sneakycharactermanager.paper.storage;

import com.sneakydb.SneakyDBApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL, independent connections/four workers like SneakyDB.
 * Opt-in only: loopback disposable cluster. Creates a random database and drops ONLY that database.
 */
@EnabledIfEnvironmentVariable(named="SCM_TEST_JDBC_URL", matches="jdbc:postgresql://127\\.0\\.0\\.1:.*")
class PostgresIntegrationTest {
    static String rootUrl, testUrl, user, password, database;
    static ExecutorService workers;
    static class Api implements SneakyDBApi {
        public CompletableFuture<Void> whenReady() { return CompletableFuture.completedFuture(null); }
        public boolean isAvailable() { return true; }
        public CompletableFuture<List<Map<String,Object>>> query(String sql, List<?> args) {
            return CompletableFuture.supplyAsync(() -> {
                try (Connection c = connection(testUrl); PreparedStatement statement = prepare(c, sql, args);
                     ResultSet result = statement.executeQuery()) {
                    List<Map<String,Object>> rows = new ArrayList<>();
                    while (result.next()) {
                        Map<String,Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) row.put(result.getMetaData().getColumnLabel(i), result.getObject(i));
                        rows.add(row);
                    }
                    return rows;
                } catch (SQLException error) { throw new CompletionException(error); }
            }, workers);
        }
        public CompletableFuture<Integer> execute(String sql, List<?> args) {
            return CompletableFuture.supplyAsync(() -> {
                try (Connection c = connection(testUrl); PreparedStatement statement = prepare(c, sql, args)) {
                    return statement.executeUpdate();
                } catch (SQLException error) { throw new CompletionException(error); }
            }, workers);
        }
        static PreparedStatement prepare(Connection c, String sql, List<?> args) throws SQLException {
            PreparedStatement statement = c.prepareStatement(sql);
            statement.setQueryTimeout(5);
            for (int i = 0; i < args.size(); i++) statement.setObject(i + 1, args.get(i));
            return statement;
        }
    }
    Api api; SqlStore store; DatabaseOperations operations;
    static Connection connection(String url) throws SQLException { return DriverManager.getConnection(url, user, password); }
    static void sql(String statement) throws SQLException {
        try (Connection c = connection(testUrl); Statement s = c.createStatement()) { s.execute(statement); }
    }
    @BeforeAll static void createDisposableDatabase() throws Exception {
        rootUrl = System.getenv("SCM_TEST_JDBC_URL"); user = System.getenv("SCM_TEST_JDBC_USER"); password = System.getenv("SCM_TEST_JDBC_PASSWORD");
        database = "scm_java_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = connection(rootUrl); Statement s = c.createStatement()) { s.execute("CREATE DATABASE " + database); }
        testUrl = rootUrl.substring(0, rootUrl.lastIndexOf('/') + 1) + database;
        workers = Executors.newFixedThreadPool(4);
    }
    @AfterAll static void cleanup() throws Exception {
        if (workers != null) { workers.shutdown(); assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS)); }
        if (database != null) try (Connection c = connection(rootUrl); Statement s = c.createStatement()) { s.execute("DROP DATABASE " + database); }
    }
    @BeforeEach void schema() throws Exception {
        sql("DROP SCHEMA IF EXISTS scm CASCADE");
        sql(Files.readString(Path.of("../db/001_scm.sql")));
        api = new Api(); store = new SqlStore(api, 5); store.ready().join();
        operations = new DatabaseOperations(store, url -> CompletableFuture.completedFuture("content-hash"));
    }
    List<DatabaseOperations.Effect> run(CharacterRequest request) { return operations.run(request, "Player").join(); }
    DatabaseOperations.Listed list(String owner) {
        return (DatabaseOperations.Listed)run(new CharacterRequest.ListCharacters(owner, owner)).getFirst();
    }
    @Test void incompleteSchemaIsRejectedBeforeAnyIdentityWrites() throws Exception {
        sql("DROP TABLE scm.uniform_variants");
        SqlStore incomplete = new SqlStore(api, 5);
        assertThrows(CompletionException.class, () -> incomplete.ready().join());
        assertEquals(0, api.query("SELECT * FROM scm.owners", List.of()).join().size());
    }
    @Test void joinCreatesExactlyOneInitialCharacterAndLoadsLastPlayed() {
        List<CompletableFuture<Integer>> creates = new ArrayList<>();
        for (int i = 0; i < 20; i++) creates.add(store.ensureOwner("owner", "Player"));
        CompletableFuture.allOf(creates.toArray(CompletableFuture[]::new)).join();
        assertEquals(1, list("owner").characters().size());
        var loaded = (DatabaseOperations.Loaded)run(new CharacterRequest.Join("owner")).getFirst();
        assertTrue(loaded.forced());
        assertEquals(list("owner").characters().getFirst().id(), loaded.character().id());
    }
    @Test void createSelectNameUpdatesDisabledSearchAndRevisionPersist() {
        var initial = list("owner").characters().getFirst();
        var created = (DatabaseOperations.Loaded)run(new CharacterRequest.Create("owner")).getFirst();
        String second = created.character().id();
        run(new CharacterRequest.Update("owner", second, 2, new Object[]{"<red>Captain &aMouse"}));
        run(new CharacterRequest.Update("owner", second, 4, new Object[]{"tag,other"}));
        run(new CharacterRequest.Update("owner", second, 5, new Object[]{"feminine"}));
        run(new CharacterRequest.Update("owner", second, 7, new Object[]{"unresolved_title"}));
        run(new CharacterRequest.Select("owner", initial.id()));
        var matched = (DatabaseOperations.Loaded)run(new CharacterRequest.SelectByName("owner", "captain")).getFirst();
        assertEquals(second, matched.character().id());
        assertEquals("tag,other", matched.character().tags()); assertEquals("feminine", matched.character().gender());
        assertEquals("unresolved_title", matched.character().title());
        run(new CharacterRequest.Update("owner", second, 3, new Object[]{false}));
        assertEquals(List.of(initial.id()), list("owner").characters().stream().map(CharacterView::id).toList());
        var results = (DatabaseOperations.Found)run(new CharacterRequest.Search("requester", "Captain")).getFirst();
        assertEquals(1, results.entries().size()); // disabled characters are still globally searchable
        run(new CharacterRequest.Update("owner", second, 3, new Object[]{true}));
        assertEquals(List.of(initial.id(), second), list("owner").characters().stream().map(CharacterView::id).toList());
        assertTrue(((Number)store.snapshot("owner").join().getFirst().get("revision")).longValue() >= 9);
    }
    @Test void templateLiteralOwnerTemporaryAndInlineOrMissingSkinRefsSurvive() {
        run(new CharacterRequest.Template("legacy-id", "Template Name", "http://example.test/skin", "skin-uuid", true));
        var temp = (DatabaseOperations.Loaded)run(new CharacterRequest.Temporary("destination", "template", "legacy-id")).getFirst();
        assertTrue(temp.temporary()); assertEquals("destination", temp.destination()); assertEquals("template", temp.source());
        assertEquals("skin-uuid", temp.character().skinUuid());
        run(new CharacterRequest.Update("template", "legacy-id", 6, new Object[]{"unresolved-skin-id", false}));
        var missing = list("template").characters().stream().filter(c -> c.id().equals("legacy-id")).findFirst().orElseThrow();
        assertEquals("unresolved-skin-id", missing.skinId()); assertEquals("", missing.skin());
        assertEquals("unresolved-skin-id", store.snapshot("template").join().stream().filter(row -> "legacy-id".equals(row.get("character_id"))).findFirst().orElseThrow().get("skin_id"));
    }
    @Test void skinsResolveRegisterInlineMigrationAndUniformVariantsRoundTrip() {
        var miss = (DatabaseOperations.Resolved)run(new CharacterRequest.ResolveSkin("p", "r", "http://example.test/image.png", false)).getFirst();
        assertEquals("MISS", miss.status()); assertEquals("content-hash", miss.id());
        run(new CharacterRequest.RegisterSkin(miss.id(), "https://textures.minecraft.net/texture/test", "texture", "signature", "http://example.test/image.png"));
        var hit = (DatabaseOperations.Resolved)run(new CharacterRequest.ResolveSkin("p", "r", "http://example.test/image.png", false)).getFirst();
        assertEquals("HIT", hit.status()); assertEquals("texture", hit.texture());
        String id = list("owner").characters().getFirst().id();
        run(new CharacterRequest.Update("owner", id, 6, new Object[]{miss.id(), true}));
        run(new CharacterRequest.Uniform("http://textures.minecraft.net/texture/test", "uniform", "u", "variant-url", "vt", "vs"));
        var listed = list("owner");
        assertEquals("http://textures.minecraft.net/texture/test", listed.characters().getFirst().skin());
        assertEquals("texture", listed.characters().getFirst().texture());
        assertEquals(1, listed.uniforms().size());
        run(new CharacterRequest.Update("owner", id, 1, new Object[]{"http://textures.minecraft.net/texture/test", "", "texture", "signature", false}));
        assertEquals("content-hash", list("owner").characters().getFirst().skinId());
        run(new CharacterRequest.DefaultSkin("owner", id, "default-url", true));
        assertEquals("content-hash", list("owner").characters().getFirst().skinId());
        // Explicit /skin reset still uses Update type 1, not default initialization.
        run(new CharacterRequest.Update("owner", id, 1, new Object[]{"reset-url", "", "", "", false}));
        assertEquals("reset-url", list("owner").characters().getFirst().skin());
    }
    @Test void delayedDefaultInitializationCannotOverwriteNewerSkinOnAnotherConnection() {
        String id = list("owner").characters().getFirst().id();
        run(new CharacterRequest.DefaultSkin("owner", id, "initial-url", true));
        assertEquals("initial-url", list("owner").characters().getFirst().skin());
        new SqlStore(new Api(), 5).skin("owner", id, "new-custom", "", "", "", "", false).join();
        run(new CharacterRequest.DefaultSkin("owner", id, "stale-default", true));
        assertEquals("new-custom", list("owner").characters().getFirst().skinId());
        assertFalse(list("owner").characters().getFirst().slim());
    }
    @Test void concurrentTitlesReturnTheSameCanonicalWinnerAndLiteralSearchEscapesNothing() {
        var a = store.createTitle("captain", "A"); var b = store.createTitle("captain", "B");
        assertEquals(a.join().getFirst().get("display"), b.join().getFirst().get("display"));
        var event = (DatabaseOperations.TitleCreated)run(new CharacterRequest.CreateTitle("a", "t", "CAPTAIN", "C")).getLast();
        assertEquals(a.join().getFirst().get("display"), event.display());
        String id = list("owner").characters().getFirst().id();
        run(new CharacterRequest.Update("owner", id, 2, new Object[]{"100%_literal"}));
        assertEquals(1, store.search("%_").join().size()); assertEquals(0, store.search("' OR true --").join().size());
    }
    @Test void importedInvalidLastPlayedAndInlineSkinFallbackAreNeverRepaired() throws Exception {
        list("owner");
        sql("UPDATE scm.owners SET last_played_character='missing' WHERE owner_id='owner'");
        sql("UPDATE scm.characters SET skin_id='missing', skin='legacy-url', texture='inline', signature='signed'");
        assertInstanceOf(DatabaseOperations.NotFound.class, run(new CharacterRequest.Join("owner")).getFirst());
        assertEquals("missing", store.snapshot("owner").join().getFirst().get("last_played_character"));
        assertEquals("legacy-url", list("owner").characters().getFirst().skin());
        assertEquals("inline", list("owner").characters().getFirst().texture());
        assertEquals(1, list("owner").characters().size());
    }
    @Test void concurrentFieldWritesDoNotClobberOtherFieldsAndQueueKeepsPerOwnerOrder() {
        String id = list("owner").characters().getFirst().id();
        var a = store.update("owner", id, "name", "A"); var b = store.update("owner", id, "tags", "B");
        CompletableFuture.allOf(a,b).join();
        var c = list("owner").characters().getFirst(); assertEquals("A", c.name()); assertEquals("B", c.tags());
        OwnerQueue queue = new OwnerQueue(64);
        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (int i = 0; i < 20; i++) { String value = "name-" + i; writes.add(queue.submit("owner", () -> store.update("owner", id, "name", value))); }
        CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();
        assertEquals("name-19", list("owner").characters().getFirst().name());
    }
}
