package net.sneakycharactermanager.paper.storage;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;
import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.common.titles.TitleKeys;
import net.sneakycharactermanager.proxy.common.SkinContentHash;
import net.sneakycharactermanager.proxy.common.SkinFetchException;
import static net.sneakycharactermanager.paper.storage.SqlStore.text;

/** Headless operation layer: SQL/HTTP in, immutable domain effects out; no Bukkit or wire packets. */
public final class DatabaseOperations {
    public sealed interface Effect {}
    public record Listed(String owner, String requester, long revision, List<CharacterView> characters,
                         List<Map<String,Object>> uniforms) implements Effect {}
    public record Loaded(String destination, String source, long revision, CharacterView character, boolean forced,
                         boolean borrowed, List<Map<String,Object>> uniforms) implements Effect {}
    public record NotFound(String player, boolean borrowed) implements Effect {}
    /** The owner picked a character another body wears; nothing was selected, so last-played is unchanged. */
    public record HeldElsewhere(String owner, String character) implements Effect {}
    public record Deleted(String player, String id, String name) implements Effect {}
    public record Catalog(Map<String,String> titles) implements Effect {}
    public record TitleCreated(String admin, String target, boolean success, String code, String display, String error) implements Effect {}
    public record Resolved(String requestId, String status, String id, String url, String texture, String signature, String error) implements Effect {}
    public record Found(String requester, List<String> entries) implements Effect {}
    private record Snapshot(String lastPlayed, long revision, List<CharacterView> characters, List<Map<String,Object>> uniforms) {}
    /** An owner's enabled characters in {@code /char} order, and their last-played character ID (empty if none). */
    public record Roster(String lastPlayed, List<CharacterView> characters) {}
    private final SqlStore store;
    private final Function<String, CompletableFuture<String>> hash;
    /** True if a body other than the owner wears the character (or is about to). Called on storage threads. */
    private final Predicate<CharacterKey> heldElsewhere;
    public DatabaseOperations(SqlStore store, Function<String, CompletableFuture<String>> hash) {
        this(store, hash, key -> false);
    }
    public DatabaseOperations(SqlStore store, Function<String, CompletableFuture<String>> hash, Predicate<CharacterKey> heldElsewhere) {
        this.store = store; this.hash = hash; this.heldElsewhere = heldElsewhere;
    }
    public CompletableFuture<Void> ready() { return store.ready(); }
    public void close() { store.close(); }

    public static String owner(CharacterRequest request) {
        return switch (request) {
            case CharacterRequest.Join r -> r.owner();
            case CharacterRequest.ListCharacters r -> r.owner();
            case CharacterRequest.Select r -> r.owner();
            case CharacterRequest.Borrow r -> r.owner();
            case CharacterRequest.SelectByName r -> r.owner();
            case CharacterRequest.Create r -> r.owner();
            case CharacterRequest.Update r -> r.owner();
            case CharacterRequest.DefaultSkin r -> r.owner();
            default -> "";
        };
    }
    public CompletableFuture<List<Effect>> run(CharacterRequest request, String playerName, long deadline) {
        return new DatabaseOperations(store.withDeadline(deadline), hash, heldElsewhere).run(request, playerName);
    }
    /** Reads {@code owner}'s character list. Reads only: an owner storage doesn't know is not created. */
    public CompletableFuture<Roster> roster(String owner, long deadline) {
        return new DatabaseOperations(store.withDeadline(deadline), hash, heldElsewhere).snapshot(owner)
                .thenApply(s -> new Roster(s.lastPlayed(), s.characters().stream().filter(CharacterView::enabled).toList()));
    }
    public CompletableFuture<List<Effect>> run(CharacterRequest request, String playerName) {
        String owner = owner(request);
        // A borrow only reads: it must never create the owner it reads from.
        CompletableFuture<?> initialized = owner.isEmpty() || request instanceof CharacterRequest.Borrow ? CompletableFuture.completedFuture(null)
                : store.ensureOwner(owner, playerName);
        return initialized.thenCompose(unused -> dispatch(request, playerName));
    }
    private CompletableFuture<List<Effect>> dispatch(CharacterRequest request, String name) {
        return switch (request) {
            case CharacterRequest.Join r -> snapshot(r.owner()).thenCompose(s -> load(r.owner(), r.owner(), s.lastPlayed(), true, s))
                    .thenCompose(events -> catalog().thenApply(titles -> append(events, titles)));
            case CharacterRequest.ListCharacters r -> snapshot(r.owner()).thenApply(s -> List.of(list(r.owner(), r.requester(), s)));
            case CharacterRequest.Select r -> snapshot(r.owner()).thenCompose(s -> load(r.owner(), r.owner(), r.character(), false, s));
            case CharacterRequest.SelectByName r -> snapshot(r.owner()).thenCompose(s -> {
                String id = s.characters().stream().filter(c -> c.enabled() && !c.id().equals(s.lastPlayed())
                    && c.plainName().toLowerCase(Locale.ROOT).startsWith(r.name().toLowerCase(Locale.ROOT)))
                    .map(CharacterView::id).findFirst().orElse("");
                if (id.isEmpty()) return done(new NotFound(r.owner(), false));
                return load(r.owner(), r.owner(), id, false, s);
            });
            // Owner's snapshot only: no select, so neither the body's nor the owner's last-played changes.
            // A disabled character is deleted as far as /char is concerned, so it can't be borrowed either.
            case CharacterRequest.Borrow r -> snapshot(r.owner()).thenApply(s -> {
                CharacterView c = s.characters().stream().filter(ch -> ch.id().equals(r.character()) && ch.enabled()).findFirst().orElse(null);
                return List.<Effect>of(c == null ? new NotFound(r.body(), true)
                        : new Loaded(r.body(), r.owner(), s.revision(), c, true, true, s.uniforms()));
            });
            case CharacterRequest.Create r -> {
                String id = UUID.randomUUID().toString();
                yield store.create(r.owner(), id, name, "", "", false)
                        .thenCompose(unused -> snapshot(r.owner()))
                        .thenCompose(s -> load(r.owner(), r.owner(), id, true, s));
            }
            case CharacterRequest.Update r -> update(r);
            case CharacterRequest.DefaultSkin r -> empty(store.initializeSkin(r.owner(), r.character(), r.url(), r.slim()));
            case CharacterRequest.Uniform r -> empty(store.uniform(r.base(), r.hash(), r.uuid(), r.url(), r.texture(), r.signature()));
            case CharacterRequest.RegisterSkin r -> empty(store.registerSkin(r.id(), SkinContentHash.normalizeMojangTextureUrl(r.url()),
                    r.texture(), r.signature(), SkinContentHash.normalizeMojangTextureUrl(r.source())));
            case CharacterRequest.ResolveSkin r -> resolve(r.source(), r.requestId()).thenApply(List::of);
            case CharacterRequest.Titles r -> catalog().thenApply(List::of);
            case CharacterRequest.CreateTitle r -> createTitle(r);
            case CharacterRequest.Search r -> store.search(r.filter()).thenApply(rows -> List.of(new Found(r.requester(),
                    rows.stream().map(row -> text(row, "owner_id") + "$" + text(row, "name")).toList())));
            case CharacterRequest.Quit r -> CompletableFuture.completedFuture(List.of());
        };
    }
    private CompletableFuture<Snapshot> snapshot(String owner) {
        return store.snapshot(owner).thenCompose(rows -> {
            List<CharacterView> characters = rows.stream().filter(row -> row.get("character_id") != null).map(CharacterView::from).toList();
            return store.uniforms(characters.stream().map(CharacterView::uniformBase).filter(url -> !url.isEmpty()).distinct().toList()).thenApply(uniforms -> new Snapshot(rows.isEmpty() ? "" : text(rows.getFirst(), "last_played_character"),
                    rows.isEmpty() || !(rows.getFirst().get("revision") instanceof Number n) ? 0 : n.longValue(), characters, uniforms));
        });
    }
    private Listed list(String owner, String requester, Snapshot s) {
        return new Listed(owner, requester, s.revision(), s.characters().stream().filter(CharacterView::enabled).toList(), s.uniforms());
    }
    private CompletableFuture<List<Effect>> load(String destination, String owner, String id, boolean forced, Snapshot s) {
        CharacterView c = s.characters().stream().filter(ch -> ch.id().equals(id)).findFirst().orElse(null);
        if (c == null) return done(new NotFound(destination, false));
        // A /char pick (not forced) of a character another body wears must not move last-played: the owner's next
        // join would otherwise take it back from the holder. A join (forced) is the owner taking it back on purpose.
        if (!forced && heldElsewhere(owner, id)) return done(new HeldElsewhere(owner, id));
        Loaded loaded = new Loaded(destination, owner, s.revision(), c, forced, false, s.uniforms());
        return store.select(owner, id).thenApply(unused -> List.of(loaded, list(owner, owner, s)));
    }
    private boolean heldElsewhere(String owner, String id) {
        try {
            return heldElsewhere.test(CharacterKey.of(owner, id));
        } catch (IllegalArgumentException e) {
            return false; // not a key anyone can wear; throwing here would fence the owner's storage
        }
    }
    private CompletableFuture<List<Effect>> update(CharacterRequest.Update r) {
        Object[] v = r.values();
        CompletableFuture<?> write = switch (r.type()) {
            case 1 -> legacySkin(r.owner(), r.character(), (String)v[0], (String)v[1], (String)v[2], (String)v[3], (Boolean)v[4]);
            case 2 -> store.update(r.owner(), r.character(), "name", v[0]);
            case 3 -> store.update(r.owner(), r.character(), "enabled", v[0]);
            case 4 -> store.update(r.owner(), r.character(), "tags", v[0]);
            case 5 -> store.update(r.owner(), r.character(), "gender", v[0]);
            case 6 -> {
                if (((String)v[0]).isEmpty()) throw new IllegalArgumentException("Empty skin id");
                yield store.skin(r.owner(), r.character(), (String)v[0], "", "", "", "", (Boolean)v[1]);
            }
            case 7 -> store.update(r.owner(), r.character(), "title", v[0]);
            default -> throw new IllegalArgumentException("Unknown character update");
        };
        if (r.type() == 1 || r.type() == 4 || r.type() == 6) return empty(write);
        return write.thenCompose(unused -> snapshot(r.owner())).thenApply(s -> {
            List<Effect> effects = new ArrayList<>();
            if (r.type() == 3 && Boolean.FALSE.equals(v[0])) {
                s.characters().stream().filter(c -> c.id().equals(r.character())).findFirst()
                        .ifPresent(c -> effects.add(new Deleted(r.owner(), c.id(), c.name())));
            }
            effects.add(list(r.owner(), r.owner(), s)); return List.copyOf(effects);
        });
    }
    private CompletableFuture<?> legacySkin(String owner, String character, String url, String uuid, String texture, String signature, boolean slim) {
        if (texture.isEmpty() || signature.isEmpty()) return store.skin(owner, character, "", url, uuid, texture, signature, slim);
        String extracted = SkinContentHash.mojangUrlFromTextureProperty(texture);
        String source = SkinContentHash.normalizeMojangTextureUrl(extracted == null || extracted.isEmpty() ? url : extracted);
        return resolve(source, "").thenCompose(resolved -> {
            if (resolved.id().isEmpty()) return store.skin(owner, character, "", url, uuid, texture, signature, slim);
            return store.registerSkin(resolved.id(), source, texture, signature, source)
                    .thenCompose(unused -> store.skin(owner, character, resolved.id(), "", "", "", "", slim));
        });
    }
    private CompletableFuture<Catalog> catalog() {
        return store.titles().thenApply(rows -> {
            Map<String,String> titles = new LinkedHashMap<>();
            for (Map<String,Object> row : rows) titles.put(text(row, "code"), text(row, "display"));
            return new Catalog(Collections.unmodifiableMap(titles));
        });
    }
    private CompletableFuture<List<Effect>> createTitle(CharacterRequest.CreateTitle r) {
        String code = TitleKeys.normalize(r.code()), display = r.display().trim();
        if (code.isEmpty() || display.isEmpty()) return done(new TitleCreated(r.admin(), r.target(), false, code, display, "invalid"));
        return store.createTitle(code, display).thenCompose(rows -> {
            if (rows.size() != 1) throw new IllegalStateException("Title winner missing");
            TitleCreated result = new TitleCreated(r.admin(), r.target(), true, text(rows.getFirst(), "code"), text(rows.getFirst(), "display"), "");
            return catalog().thenApply(catalog -> List.of(catalog, result));
        });
    }
    private CompletableFuture<Resolved> resolve(String source, String requestId) {
        String normalized = SkinContentHash.normalizeMojangTextureUrl(source.trim());
        if (normalized.isEmpty()) return CompletableFuture.completedFuture(new Resolved(requestId, "ERROR", "", "", "", "", "Empty source URL"));
        return store.findSkin(normalized).thenCompose(rows -> {
            if (!rows.isEmpty()) return CompletableFuture.completedFuture(hit(requestId, rows.getFirst()));
            return hash.apply(normalized).handle((id, error) -> {
                        if (error == null) return id;
                        SkinFetchException failure = fetchFailure(error);
                        if (failure != null && failure.isRejection()) throw new CompletionException(failure);
                        return "";
                    })
                    .thenCompose(id -> {
                        if (id == null || id.isEmpty()) return CompletableFuture.completedFuture(new Resolved(requestId, "ERROR", "", "", "", "", "Skin download/hash failed"));
                        return store.findSkin(id).thenApply(found -> found.isEmpty()
                            ? new Resolved(requestId, "MISS", id, "", "", "", "") : hit(requestId, found.getFirst()));
                    })
                    .exceptionallyCompose(error -> {
                        SkinFetchException failure = fetchFailure(error);
                        if (failure == null || !failure.isRejection()) return CompletableFuture.failedFuture(error);
                        return CompletableFuture.completedFuture(new Resolved(requestId, "REJECTED", "", "", "", "", failure.reason().name()));
                    });
        });
    }
    private static SkinFetchException fetchFailure(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) if (t instanceof SkinFetchException f) return f;
        return null;
    }
    private Resolved hit(String requestId, Map<String,Object> row) {
        return new Resolved(requestId, "HIT", text(row, "skin_id"), text(row, "mojang_url"), text(row, "texture"), text(row, "signature"), "");
    }
    private static CompletableFuture<List<Effect>> done(Effect effect) { return CompletableFuture.completedFuture(List.of(effect)); }
    private static CompletableFuture<List<Effect>> empty(CompletableFuture<?> work) { return work.thenApply(unused -> List.of()); }
    private static List<Effect> append(List<Effect> events, Effect extra) {
        List<Effect> result = new ArrayList<>(events); result.add(extra); return List.copyOf(result);
    }
}
