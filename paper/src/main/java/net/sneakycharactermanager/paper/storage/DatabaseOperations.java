package net.sneakycharactermanager.paper.storage;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import net.sneakycharactermanager.common.titles.TitleKeys;
import net.sneakycharactermanager.proxy.common.SkinContentHash;
import static net.sneakycharactermanager.paper.storage.SqlStore.text;

/** Headless operation layer: SQL/HTTP in, immutable domain effects out; no Bukkit or wire packets. */
public final class DatabaseOperations {
    public sealed interface Effect {}
    public record Listed(String owner, String requester, long revision, List<CharacterView> characters,
                         List<Map<String,Object>> uniforms) implements Effect {}
    public record Loaded(String destination, String source, long revision, CharacterView character, boolean forced,
                         boolean temporary, List<Map<String,Object>> uniforms) implements Effect {}
    public record NotFound(String player, boolean temporary) implements Effect {}
    public record Deleted(String player, String id, String name) implements Effect {}
    public record Catalog(Map<String,String> titles) implements Effect {}
    public record TitleCreated(String admin, String target, boolean success, String code, String display, String error) implements Effect {}
    public record Resolved(String requestId, String status, String id, String url, String texture, String signature, String error) implements Effect {}
    public record Found(String requester, List<String> entries) implements Effect {}
    private record Snapshot(String lastPlayed, long revision, List<CharacterView> characters, List<Map<String,Object>> uniforms) {}
    private final SqlStore store;
    private final Function<String, CompletableFuture<String>> hash;
    public DatabaseOperations(SqlStore store, Function<String, CompletableFuture<String>> hash) {
        this.store = store; this.hash = hash;
    }
    public CompletableFuture<Void> ready() { return store.ready(); }
    public void close() { store.close(); }

    public static String owner(CharacterRequest request) {
        return switch (request) {
            case CharacterRequest.Join r -> r.owner();
            case CharacterRequest.ListCharacters r -> r.owner();
            case CharacterRequest.Select r -> r.owner();
            case CharacterRequest.SelectByName r -> r.owner();
            case CharacterRequest.Temporary r -> r.owner();
            case CharacterRequest.Create r -> r.owner();
            case CharacterRequest.Update r -> r.owner();
            case CharacterRequest.DefaultSkin r -> r.owner();
            case CharacterRequest.Template r -> "template";
            default -> "";
        };
    }
    public CompletableFuture<List<Effect>> run(CharacterRequest request, String playerName, long deadline) {
        return new DatabaseOperations(store.withDeadline(deadline), hash).run(request, playerName);
    }
    public CompletableFuture<List<Effect>> run(CharacterRequest request, String playerName) {
        String owner = owner(request);
        CompletableFuture<?> initialized = owner.isEmpty() ? CompletableFuture.completedFuture(null)
                : store.ensureOwner(owner, owner.equals("template") ? "template" : playerName);
        return initialized.thenCompose(unused -> dispatch(request, playerName));
    }
    private CompletableFuture<List<Effect>> dispatch(CharacterRequest request, String name) {
        return switch (request) {
            case CharacterRequest.Join r -> snapshot(r.owner()).thenCompose(s -> load(r.owner(), r.owner(), s.lastPlayed(), true, false, s))
                    .thenCompose(events -> catalog().thenApply(titles -> append(events, titles)));
            case CharacterRequest.ListCharacters r -> snapshot(r.owner()).thenApply(s -> List.of(list(r.owner(), r.requester(), s)));
            case CharacterRequest.Select r -> snapshot(r.owner()).thenCompose(s -> load(r.owner(), r.owner(), r.character(), false, false, s));
            case CharacterRequest.SelectByName r -> snapshot(r.owner()).thenCompose(s -> {
                String id = s.characters().stream().filter(c -> c.enabled() && !c.id().equals(s.lastPlayed())
                    && c.plainName().toLowerCase(Locale.ROOT).startsWith(r.name().toLowerCase(Locale.ROOT)))
                    .map(CharacterView::id).findFirst().orElse("");
                if (id.isEmpty()) return done(new NotFound(r.owner(), false));
                return load(r.owner(), r.owner(), id, false, false, s);
            });
            case CharacterRequest.Temporary r -> snapshot(r.owner()).thenCompose(s -> load(r.requester(), r.owner(), r.character(), true, true, s));
            case CharacterRequest.Create r -> {
                String id = UUID.randomUUID().toString();
                yield store.create(r.owner(), id, name, "", "", false)
                        .thenCompose(unused -> snapshot(r.owner()))
                        .thenCompose(s -> load(r.owner(), r.owner(), id, true, false, s));
            }
            case CharacterRequest.Template r -> empty(store.create("template", r.id(), r.name(), r.url(), r.skinUuid(), r.slim()));
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
    private CompletableFuture<List<Effect>> load(String destination, String owner, String id, boolean forced, boolean temporary, Snapshot s) {
        CharacterView c = s.characters().stream().filter(ch -> ch.id().equals(id)).findFirst().orElse(null);
        if (c == null) return done(new NotFound(destination, temporary));
        Loaded loaded = new Loaded(destination, owner, s.revision(), c, forced, temporary, s.uniforms());
        if (temporary) return done(loaded);
        return store.select(owner, id).thenApply(unused -> List.of(loaded, list(owner, owner, s)));
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
            return hash.apply(normalized).handle((id, error) -> error == null ? id : "")
                    .thenCompose(id -> {
                        if (id == null || id.isEmpty()) return CompletableFuture.completedFuture(new Resolved(requestId, "ERROR", "", "", "", "", "Skin download/hash failed"));
                        return store.findSkin(id).thenApply(found -> found.isEmpty()
                            ? new Resolved(requestId, "MISS", id, "", "", "", "") : hit(requestId, found.getFirst()));
                    });
        });
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
