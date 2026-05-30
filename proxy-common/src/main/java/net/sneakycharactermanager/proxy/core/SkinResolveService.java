package net.sneakycharactermanager.proxy.core;

import net.sneakycharactermanager.proxy.common.ProxyLogger;
import net.sneakycharactermanager.proxy.common.SkinContentHash;
import net.sneakycharactermanager.proxy.common.SkinImageFetcher;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class SkinResolveService {

    public enum Status {
        HIT,
        MISS,
        ERROR
    }

    public static final class Result {
        public final Status status;
        public final String skinId;
        public final String mojangTextureUrl;
        public final String texture;
        public final String signature;
        public final String errorMessage;

        private Result(Status status, String skinId, String mojangTextureUrl, String texture, String signature, String errorMessage) {
            this.status = status;
            this.skinId = skinId == null ? "" : skinId;
            this.mojangTextureUrl = mojangTextureUrl == null ? "" : mojangTextureUrl;
            this.texture = texture == null ? "" : texture;
            this.signature = signature == null ? "" : signature;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
        }

        public static Result hit(GlobalSkinCache.Entry entry) {
            return new Result(Status.HIT, entry.skinId, entry.mojangTextureUrl, entry.texture, entry.signature, "");
        }

        public static Result miss(String skinId) {
            return new Result(Status.MISS, skinId == null ? "" : skinId, "", "", "", "");
        }

        public static Result error(String message) {
            return new Result(Status.ERROR, "", "", "", "", message);
        }
    }

    private final GlobalSkinCache cache;
    private final SkinImageFetcher fetcher;
    private final ProxyLogger logger;
    private final Executor executor;
    private final Map<String, CompletableFuture<Result>> inFlight = new ConcurrentHashMap<>();

    public SkinResolveService(GlobalSkinCache cache, int timeoutSeconds, int maxBytes, ProxyLogger logger) {
        this.cache = cache;
        this.fetcher = new SkinImageFetcher(timeoutSeconds, maxBytes);
        this.logger = logger;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "SCM-SkinResolve");
            t.setDaemon(true);
            return t;
        });
    }

    public CompletableFuture<Result> resolve(String sourceUrl) {
        String key = sourceUrl == null ? "" : sourceUrl.trim();
        if (key.isEmpty()) {
            return CompletableFuture.completedFuture(Result.error("Empty source URL"));
        }
        if (SkinContentHash.isMojangTextureUrl(key)) {
            key = SkinContentHash.normalizeMojangTextureUrl(key);
        }
        final String resolveKey = key;

        CompletableFuture<Result> existing = inFlight.get(resolveKey);
        if (existing != null) {
            return existing;
        }

        CompletableFuture<Result> future = doResolve(resolveKey);
        CompletableFuture<Result> racing = inFlight.putIfAbsent(resolveKey, future);
        if (racing != null) {
            return racing;
        }

        // Do not remove from inFlight inside computeIfAbsent / on the same stack as a
        // just-completed future — that triggers ConcurrentHashMap "Recursive update".
        future.whenComplete((res, err) -> inFlight.remove(resolveKey, future));
        return future;
    }

    private CompletableFuture<Result> doResolve(String sourceUrl) {
        if (SkinContentHash.isMojangTextureUrl(sourceUrl)) {
            Optional<GlobalSkinCache.Entry> hit = cache.getByMojangUrl(sourceUrl);
            if (hit.isPresent()) {
                return CompletableFuture.completedFuture(Result.hit(hit.get()));
            }
            return CompletableFuture.supplyAsync(() -> resolveByDownload(sourceUrl), executor);
        }

        return CompletableFuture.supplyAsync(() -> resolveByDownload(sourceUrl), executor);
    }

    private Result resolveByDownload(String sourceUrl) {
        try {
            if (SkinContentHash.isMojangTextureUrl(sourceUrl)) {
                Optional<GlobalSkinCache.Entry> hit = cache.getByMojangUrl(sourceUrl);
                if (hit.isPresent()) return Result.hit(hit.get());
            }

            BufferedImage image = fetcher.downloadImage(sourceUrl);
            String skinId = SkinContentHash.skinIdFromImage(image);
            if (skinId == null) return Result.error("Failed to hash image");

            Optional<GlobalSkinCache.Entry> byId = cache.getById(skinId);
            if (byId.isPresent()) return Result.hit(byId.get());

            return Result.miss(skinId);
        } catch (Exception e) {
            logger.warning("Skin resolve failed for " + sourceUrl + ": " + e.getMessage());
            return Result.error(e.getMessage());
        }
    }
}
