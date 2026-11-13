package net.sneakycharactermanager.proxy.common;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rules a skin URL must meet before anything is fetched. Checks only the URL itself (no DNS), so it is cheap
 * enough to run on the server thread; address checks happen at connect time.
 *
 * @param allowedHosts lower-case host names; empty means any host. An entry starting with {@code .} also
 *                     matches subdomains (for example {@code .discordapp.net}). {@code textures.minecraft.net}
 *                     is always allowed.
 * @param allowedPorts explicit ports allowed; a URL without a port uses the scheme default (80/443)
 */
public record SkinUrlPolicy(Set<String> allowedHosts, Set<Integer> allowedPorts,
                            int maxBytes, int maxRedirects, int timeoutMillis) {

    public static final int DEFAULT_MAX_BYTES = 1024 * 1024;
    public static final int DEFAULT_MAX_REDIRECTS = 3;
    public static final int DEFAULT_TIMEOUT_MILLIS = 5000;
    public static final String MOJANG_TEXTURE_HOST = "textures.minecraft.net";

    public SkinUrlPolicy {
        allowedHosts = allowedHosts == null ? Set.of() : allowedHosts.stream()
                .filter(h -> h != null && !h.isBlank())
                .map(h -> h.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        allowedPorts = allowedPorts == null || allowedPorts.isEmpty() ? Set.of(80, 443) : Set.copyOf(allowedPorts);
        maxBytes = Math.max(PngSkinHeader.HEADER_BYTES, maxBytes);
        maxRedirects = Math.max(0, maxRedirects);
        timeoutMillis = Math.max(500, timeoutMillis);
    }

    public static SkinUrlPolicy defaults() {
        return new SkinUrlPolicy(Set.of(), Set.of(80, 443), DEFAULT_MAX_BYTES, DEFAULT_MAX_REDIRECTS, DEFAULT_TIMEOUT_MILLIS);
    }

    /** Mojang texture server only: what the proxy may still download (migration, Mojang URL hashing). */
    public static SkinUrlPolicy mojangTexturesOnly(int timeoutMillis, int maxBytes) {
        return new SkinUrlPolicy(Set.of(MOJANG_TEXTURE_HOST), Set.of(80, 443), maxBytes, 0, timeoutMillis);
    }

    /** @return the parsed URI if it meets the rules. */
    public URI check(String url) throws SkinFetchException {
        if (url == null || url.isBlank()) {
            throw new SkinFetchException(SkinFetchException.Reason.INVALID_URL, "empty URL");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new SkinFetchException(SkinFetchException.Reason.INVALID_URL, "unparseable URL");
        }
        return check(uri);
    }

    public URI check(URI uri) throws SkinFetchException {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new SkinFetchException(SkinFetchException.Reason.INVALID_URL, "scheme must be http or https");
        }
        if (uri.getRawUserInfo() != null) {
            throw new SkinFetchException(SkinFetchException.Reason.INVALID_URL, "user info not allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new SkinFetchException(SkinFetchException.Reason.INVALID_URL, "missing host");
        }
        int port = uri.getPort() == -1 ? (scheme.equals("https") ? 443 : 80) : uri.getPort();
        if (!allowedPorts.contains(port)) {
            throw new SkinFetchException(SkinFetchException.Reason.INVALID_URL, "port " + port + " not allowed");
        }
        if (!hostAllowed(host)) {
            throw new SkinFetchException(SkinFetchException.Reason.HOST_NOT_ALLOWED, "host " + host + " not allowed");
        }
        return uri;
    }

    public boolean hostAllowed(String host) {
        if (allowedHosts.isEmpty()) return true;
        String h = host.toLowerCase(Locale.ROOT);
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        // Mojang's texture server is always allowed: uniforms and stored skins point there.
        if (h.equals(MOJANG_TEXTURE_HOST)) return true;
        for (String entry : allowedHosts) {
            if (entry.startsWith(".")) {
                if (h.endsWith(entry) || h.equals(entry.substring(1))) return true;
            } else if (h.equals(entry)) {
                return true;
            }
        }
        return false;
    }
}
