package net.sneakycharactermanager.proxy.common;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Downloads skin images for content hashing on the proxy.
 */
public final class SkinImageFetcher {

    private final HttpClient httpClient;
    private final int timeoutSeconds;
    private final int maxBytes;

    public SkinImageFetcher(int timeoutSeconds, int maxBytes) {
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
        this.maxBytes = Math.max(1024, maxBytes);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(this.timeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public BufferedImage downloadImage(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        byte[] bytes = readAllBytesLimited(response.body());
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) throw new IOException("Not a readable image: " + url);
            return image;
        }
    }

    private byte[] readAllBytesLimited(InputStream in) throws IOException {
        byte[] buffer = new byte[8192];
        int total = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("Image exceeds max size (" + maxBytes + " bytes)");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
