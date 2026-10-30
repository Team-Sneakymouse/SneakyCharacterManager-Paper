package net.sneakycharactermanager.paper.storage;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.admincommands.CommandTitleGrant;
import net.sneakycharactermanager.paper.consolecommands.ConsoleCommandCharTemp;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.skins.SkinCache;
import net.sneakycharactermanager.paper.listeners.ProxyMessageListener;
import net.sneakycharactermanager.proxy.common.SkinContentHash;
import net.sneakycharactermanager.proxy.common.SkinImageFetcher;
import com.sneakyutils.util.TextUtility;
import java.util.*;
import java.util.concurrent.*;

/** Enable-generation owner of database operations, bounded HTTP workers and main-thread effects. */
public final class DatabaseBackend implements CharacterBackend {
    private final SneakyCharacterManager plugin;
    private final DatabaseOperations operations;
    private final OwnerQueue queue = new OwnerQueue(256);
    private final ResultFence fence = new ResultFence();
    private final RevisionFence revisions = new RevisionFence();
    private final Set<Player> joining = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Player> failedJoins = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Player, CharacterRequest.Temporary> temporary = new IdentityHashMap<>();
    private final Set<Player> failedTemporary = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ThreadPoolExecutor http;
    private CompletableFuture<?> skinBarrier = CompletableFuture.completedFuture(null);
    private long titleCompletion;
    private volatile boolean closed;
    private final int timeoutSeconds;

    public DatabaseBackend(SneakyCharacterManager plugin, SqlStore store) {
        this.plugin = plugin;
        this.timeoutSeconds = store.timeoutSeconds();
        http = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), r -> {
            Thread thread = new Thread(r, "SCM-skin-resolve"); thread.setDaemon(true); return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
        SkinImageFetcher fetcher = new SkinImageFetcher(5, 1024 * 1024);
        operations = new DatabaseOperations(store, url -> CompletableFuture.supplyAsync(() -> {
            try { return SkinContentHash.skinIdFromImage(fetcher.downloadImage(url)); }
            catch (Exception error) { throw new CompletionException(error); }
        }, http));
        operations.ready().whenComplete((unused, error) -> {
            if (error != null && !closed) plugin.getLogger().severe(
                    "SneakyDB SCM initialization failed. Apply db/001_scm.sql, check SneakyDB, then restart; no legacy fallback.");
        });
    }
    @Override public void submit(Player requester, CharacterRequest request) {
        if (closed) return;
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (requester == null || Bukkit.getPlayer(requester.getUniqueId()) == requester) submit(requester, request);
            });
            return;
        }
        if (request instanceof CharacterRequest.Quit quit) {
            Player player = Bukkit.getPlayer(UUID.fromString(quit.owner()));
            if (player != null) disconnect(player);
            return;
        }
        String owner = DatabaseOperations.owner(request);
        Player target = owner.isEmpty() || owner.equals("template") ? null : Bukkit.getPlayer(UUID.fromString(owner));
        String name = target != null ? target.getName() : "";
        String destination = switch (request) {
            case CharacterRequest.Temporary r -> r.requester();
            default -> owner;
        };
        Player destinationPlayer = destination.isEmpty() || destination.equals("template") ? null : Bukkit.getPlayer(UUID.fromString(destination));
        if (request instanceof CharacterRequest.Join && destinationPlayer != null) {
            if (failedJoins.contains(destinationPlayer) || !joining.add(destinationPlayer)) return;
        }
        if (request instanceof CharacterRequest.Temporary r && destinationPlayer != null) {
            if (failedTemporary.contains(destinationPlayer) || r.equals(temporary.get(destinationPlayer))) return;
            temporary.put(destinationPlayer, r);
        }
        boolean selection = request instanceof CharacterRequest.Join || request instanceof CharacterRequest.Select
                || request instanceof CharacterRequest.SelectByName || request instanceof CharacterRequest.Create
                || request instanceof CharacterRequest.Temporary;
        boolean menuRequest = request instanceof CharacterRequest.ListCharacters r && r.requester().endsWith("|openGUI");
        Player effectPlayer = selection ? destinationPlayer : requester;
        String lane = selection ? "selection" : menuRequest ? "menu" : request instanceof CharacterRequest.ListCharacters ? "list:" + owner
                : request instanceof CharacterRequest.ResolveSkin r ? "skin:" + r.requestId() : request.getClass().getSimpleName() + ":" + owner;
        // Grants have non-replaceable permission/equip effects, unlike latest-wins UI requests.
        // Each completion releases its own ticket; disconnect/disable still invalidate all tickets.
        if (request instanceof CharacterRequest.CreateTitle) lane = "grant:" + ++titleCompletion;
        ResultFence.Ticket ticket = effectPlayer == null ? null : fence.capture(effectPlayer.getUniqueId().toString(), lane, effectPlayer);
        if (selection && requester != null) {
            // A delayed menu refresh must not repopulate a menu after its selection request.
            fence.capture(requester.getUniqueId().toString(), "list:" + owner, requester);
            fence.capture(requester.getUniqueId().toString(), "menu", requester);
        }
        CompletableFuture<?> barrier = skinBarrier;
        // An uncertain registration fences only that skin, not every ownerless operation.
        String queueKey = request instanceof CharacterRequest.RegisterSkin r ? "$skin:" + r.id()
                : owner.isEmpty() ? "$global" : owner;
        revisions.begin(owner);
        if (request instanceof CharacterRequest.Update || request instanceof CharacterRequest.DefaultSkin
                || request instanceof CharacterRequest.Template) revisions.changed(owner);
        long changes = revisions.changes(owner);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        CompletableFuture<List<DatabaseOperations.Effect>> future = queue.submit(queueKey, () -> {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return CompletableFuture.failedFuture(new TimeoutException("SCM request expired in queue"));
            return operations.ready().thenCompose(unused -> barrier).thenCompose(unused -> operations.run(request, name, deadline))
                    .orTimeout(remaining, TimeUnit.NANOSECONDS);
        });
        // This is an ordering barrier, not authority to fail unrelated lanes. Keep the
        // original future exceptional for its requester and OwnerQueue's no-retry fence.
        if (request instanceof CharacterRequest.RegisterSkin)
            skinBarrier = CompletableFuture.allOf(barrier, future.handle((unused, error) -> null));
        future.whenComplete((effects, error) -> {
            if (closed) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                if (closed) return;
                if (request instanceof CharacterRequest.Join && destinationPlayer != null) {
                    joining.remove(destinationPlayer);
                    if (error != null && Bukkit.getPlayer(destinationPlayer.getUniqueId()) == destinationPlayer)
                        failedJoins.add(destinationPlayer);
                }
                if (request instanceof CharacterRequest.Temporary && destinationPlayer != null) {
                    // An old completion must not release a newer selection's pending identity.
                    boolean pendingSession = temporary.containsKey(destinationPlayer);
                    if (temporary.get(destinationPlayer) == request) temporary.remove(destinationPlayer);
                    if (error != null && pendingSession && Bukkit.getPlayer(destinationPlayer.getUniqueId()) == destinationPlayer)
                        failedTemporary.add(destinationPlayer);
                }
                if (ticket != null && !fence.current(ticket, Bukkit.getPlayer(effectPlayer.getUniqueId()))) return;
                if (requester != null && Bukkit.getPlayer(requester.getUniqueId()) != requester) return;
                if (!owner.isEmpty() && !owner.equals("template") && Bukkit.getPlayer(UUID.fromString(owner)) != target) return;
                if (ticket != null) fence.release(ticket);
                if (menuRequest && (requester == null || !plugin.selectionMenu.menuExists(requester.getUniqueId().toString()))) return;
                if (error != null) {
                    plugin.getLogger().warning("SCM database operation failed (" + request.getClass().getSimpleName()
                            + "); owner fenced after uncertain outcome. Check database and restart. No automatic retry or YAML fallback.");
                    if (requester != null) requester.sendMessage(TextUtility.INSTANCE.convertToComponent(
                            "&cCharacter storage unavailable. No changes are being retried; contact an administrator."));
                    if (request instanceof CharacterRequest.ResolveSkin r) SkinCache.completeResolveOnMainThread(r.requestId(),
                            new SkinCache.ResolveResult(SkinCache.ResolveStatus.ERROR, "", "", "", "", "Database unavailable"));
                    if (request instanceof CharacterRequest.CreateTitle r) CommandTitleGrant.handleCreateResult(r.admin(), r.target(), false, "", "", "failed");
                    return;
                }
                for (DatabaseOperations.Effect effect : effects) {
                    // A newer local mutation can have no Listed effect (notably skins).
                    if (effect instanceof DatabaseOperations.Loaded loaded
                            && (!revisions.unchanged(owner, changes) || !revisions.accept(loaded.source(), loaded.revision()))) continue;
                    if (effect instanceof DatabaseOperations.Listed listed && !revisions.accept(listed.owner(), listed.revision())) continue;
                    apply(effect);
                }
                } finally { revisions.finish(owner); }
            });
        });
    }
    private void apply(DatabaseOperations.Effect effect) {
        switch (effect) {
            case DatabaseOperations.Listed r -> CharacterResultEffects.handleListCompletion("syncCharacters", r.owner(), r.requester(),
                    r.characters().stream().map(c -> character(r.owner(), c, r.uniforms())).toList());
            case DatabaseOperations.Loaded r -> {
                Character character = character(r.destination(), r.character(), r.uniforms());
                if (r.temporary()) {
                    character.load();
                    ConsoleCommandCharTemp.playerTempCharAdd(r.destination(), r.source(), r.character().id());
                } else {
                    CharacterResultEffects.load(character, r.forced());
                    if (r.character().skin().isEmpty() && r.character().skinId().isEmpty())
                        CharacterResultEffects.defaultSkin(r.destination(), r.character().id());
                }
            }
            case DatabaseOperations.Catalog r -> {
                // SCM title creation is append-only and canonical displays never change.
                // Concurrent owner/global workers may deliver catalogs out of order; an older
                // snapshot must not remove a title already observed this enable generation.
                Map<String,String> titles = new LinkedHashMap<>(plugin.titleRegistry.all());
                r.titles().forEach(titles::putIfAbsent);
                CharacterResultEffects.titles(titles);
            }
            case DatabaseOperations.TitleCreated r -> CommandTitleGrant.handleCreateResult(r.admin(), r.target(), r.success(), r.code(), r.display(), r.error());
            case DatabaseOperations.Resolved r -> SkinCache.completeResolveOnMainThread(r.requestId(), new SkinCache.ResolveResult(
                    SkinCache.ResolveStatus.valueOf(r.status()), r.id(), r.url(), r.texture(), r.signature(), r.error()));
            case DatabaseOperations.Found r -> ProxyMessageListener.handleCharacterOutput(r.requester(), r.entries());
            case DatabaseOperations.Deleted r -> {
                Player player = Bukkit.getPlayer(UUID.fromString(r.player()));
                if (player != null) player.sendMessage(TextUtility.INSTANCE.convertToComponent(
                        "&aThe following character has been deleted: &b`" + r.name() + "`&a (" + r.id() + ")"));
            }
            case DatabaseOperations.NotFound r -> {
                if (r.temporary()) plugin.getLogger().warning("Requested temporary character does not exist");
                else {
                    Player player = Bukkit.getPlayer(UUID.fromString(r.player()));
                    if (player != null) {
                        player.sendMessage(TextUtility.INSTANCE.convertToComponent("&aNo character found. Loading character menu..."));
                        plugin.selectionMenu.openMenu(player);
                    }
                }
            }
        }
    }
    private Character character(String destination, CharacterView c, List<Map<String,Object>> uniforms) {
        Character character = new Character(destination, c.id(), c.name(), c.skin(), c.skinUuid(), c.texture(), c.signature(),
                c.slim(), c.tags(), c.gender(), c.title());
        for (Map<String,Object> row : uniforms) {
            if (SqlStore.text(row, "base_url").equals(c.uniformBase())) character.addUniformVariant(SqlStore.text(row, "uniform_hash"),
                    SqlStore.text(row, "skin_uuid"), SqlStore.text(row, "texture_url"), SqlStore.text(row, "texture"), SqlStore.text(row, "signature"));
        }
        return character;
    }
    @Override public void disconnect(Player player) {
        fence.forget(player.getUniqueId().toString()); joining.remove(player); failedJoins.remove(player);
        temporary.remove(player); failedTemporary.remove(player);
    }
    @Override public void close() {
        closed = true; fence.close(); revisions.clear(); queue.close(); operations.close(); http.shutdownNow(); joining.clear(); failedJoins.clear(); temporary.clear(); failedTemporary.clear();
    }
}
