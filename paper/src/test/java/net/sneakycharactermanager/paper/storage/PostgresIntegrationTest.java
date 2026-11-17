package net.sneakycharactermanager.paper.storage;

import com.sneakydb.SneakyDBApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.io.File;
import java.net.URI;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL, independent connections/four workers like SneakyDB.
 * Opt-in only: loopback disposable cluster. Creates a random database and drops ONLY that database.
 * Applies every jar {@code db/migrations} script to an empty {@code scm} schema (operator creates the schema).
 */
@EnabledIfEnvironmentVariable(named="SCM_TEST_JDBC_URL", matches="jdbc:postgresql://127\\.0\\.0\\.1:.*")
class PostgresIntegrationTest {
    static final String OWNER = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    static final String OTHER = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    static final Pattern MIGRATION = Pattern.compile("V0*(\\d+)__[A-Za-z0-9_-]+\\.sql");
    static String rootUrl, testUrl, user, password, database;
    static ExecutorService workers;
    static class Api implements SneakyDBApi {
        public CompletableFuture<Void> whenReady() { return CompletableFuture.completedFuture(null); }
        public CompletableFuture<Integer> migrate(String plugin, Class<?> anchor) {
            return CompletableFuture.completedFuture(2);
        }
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
    static void applyMigrations() throws Exception {
        URI uri = Objects.requireNonNull(PostgresIntegrationTest.class.getResource("/db/migrations")).toURI();
        File folder = Paths.get(uri).toFile();
        File[] scripts = Objects.requireNonNull(folder.listFiles((dir, name) -> MIGRATION.matcher(name).matches()));
        Arrays.sort(scripts, Comparator.comparingInt(f -> {
            Matcher m = MIGRATION.matcher(f.getName());
            assertTrue(m.matches(), f.getName());
            return Integer.parseInt(m.group(1));
        }));
        assertEquals(List.of(1, 2), Arrays.stream(scripts).map(f -> {
            Matcher m = MIGRATION.matcher(f.getName());
            m.matches();
            return Integer.parseInt(m.group(1));
        }).toList(), "numbered from 1 without gaps");
        try (Connection c = connection(testUrl)) {
            c.createStatement().execute("DROP SCHEMA IF EXISTS scm CASCADE");
            c.createStatement().execute("CREATE SCHEMA scm");
            c.setAutoCommit(false);
            for (File script : scripts) {
                try (Statement s = c.createStatement()) { s.execute(Files.readString(script.toPath())); }
                c.commit();
            }
        }
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
        applyMigrations();
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
        for (int i = 0; i < 20; i++) creates.add(store.ensureOwner(OWNER, "Player"));
        CompletableFuture.allOf(creates.toArray(CompletableFuture[]::new)).join();
        assertEquals(1, list(OWNER).characters().size());
        var loaded = (DatabaseOperations.Loaded)run(new CharacterRequest.Join(OWNER)).getFirst();
        assertTrue(loaded.forced());
        assertEquals(list(OWNER).characters().getFirst().id(), loaded.character().id());
    }
    @Test void createSelectNameUpdatesDisabledSearchAndRevisionPersist() {
        var initial = list(OWNER).characters().getFirst();
        var created = (DatabaseOperations.Loaded)run(new CharacterRequest.Create(OWNER)).getFirst();
        String second = created.character().id();
        run(new CharacterRequest.Update(OWNER, second, 2, new Object[]{"<red>Captain &aMouse"}));
        run(new CharacterRequest.Update(OWNER, second, 4, new Object[]{"tag,other"}));
        run(new CharacterRequest.Update(OWNER, second, 5, new Object[]{"feminine"}));
        run(new CharacterRequest.Update(OWNER, second, 7, new Object[]{"unresolved_title"}));
        run(new CharacterRequest.Select(OWNER, initial.id()));
        var matched = (DatabaseOperations.Loaded)run(new CharacterRequest.SelectByName(OWNER, "captain")).getFirst();
        assertEquals(second, matched.character().id());
        assertEquals("tag,other", matched.character().tags()); assertEquals("feminine", matched.character().gender());
        assertEquals("unresolved_title", matched.character().title());
        run(new CharacterRequest.Update(OWNER, second, 3, new Object[]{false}));
        assertEquals(List.of(initial.id()), list(OWNER).characters().stream().map(CharacterView::id).toList());
        var results = (DatabaseOperations.Found)run(new CharacterRequest.Search(OWNER, "Captain")).getFirst();
        assertEquals(1, results.entries().size()); // disabled characters are still globally searchable
        run(new CharacterRequest.Update(OWNER, second, 3, new Object[]{true}));
        assertEquals(List.of(initial.id(), second), list(OWNER).characters().stream().map(CharacterView::id).toList());
        assertTrue(((Number)store.snapshot(OWNER).join().getFirst().get("revision")).longValue() >= 9);
    }
    @Test void directoryListsOfflineCharactersInMenuOrderAndFindsAnyById() {
        String owner = "11111111-1111-1111-1111-111111111111";
        var directory = new SqlCharacterDirectory(store, java.util.logging.Logger.getAnonymousLogger());
        String first = list(owner).characters().getFirst().id();
        String second = ((DatabaseOperations.Loaded)run(new CharacterRequest.Create(owner)).getFirst()).character().id();
        String third = ((DatabaseOperations.Loaded)run(new CharacterRequest.Create(owner)).getFirst()).character().id();
        run(new CharacterRequest.Update(owner, second, 2, new Object[]{"<red>Captain &aMouse"}));
        run(new CharacterRequest.Update(owner, third, 3, new Object[]{false}));
        var mine = directory.charactersOf(UUID.fromString(owner)).join();
        assertEquals(List.of(first, second), mine.stream().map(c -> c.id().toString()).toList());
        assertEquals("Captain Mouse", mine.get(1).name());
        var disabled = directory.character(UUID.fromString(third)).join().orElseThrow();
        assertFalse(disabled.enabled());
        assertEquals(owner, disabled.owner().toString());
        assertTrue(directory.character(UUID.randomUUID()).join().isEmpty());
        assertTrue(directory.charactersOf(UUID.randomUUID()).join().isEmpty());
    }
    @Test void characterIdsAreGlobal() throws Exception {
        String player = list(OWNER).characters().getFirst().id();
        store.ensureOwner(OTHER, "Other").join();
        SQLException duplicate = assertThrows(SQLException.class, () -> sql(
                "INSERT INTO scm.characters(owner_id, character_id) VALUES ('" + OTHER + "'::uuid, '" + player + "'::uuid)"));
        assertEquals("23505", duplicate.getSQLState());
    }
    @Test void missingSkinRefSurvives() {
        String id = list(OWNER).characters().getFirst().id();
        run(new CharacterRequest.Update(OWNER, id, 6, new Object[]{"unresolved-skin-id", false}));
        var missing = list(OWNER).characters().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
        assertEquals("unresolved-skin-id", missing.skinId()); assertEquals("", missing.skin());
        assertEquals("unresolved-skin-id", store.snapshot(OWNER).join().stream()
                .filter(row -> id.equals(SqlStore.text(row, "character_id"))).findFirst().orElseThrow().get("skin_id"));
    }
    @Test void skinsResolveRegisterInlineMigrationAndUniformVariantsRoundTrip() {
        var miss = (DatabaseOperations.Resolved)run(new CharacterRequest.ResolveSkin(OWNER, OWNER, "http://example.test/image.png", false)).getFirst();
        assertEquals("MISS", miss.status()); assertEquals("content-hash", miss.id());
        run(new CharacterRequest.RegisterSkin(miss.id(), "https://textures.minecraft.net/texture/test", "texture", "signature", "http://example.test/image.png"));
        var hit = (DatabaseOperations.Resolved)run(new CharacterRequest.ResolveSkin(OWNER, OWNER, "http://example.test/image.png", false)).getFirst();
        assertEquals("HIT", hit.status()); assertEquals("texture", hit.texture());
        String id = list(OWNER).characters().getFirst().id();
        run(new CharacterRequest.Update(OWNER, id, 6, new Object[]{miss.id(), true}));
        run(new CharacterRequest.Uniform("http://textures.minecraft.net/texture/test", "uniform", "u", "variant-url", "vt", "vs"));
        var listed = list(OWNER);
        assertEquals("http://textures.minecraft.net/texture/test", listed.characters().getFirst().skin());
        assertEquals("texture", listed.characters().getFirst().texture());
        assertEquals(1, listed.uniforms().size());
        run(new CharacterRequest.Update(OWNER, id, 1, new Object[]{"http://textures.minecraft.net/texture/test", "", "texture", "signature", false}));
        assertEquals("content-hash", list(OWNER).characters().getFirst().skinId());
        run(new CharacterRequest.DefaultSkin(OWNER, id, "default-url", true));
        assertEquals("content-hash", list(OWNER).characters().getFirst().skinId());
        run(new CharacterRequest.Update(OWNER, id, 1, new Object[]{"reset-url", "", "", "", false}));
        assertEquals("reset-url", list(OWNER).characters().getFirst().skin());
    }
    @Test void delayedDefaultInitializationCannotOverwriteNewerSkinOnAnotherConnection() {
        String id = list(OWNER).characters().getFirst().id();
        run(new CharacterRequest.DefaultSkin(OWNER, id, "initial-url", true));
        assertEquals("initial-url", list(OWNER).characters().getFirst().skin());
        new SqlStore(new Api(), 5).skin(OWNER, id, "new-custom", "", "", "", "", false).join();
        run(new CharacterRequest.DefaultSkin(OWNER, id, "stale-default", true));
        assertEquals("new-custom", list(OWNER).characters().getFirst().skinId());
        assertFalse(list(OWNER).characters().getFirst().slim());
    }
    @Test void concurrentTitlesReturnTheSameCanonicalWinnerAndLiteralSearchEscapesNothing() {
        var a = store.createTitle("captain", "A"); var b = store.createTitle("captain", "B");
        assertEquals(a.join().getFirst().get("display"), b.join().getFirst().get("display"));
        var event = (DatabaseOperations.TitleCreated)run(new CharacterRequest.CreateTitle(OWNER, "t", "CAPTAIN", "C")).getLast();
        assertEquals(a.join().getFirst().get("display"), event.display());
        String id = list(OWNER).characters().getFirst().id();
        run(new CharacterRequest.Update(OWNER, id, 2, new Object[]{"100%_literal"}));
        assertEquals(1, store.search("%_").join().size()); assertEquals(0, store.search("' OR true --").join().size());
    }
    @Test void importedInvalidLastPlayedAndInlineSkinFallbackAreNeverRepaired() throws Exception {
        list(OWNER);
        String missing = "cccccccc-cccc-cccc-cccc-cccccccccccc";
        sql("UPDATE scm.owners SET last_played_character='" + missing + "'::uuid WHERE owner_id='" + OWNER + "'::uuid");
        sql("UPDATE scm.characters SET skin_id='missing', skin='legacy-url', texture='inline', signature='signed'");
        assertInstanceOf(DatabaseOperations.NotFound.class, run(new CharacterRequest.Join(OWNER)).getFirst());
        assertEquals(missing, SqlStore.text(store.snapshot(OWNER).join().getFirst(), "last_played_character"));
        assertEquals("legacy-url", list(OWNER).characters().getFirst().skin());
        assertEquals("inline", list(OWNER).characters().getFirst().texture());
        assertEquals(1, list(OWNER).characters().size());
    }
    @Test void concurrentFieldWritesDoNotClobberOtherFieldsAndQueueKeepsPerOwnerOrder() {
        String id = list(OWNER).characters().getFirst().id();
        var a = store.update(OWNER, id, "name", "A"); var b = store.update(OWNER, id, "tags", "B");
        CompletableFuture.allOf(a,b).join();
        var c = list(OWNER).characters().getFirst(); assertEquals("A", c.name()); assertEquals("B", c.tags());
        OwnerQueue queue = new OwnerQueue(64);
        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (int i = 0; i < 20; i++) { String value = "name-" + i; writes.add(queue.submit(OWNER, () -> store.update(OWNER, id, "name", value))); }
        CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();
        assertEquals("name-19", list(OWNER).characters().getFirst().name());
    }
    @Test void uuidIdentityColumnsAndNoSchemaVersion() throws Exception {
        try (Connection c = connection(testUrl); ResultSet types = c.getMetaData().getColumns(null, "scm", "owners", "owner_id")) {
            assertTrue(types.next());
            assertEquals("uuid", types.getString("TYPE_NAME"));
        }
        try (Connection c = connection(testUrl); ResultSet tables = c.getMetaData().getTables(null, "scm", "schema_version", null)) {
            assertFalse(tables.next());
        }
        assertEquals(0, ((Number) api.query("SELECT count(*) AS n FROM scm.owners", List.of()).join().getFirst().get("n")).intValue());
    }
}
