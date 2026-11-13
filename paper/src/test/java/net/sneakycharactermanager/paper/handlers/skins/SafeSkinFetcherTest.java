package net.sneakycharactermanager.paper.handlers.skins;

import com.sun.net.httpserver.HttpServer;
import net.sneakycharactermanager.proxy.common.SkinFetchException;
import net.sneakycharactermanager.proxy.common.SkinFetchException.Reason;
import net.sneakycharactermanager.proxy.common.SkinUrlPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Runs against a loopback server; a test-only address filter stands in for "public" so loopback can be used. */
class SafeSkinFetcherTest {

    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

    private HttpServer server;
    private int port;
    private final AtomicInteger skinHits = new AtomicInteger();
    private SafeSkinFetcher loopbackAllowed;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        byte[] skin = png(64, 64);
        server.createContext("/skin.png", ex -> { skinHits.incrementAndGet(); send(ex, 200, skin); });
        server.createContext("/big.png", ex -> send(ex, 200, png(256, 256)));
        server.createContext("/bomb.png", ex -> send(ex, 200, headerClaiming(30000, 30000)));
        server.createContext("/huge", ex -> send(ex, 200, new byte[64 * 1024]));
        server.createContext("/missing", ex -> send(ex, 404, new byte[0]));
        server.createContext("/to-skin", ex -> redirect(ex, "/skin.png"));
        server.createContext("/to-other-loopback", ex -> redirect(ex, "http://127.0.0.2:" + port + "/skin.png"));
        server.createContext("/to-localhost", ex -> redirect(ex, "http://localhost:" + port + "/skin.png"));
        server.createContext("/loop", ex -> redirect(ex, "/loop"));
        server.start();
        // Allows exactly 127.0.0.1, so a redirect to any other address must be refused by the DNS filter.
        loopbackAllowed = new SafeSkinFetcher(address -> address.getHostAddress().equals("127.0.0.1"));
    }

    @AfterEach
    void stop() throws IOException {
        server.stop(0);
        loopbackAllowed.close();
    }

    private SkinUrlPolicy policy(Set<String> hosts) {
        return new SkinUrlPolicy(hosts, Set.of(port), 16 * 1024, 2, 3000);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    @Test
    void fetchesValidSkinAndFollowsCheckedRedirects() throws Exception {
        BufferedImage image = loopbackAllowed.fetchImage(url("/to-skin"), policy(Set.of()));
        assertEquals(64, image.getWidth());
        assertEquals(1, skinHits.get());
    }

    @Test
    void productionFilterRefusesLoopbackWithoutConnecting() throws Exception {
        try (SafeSkinFetcher real = new SafeSkinFetcher()) {
            assertReason(Reason.ADDRESS_NOT_PUBLIC, () -> real.fetchBytes(url("/skin.png"), policy(Set.of())));
            assertReason(Reason.ADDRESS_NOT_PUBLIC, () -> real.fetchBytes("http://[::1]:" + port + "/skin.png", policy(Set.of())));
        }
        assertEquals(0, skinHits.get());
    }

    @Test
    void everyRedirectHopIsRechecked() {
        assertReason(Reason.ADDRESS_NOT_PUBLIC, () -> loopbackAllowed.fetchBytes(url("/to-other-loopback"), policy(Set.of())));
        assertReason(Reason.HOST_NOT_ALLOWED, () -> loopbackAllowed.fetchBytes(url("/to-localhost"), policy(Set.of("127.0.0.1"))));
        assertReason(Reason.TOO_MANY_REDIRECTS, () -> loopbackAllowed.fetchBytes(url("/loop"), policy(Set.of())));
        assertEquals(0, skinHits.get());
    }

    @Test
    void capsBodySizeAndRejectsNonSkinImages() {
        assertReason(Reason.TOO_LARGE, () -> loopbackAllowed.fetchBytes(url("/huge"), policy(Set.of())));
        assertReason(Reason.NOT_A_SKIN_PNG, () -> loopbackAllowed.fetchImage(url("/big.png"), policy(Set.of())));
        assertReason(Reason.NOT_A_SKIN_PNG, () -> loopbackAllowed.fetchImage(url("/bomb.png"), policy(Set.of())));
    }

    @Test
    void transientFailuresAreNotRejections() {
        SkinFetchException notFound = assertThrows(SkinFetchException.class,
                () -> loopbackAllowed.fetchBytes(url("/missing"), policy(Set.of())));
        assertEquals(Reason.HTTP_STATUS, notFound.reason());
        assertFalse(notFound.isRejection());
    }

    @Test
    void urlRulesApplyBeforeAnyRequest() {
        assertReason(Reason.INVALID_URL, () -> loopbackAllowed.fetchBytes("http://127.0.0.1:1/skin.png", policy(Set.of())));
        assertReason(Reason.INVALID_URL, () -> loopbackAllowed.fetchBytes("file:///etc/passwd", policy(Set.of())));
    }

    private static void assertReason(Reason reason, org.junit.jupiter.api.function.Executable call) {
        SkinFetchException e = assertThrows(SkinFetchException.class, call);
        assertEquals(reason, e.reason(), e.getMessage());
    }

    private static void send(com.sun.net.httpserver.HttpExchange ex, int status, byte[] body) throws IOException {
        ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    private static void redirect(com.sun.net.httpserver.HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().add("Location", location);
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    private static byte[] headerClaiming(int width, int height) {
        return ByteBuffer.allocate(64)
                .put(new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' })
                .putInt(13).put(new byte[] { 'I', 'H', 'D', 'R' })
                .putInt(width).putInt(height)
                .array();
    }
}
