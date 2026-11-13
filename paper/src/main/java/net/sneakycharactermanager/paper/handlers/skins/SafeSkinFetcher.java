package net.sneakycharactermanager.paper.handlers.skins;

import net.sneakycharactermanager.proxy.common.PngSkinHeader;
import net.sneakycharactermanager.proxy.common.PublicAddresses;
import net.sneakycharactermanager.proxy.common.SkinFetchException;
import net.sneakycharactermanager.proxy.common.SkinFetchException.Reason;
import net.sneakycharactermanager.proxy.common.SkinUrlPolicy;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.DnsResolver;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.function.Predicate;

/**
 * Fetches player-supplied skin URLs without letting them reach internal addresses or exhaust memory (issue #28).
 * <ul>
 *   <li>URL rules from {@link SkinUrlPolicy} on the first URL and on every redirect hop (redirects are followed by hand).</li>
 *   <li>DNS goes through a resolver that refuses the host if any resolved address is not public, and the connection
 *       uses only the addresses it returned, so the checked address is the connected one (no DNS-rebinding gap).</li>
 *   <li>Byte cap, overall deadline, and a PNG header check (64x64 / 64x32) before decoding.</li>
 * </ul>
 * Blocking: call from an async task.
 */
public final class SafeSkinFetcher implements Closeable {

    private static final String USER_AGENT = "SneakyCharacterManager skin fetcher";

    private final CloseableHttpClient client;

    public SafeSkinFetcher() {
        this(PublicAddresses::isPublic);
    }

    /** @param addressAllowed address filter; tests pass one that allows loopback. */
    SafeSkinFetcher(Predicate<InetAddress> addressAllowed) {
        this.client = HttpClientBuilder.create()
                .setDnsResolver(new FilteringDnsResolver(addressAllowed))
                .disableRedirectHandling()
                .disableCookieManagement()
                .disableAuthCaching()
                .disableAutomaticRetries()
                .setUserAgent(USER_AGENT)
                .build();
    }

    public BufferedImage fetchImage(String url, SkinUrlPolicy policy) throws IOException {
        return PngSkinHeader.decode(fetchBytes(url, policy));
    }

    public byte[] fetchBytes(String url, SkinUrlPolicy policy) throws IOException {
        long deadline = System.nanoTime() + policy.timeoutMillis() * 1_000_000L;
        URI uri = policy.check(url);
        for (int hop = 0; ; hop++) {
            HttpGet get = new HttpGet(uri);
            int remaining = remainingMillis(deadline, uri);
            get.setConfig(RequestConfig.custom()
                    .setConnectTimeout(remaining)
                    .setSocketTimeout(remaining)
                    .setConnectionRequestTimeout(remaining)
                    .setRedirectsEnabled(false)
                    .build());
            try (CloseableHttpResponse response = client.execute(get)) {
                int status = response.getStatusLine().getStatusCode();
                if (isRedirect(status)) {
                    Header location = response.getFirstHeader("Location");
                    if (location == null) {
                        throw new SkinFetchException(Reason.HTTP_STATUS, "HTTP " + status + " without Location from " + uri);
                    }
                    if (hop >= policy.maxRedirects()) {
                        throw new SkinFetchException(Reason.TOO_MANY_REDIRECTS, "more than " + policy.maxRedirects() + " redirects from " + url);
                    }
                    try {
                        uri = policy.check(uri.resolve(location.getValue().trim()));
                    } catch (IllegalArgumentException e) {
                        throw new SkinFetchException(Reason.INVALID_URL, "bad redirect Location from " + uri);
                    }
                    continue;
                }
                if (status != 200) {
                    throw new SkinFetchException(Reason.HTTP_STATUS, "HTTP " + status + " for " + uri);
                }
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    throw new SkinFetchException(Reason.NOT_A_SKIN_PNG, "empty body from " + uri);
                }
                if (entity.getContentLength() > policy.maxBytes()) {
                    throw new SkinFetchException(Reason.TOO_LARGE, "Content-Length " + entity.getContentLength() + " from " + uri);
                }
                try (InputStream in = entity.getContent()) {
                    return readLimited(in, policy.maxBytes(), deadline, uri);
                }
            } catch (BlockedHostException e) {
                throw new SkinFetchException(Reason.ADDRESS_NOT_PUBLIC, e.getMessage());
            } catch (SocketTimeoutException | ConnectTimeoutException e) {
                throw new SkinFetchException(Reason.TIMEOUT, "timed out fetching " + uri, e);
            } catch (SkinFetchException e) {
                throw e;
            } catch (UnknownHostException e) {
                throw new SkinFetchException(Reason.NETWORK, "unknown host " + uri.getHost(), e);
            } catch (IOException e) {
                throw new SkinFetchException(Reason.NETWORK, e.getClass().getSimpleName() + " fetching " + uri, e);
            }
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static int remainingMillis(long deadline, URI uri) throws SkinFetchException {
        long remaining = (deadline - System.nanoTime()) / 1_000_000L;
        if (remaining <= 0) throw new SkinFetchException(Reason.TIMEOUT, "deadline passed before fetching " + uri);
        return (int) Math.min(Integer.MAX_VALUE, remaining);
    }

    private static byte[] readLimited(InputStream in, int maxBytes, long deadline, URI uri) throws IOException {
        byte[] buffer = new byte[8192];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new SkinFetchException(Reason.TOO_LARGE, "body from " + uri + " exceeds " + maxBytes + " bytes");
            }
            out.write(buffer, 0, read);
            // Socket timeout bounds each read; this bounds a slow drip across many reads.
            if (System.nanoTime() > deadline) {
                throw new SkinFetchException(Reason.TIMEOUT, "deadline passed while reading " + uri);
            }
        }
        return out.toByteArray();
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    /** Thrown from DNS resolution; {@link DnsResolver} may only throw {@link UnknownHostException}. */
    private static final class BlockedHostException extends UnknownHostException {
        BlockedHostException(String message) {
            super(message);
        }
    }

    private record FilteringDnsResolver(Predicate<InetAddress> addressAllowed) implements DnsResolver {
        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                // Refuse the whole host if any record is non-public, rather than silently picking the public ones.
                if (!addressAllowed.test(address)) {
                    throw new BlockedHostException(host + " resolved to non-public " + address.getHostAddress());
                }
            }
            return addresses;
        }
    }
}
