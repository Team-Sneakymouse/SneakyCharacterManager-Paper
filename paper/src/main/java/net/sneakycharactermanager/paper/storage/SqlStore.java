package net.sneakycharactermanager.paper.storage;

import com.sneakydb.SneakyDBApi;
import java.util.*;
import java.util.concurrent.*;

/** All SQL is SCM-owned. Each call is one statement on an independent pooled connection.
 * Mutations serialize through the owner row and increment its revision in the same statement.
 * There is deliberately no schema creation, importer, or YAML fallback here.
 */
public final class SqlStore {
    public static final String SCHEMA_CHECK = """
        SELECT v.version, o.owner_id, o.last_played_character, o.revision,
               c.character_id, c.ordinal, c.enabled, c.name, c.skin_id, c.skin, c.skin_uuid,
               c.texture, c.signature, c.slim, c.tags, c.gender, c.title,
               s.skin_id, s.mojang_url, s.texture, s.signature, a.source_url, a.skin_id,
               u.base_url, u.uniform_hash, u.skin_uuid, u.texture_url, u.texture, u.signature,
               t.code, t.display
        FROM scm.schema_version v
        LEFT JOIN scm.owners o ON false LEFT JOIN scm.characters c ON false
        LEFT JOIN scm.skins s ON false LEFT JOIN scm.skin_sources a ON false
        LEFT JOIN scm.uniform_variants u ON false LEFT JOIN scm.titles t ON false
        WHERE v.singleton = true
        """;
    private final SneakyDBApi api;
    private final int timeoutSeconds;
    private final CompletableFuture<Void> ready;
    private final java.util.concurrent.atomic.AtomicBoolean closed;
    private final long deadline;
    private SqlStore(SqlStore parent, long deadline) {
        api = parent.api; timeoutSeconds = parent.timeoutSeconds; ready = parent.ready;
        closed = parent.closed; this.deadline = deadline;
    }
    public SqlStore withDeadline(long deadline) { return new SqlStore(this, deadline); }
    public int timeoutSeconds() { return timeoutSeconds; }
    public void close() { closed.set(true); }

    public SqlStore(SneakyDBApi api, int timeoutSeconds) {
        this.api = api;
        this.closed = new java.util.concurrent.atomic.AtomicBoolean();
        this.deadline = 0;
        this.timeoutSeconds = timeoutSeconds;
        ready = bounded(api.whenReady()).thenCompose(unused -> query(SCHEMA_CHECK, List.of()))
                .thenAccept(rows -> {
                    if (rows.size() != 1 || !(rows.getFirst().get("version") instanceof Number n) || n.intValue() != 1)
                        throw new IllegalStateException("SCM schema version 1 must be operator-applied before enabling database storage");
                });
    }
    public CompletableFuture<Void> ready() { return ready.copy(); }
    private <T> CompletableFuture<T> bounded(CompletableFuture<T> future) {
        return future.copy().orTimeout(timeoutSeconds, TimeUnit.SECONDS);
    }
    CompletableFuture<List<Map<String,Object>>> query(String sql, List<?> args) {
        if (closed.get() || (deadline != 0 && System.nanoTime() - deadline >= 0) || !api.isAvailable()) return CompletableFuture.failedFuture(new IllegalStateException("SneakyDB unavailable or SCM disabled"));
        return bounded(api.query(sql, args));
    }
    CompletableFuture<Integer> execute(String sql, List<?> args) {
        if (closed.get() || (deadline != 0 && System.nanoTime() - deadline >= 0) || !api.isAvailable()) return CompletableFuture.failedFuture(new IllegalStateException("SneakyDB unavailable or SCM disabled"));
        return bounded(api.execute(sql, args));
    }

    public CompletableFuture<Integer> ensureOwner(String owner, String name) {
        String id = UUID.randomUUID().toString();
        String skin = owner.equals("template")
                ? "http://textures.minecraft.net/texture/b90696ebc74ce7a900ec8abeec0dc1ccb3534c1b8ba6cbd9e83c5cd7f381fb48" : "";
        return execute("""
            WITH new_owner AS (
                INSERT INTO scm.owners(owner_id, last_played_character) VALUES (?, ?)
                ON CONFLICT (owner_id) DO NOTHING RETURNING owner_id
            )
            INSERT INTO scm.characters(owner_id, character_id, name, skin)
            SELECT owner_id, ?, ?, ? FROM new_owner
            """, List.of(owner, id, id, name, skin));
    }

    // Fixed allowlist: untrusted field names never enter SQL.
    private static final Set<String> FIELDS = Set.of("name", "enabled", "tags", "gender", "title");
    public CompletableFuture<Integer> update(String owner, String character, String field, Object value) {
        if (!FIELDS.contains(field)) throw new IllegalArgumentException("Unknown character field");
        return mutate(owner, character, field + " = ?", List.of(value));
    }
    public CompletableFuture<Integer> skin(String owner, String character, String id, String url,
            String uuid, String texture, String signature, boolean slim) {
        return mutate(owner, character,
                "skin_id = ?, skin = ?, skin_uuid = ?, texture = ?, signature = ?, slim = ?",
                List.of(id, url, uuid, texture, signature, slim));
    }
    public CompletableFuture<Integer> initializeSkin(String owner, String character, String url, boolean slim) {
        return mutate(owner, character, "skin = ?, slim = ?", List.of(url, slim),
                " AND c.skin = '' AND c.skin_id = ''");
    }
    private CompletableFuture<Integer> mutate(String owner, String character, String assignment, List<?> values) {
        return mutate(owner, character, assignment, values, "");
    }
    private CompletableFuture<Integer> mutate(String owner, String character, String assignment, List<?> values, String condition) {
        List<Object> args = new ArrayList<>(); args.add(owner); args.addAll(values); args.add(character);
        return execute("WITH locked AS (UPDATE scm.owners SET revision = revision + 1 WHERE owner_id = ? RETURNING owner_id) "
                + "UPDATE scm.characters c SET " + assignment
                + " FROM locked WHERE c.owner_id = locked.owner_id AND c.character_id = ?" + condition, args);
    }
    public CompletableFuture<Integer> select(String owner, String character) {
        return execute("""
            UPDATE scm.owners SET last_played_character = ?, revision = revision + 1
            WHERE owner_id = ? AND EXISTS (
                SELECT 1 FROM scm.characters WHERE owner_id = ? AND character_id = ?)
            """, List.of(character, owner, owner, character));
    }
    public CompletableFuture<Integer> create(String owner, String id, String name, String skin, String skinUuid, boolean slim) {
        return execute("""
            WITH locked AS (
                UPDATE scm.owners SET revision = revision + 1 WHERE owner_id = ? RETURNING owner_id
            )
            INSERT INTO scm.characters(owner_id, character_id, name, skin, skin_uuid, slim)
            SELECT owner_id, ?, ?, ?, ?, ? FROM locked
            ON CONFLICT (owner_id, character_id) DO UPDATE
            SET enabled = true, name = EXCLUDED.name, skin_id = '', skin = EXCLUDED.skin,
                skin_uuid = EXCLUDED.skin_uuid, texture = '', signature = '', slim = EXCLUDED.slim,
                tags = '', gender = '', title = ''
            """, List.of(owner, id, name, skin, skinUuid, slim));
    }
    public CompletableFuture<List<Map<String,Object>>> snapshot(String owner) {
        return query("""
            SELECT o.last_played_character, o.revision, c.*, s.mojang_url AS cached_url,
                   s.texture AS cached_texture, s.signature AS cached_signature,
                   s.skin_id AS cached_id
            FROM scm.owners o LEFT JOIN scm.characters c ON c.owner_id = o.owner_id
            LEFT JOIN scm.skins s ON s.skin_id = c.skin_id
            WHERE o.owner_id = ? ORDER BY c.ordinal
            """, List.of(owner));
    }
    public CompletableFuture<List<Map<String,Object>>> uniforms(Collection<String> bases) {
        if (bases.isEmpty()) return CompletableFuture.completedFuture(List.of());
        String placeholders = String.join(",", Collections.nCopies(bases.size(), "?"));
        return query("SELECT * FROM scm.uniform_variants WHERE base_url IN (" + placeholders + ") ORDER BY base_url, uniform_hash", new ArrayList<>(bases));
    }
    public CompletableFuture<Integer> uniform(String base, String hash, String uuid, String url, String texture, String signature) {
        return execute("""
            INSERT INTO scm.uniform_variants(base_url, uniform_hash, skin_uuid, texture_url, texture, signature)
            VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (base_url, uniform_hash) DO UPDATE
            SET skin_uuid = EXCLUDED.skin_uuid, texture_url = EXCLUDED.texture_url,
                texture = EXCLUDED.texture, signature = EXCLUDED.signature
            """, List.of(base, hash, uuid, url, texture, signature));
    }
    public CompletableFuture<List<Map<String,Object>>> titles() {
        return query("SELECT code, display FROM scm.titles ORDER BY code", List.of());
    }
    public CompletableFuture<List<Map<String,Object>>> createTitle(String code, String display) {
        // DO UPDATE (no-op) returns the winning display even under a concurrent insert's snapshot.
        return query("""
            INSERT INTO scm.titles(code, display) VALUES (?, ?)
            ON CONFLICT (code) DO UPDATE SET display = scm.titles.display RETURNING code, display
            """, List.of(code, display));
    }
    public CompletableFuture<List<Map<String,Object>>> search(String filter) {
        // POSITION rather than LIKE: '%' and '_' are literal user input.
        return query("""
            SELECT owner_id, name FROM scm.characters
            WHERE position(lower(?) in lower(name)) > 0 ORDER BY owner_id, ordinal
            """, List.of(filter));
    }
    public CompletableFuture<List<Map<String,Object>>> findSkin(String key) {
        return query("""
            SELECT s.* FROM scm.skins s WHERE s.skin_id = ? OR s.mojang_url = ?
            OR s.skin_id IN (SELECT skin_id FROM scm.skin_sources WHERE source_url = ?)
            ORDER BY s.skin_id LIMIT 1
            """, List.of(key, key, key));
    }
    public CompletableFuture<Integer> registerSkin(String id, String url, String texture, String signature, String source) {
        return execute("""
            WITH saved AS (
                INSERT INTO scm.skins(skin_id, mojang_url, texture, signature) VALUES (?, ?, ?, ?)
                ON CONFLICT (skin_id) DO UPDATE SET mojang_url = EXCLUDED.mojang_url,
                    texture = EXCLUDED.texture, signature = EXCLUDED.signature RETURNING skin_id
            )
            INSERT INTO scm.skin_sources(source_url, skin_id) SELECT ?, skin_id FROM saved WHERE ? <> ''
            ON CONFLICT (source_url) DO UPDATE SET skin_id = EXCLUDED.skin_id
            """, List.of(id, url, texture, signature, source, source));
    }
    static String text(Map<String,Object> row, String key) {
        Object value = row.get(key); return value == null ? "" : String.valueOf(value);
    }
}
