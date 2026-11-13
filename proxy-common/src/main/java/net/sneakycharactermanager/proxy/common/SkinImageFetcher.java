package net.sneakycharactermanager.proxy.common;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Downloads Mojang texture images ({@code textures.minecraft.net}) for content hashing. Player-supplied URLs are
 * never fetched here: Paper validates, downloads and hashes those itself (see issue #28).
 */
public final class SkinImageFetcher {

    private final HttpClient httpClient;
    private final SkinUrlPolicy policy;

    public SkinImageFetcher(int timeoutSeconds, int maxBytes) {
        this.policy = SkinUrlPolicy.mojangTexturesOnly(Math.max(1, timeoutSeconds) * 1000, maxBytes);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(policy.timeoutMillis()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public BufferedImage downloadImage(String url) throws IOException, InterruptedException {
        URI uri = policy.check(url);
        requirePublic(uri.getHost());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMillis(policy.timeoutMillis()))
                .GET()
                .build();
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpTimeoutException e) {
            throw new SkinFetchException(SkinFetchException.Reason.TIMEOUT, "timed out fetching " + uri, e);
        }
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                throw new SkinFetchException(SkinFetchException.Reason.HTTP_STATUS,
                        "HTTP " + response.statusCode() + " for " + uri);
            }
            return PngSkinHeader.decode(readLimited(body, policy.maxBytes()));
        }
    }

    private static void requirePublic(String host) throws SkinFetchException {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (!PublicAddresses.isPublic(address)) {
                    throw new SkinFetchException(SkinFetchException.Reason.ADDRESS_NOT_PUBLIC,
                            host + " resolved to non-public " + address.getHostAddress());
                }
            }
        } catch (UnknownHostException e) {
            throw new SkinFetchException(SkinFetchException.Reason.NETWORK, "unknown host " + host, e);
        }
    }

    /** Reads at most {@code maxBytes}; throws {@link SkinFetchException.Reason#TOO_LARGE} beyond that. */
    public static byte[] readLimited(InputStream in, int maxBytes) throws IOException {
        byte[] buffer = new byte[8192];
        int total = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new SkinFetchException(SkinFetchException.Reason.TOO_LARGE, "body exceeds " + maxBytes + " bytes");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
