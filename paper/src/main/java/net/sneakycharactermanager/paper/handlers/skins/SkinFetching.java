package net.sneakycharactermanager.paper.handlers.skins;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.proxy.common.SkinContentHash;
import net.sneakycharactermanager.proxy.common.SkinFetchException;
import net.sneakycharactermanager.proxy.common.SkinUrlPolicy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Paper's single entry point for downloading player-supplied skin URLs. Policy is read from {@code skin.fetch.*} on
 * each call, so {@code /charreload} applies changes.
 */
public final class SkinFetching {

    private static final SafeSkinFetcher FETCHER = new SafeSkinFetcher();
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), r -> {
                Thread thread = new Thread(r, "SCM-skin-fetch");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    private SkinFetching() {}

    public static SafeSkinFetcher fetcher() {
        return FETCHER;
    }

    public static SkinUrlPolicy policy() {
        return policyFrom(SneakyCharacterManager.getInstance().getConfig());
    }

    static SkinUrlPolicy policyFrom(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("skin.fetch");
        if (section == null) return SkinUrlPolicy.defaults();
        List<String> hosts = section.getStringList("allowedHosts");
        Set<Integer> ports = new HashSet<>(section.getIntegerList("allowedPorts"));
        return new SkinUrlPolicy(new HashSet<>(hosts), ports,
                section.getInt("maxBytes", SkinUrlPolicy.DEFAULT_MAX_BYTES),
                section.getInt("maxRedirects", SkinUrlPolicy.DEFAULT_MAX_REDIRECTS),
                section.getInt("timeoutSeconds", SkinUrlPolicy.DEFAULT_TIMEOUT_MILLIS / 1000) * 1000);
    }

    /** Downloads, validates and content-hashes a skin URL off the server thread. */
    public static CompletableFuture<String> skinIdAsync(String url) {
        SkinUrlPolicy policy = policy();
        try {
            return CompletableFuture.supplyAsync(() -> skinId(url, policy), EXECUTOR);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.failedFuture(new SkinFetchException(SkinFetchException.Reason.NETWORK, "skin fetch queue full"));
        }
    }

    /** Blocking variant for callers that already run on their own bounded executor. */
    public static String skinId(String url, SkinUrlPolicy policy) {
        try {
            String id = SkinContentHash.skinIdFromImage(FETCHER.fetchImage(url, policy));
            if (id == null) throw new SkinFetchException(SkinFetchException.Reason.NOT_A_SKIN_PNG, "could not hash " + url);
            return id;
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    /** @return the {@link SkinFetchException} behind a (possibly wrapped) failure, or null. */
    public static SkinFetchException fetchFailure(Throwable error) {
        Throwable t = error;
        while (t != null) {
            if (t instanceof SkinFetchException f) return f;
            t = t.getCause();
        }
        return null;
    }

    /** One message for every rejected skin; details stay in the server log. */
    public static final String REJECTED_MESSAGE =
            "&4Couldn't use that skin. Use a direct link to a public 64x64 or 64x32 PNG.";
}
