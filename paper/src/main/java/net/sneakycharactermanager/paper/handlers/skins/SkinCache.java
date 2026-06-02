package net.sneakycharactermanager.paper.handlers.skins;

import com.destroystokyo.paper.profile.ProfileProperty;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.util.ProxyMessagingUtil;
import net.sneakycharactermanager.paper.util.SkinUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Proxy-backed global skin cache client (replaces the old in-memory URL map).
 */
public final class SkinCache {

    public enum ResolveStatus {
        HIT,
        MISS,
        ERROR
    }

    public static final class ResolveResult {
        public final ResolveStatus status;
        public final String skinId;
        public final String mojangTextureUrl;
        public final String texture;
        public final String signature;
        public final String errorMessage;

        public ResolveResult(ResolveStatus status, String skinId, String mojangTextureUrl,
                             String texture, String signature, String errorMessage) {
            this.status = status;
            this.skinId = skinId == null ? "" : skinId;
            this.mojangTextureUrl = mojangTextureUrl == null ? "" : mojangTextureUrl;
            this.texture = texture == null ? "" : texture;
            this.signature = signature == null ? "" : signature;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
        }

        @Nullable
        public ProfileProperty toProfileProperty() {
            if (status != ResolveStatus.HIT || texture.isEmpty()) return null;
            return new ProfileProperty("textures", texture, signature);
        }
    }

    private static final Map<String, CompletableFuture<ResolveResult>> pending = new ConcurrentHashMap<>();
    private static final Map<String, ProfileProperty> sessionHits = new ConcurrentHashMap<>();

    private SkinCache() {}

    public static CompletableFuture<ResolveResult> resolve(@Nullable Player requester, String sourceUrl, boolean slim) {
        if (sourceUrl == null || sourceUrl.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new ResolveResult(ResolveStatus.ERROR, "", "", "", "", "Empty URL"));
        }

        if (SkinUtil.isMojangTextureUrl(sourceUrl)) {
            sourceUrl = SkinUtil.normalizeMojangTextureUrl(sourceUrl);
        }

        ProfileProperty session = sessionHits.get(sessionKey(sourceUrl));
        if (session != null) {
            String mojang = net.sneakycharactermanager.paper.util.SkinUtil.getTextureUrl(session);
            return CompletableFuture.completedFuture(new ResolveResult(
                    ResolveStatus.HIT, "", mojang == null ? "" : mojang,
                    session.getValue(), session.getSignature(), ""));
        }

        String requestId = UUID.randomUUID().toString();
        CompletableFuture<ResolveResult> future = new CompletableFuture<>();
        pending.put(requestId, future);

        future.completeOnTimeout(
                new ResolveResult(ResolveStatus.ERROR, "", "", "", "", "Resolve timed out"),
                resolveTimeoutSeconds(), TimeUnit.SECONDS)
                .whenComplete((res, ex) -> pending.remove(requestId));

        ProxyMessagingUtil.sendByteArray(requester, "resolveSkin",
                requester == null ? "" : requester.getUniqueId().toString(),
                requestId, sourceUrl, slim);
        return future;
    }

    public static void completeResolve(String requestId, ResolveResult result) {
        CompletableFuture<ResolveResult> future = pending.remove(requestId);
        if (future == null) {
            SneakyCharacterManager.getInstance().getLogger().fine(
                    "resolveSkinResult for unknown or timed-out request: " + requestId);
            return;
        }
        if (result.status == ResolveStatus.HIT && result.toProfileProperty() != null) {
            cacheSession(result.mojangTextureUrl, result.toProfileProperty());
            if (!result.skinId.isEmpty()) {
                cacheSession(result.skinId, result.toProfileProperty());
            }
        }
        future.complete(result);
    }

    /** Runs {@link #completeResolve} on the server main thread (plugin messages may arrive off-thread). */
    public static void completeResolveOnMainThread(String requestId, ResolveResult result) {
        if (Bukkit.isPrimaryThread()) {
            completeResolve(requestId, result);
            return;
        }
        Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(),
                () -> completeResolve(requestId, result));
    }

    public static void register(@Nullable Player requester, String skinId, String mojangTextureUrl,
                                String texture, String signature, @Nullable String sourceUrl) {
        GlobalSkinCacheBridge.register(requester, skinId, mojangTextureUrl, texture, signature, sourceUrl);
        ProfileProperty property = new ProfileProperty("textures", texture, signature);
        if (mojangTextureUrl != null && !mojangTextureUrl.isEmpty()) {
            cacheSession(mojangTextureUrl, property);
        }
        if (sourceUrl != null && !sourceUrl.isEmpty()) {
            cacheSession(sourceUrl, property);
        }
        if (skinId != null && !skinId.isEmpty()) {
            cacheSession(skinId, property);
        }
    }

    public static void updateCharacterSkin(Player player, String characterUUID, String skinId, boolean slim) {
        ProxyMessagingUtil.sendByteArray(player, "updateCharacter",
                player.getUniqueId().toString(), characterUUID, 6, skinId, slim);
    }

    /** @deprecated Use {@link #resolve(Player, String, boolean)} */
    @Nullable
    @Deprecated
    public static ProfileProperty get(String playerUUID, String url) {
        ProfileProperty session = sessionHits.get(sessionKey(url));
        return session != null && session.isSigned() ? session : null;
    }

    /** @deprecated Use {@link #register(Player, String, String, String, String, String)} */
    @Deprecated
    public static void put(String playerUUID, String url, ProfileProperty profileProperty) {
        if (profileProperty != null && profileProperty.isSigned()) {
            cacheSession(url, profileProperty);
        }
    }

    public static void remove(String playerUUID) {
        // No per-player state on proxy client; kept for API compatibility.
    }

    private static void cacheSession(String key, ProfileProperty property) {
        if (key == null || key.isEmpty() || property == null) return;
        sessionHits.put(sessionKey(key), property);
    }

    private static String sessionKey(String url) {
        return url.trim();
    }

    private static int resolveTimeoutSeconds() {
        return SneakyCharacterManager.getInstance().getConfig().getInt("skin.resolveTimeoutSeconds", 5);
    }

    /** Sends registerSkin to proxy without exposing messaging details to callers. */
    static final class GlobalSkinCacheBridge {
        private GlobalSkinCacheBridge() {}

        static void register(@Nullable Player requester, String skinId, String mojangTextureUrl,
                             String texture, String signature, @Nullable String sourceUrl) {
            ProxyMessagingUtil.sendByteArray(requester, "registerSkin",
                    skinId,
                    mojangTextureUrl == null ? "" : mojangTextureUrl,
                    texture,
                    signature,
                    sourceUrl == null ? "" : sourceUrl);
        }
    }
}
