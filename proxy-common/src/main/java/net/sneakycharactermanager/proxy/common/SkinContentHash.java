package net.sneakycharactermanager.proxy.common;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Content-addressed skin IDs from canonical decoded pixels (not raw PNG bytes).
 */
public final class SkinContentHash {

    private static final String MOJANG_TEXTURE_PREFIX = "http://textures.minecraft.net/texture/";

    private SkinContentHash() {}

    public static boolean isMojangTextureUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        return url.startsWith(MOJANG_TEXTURE_PREFIX)
                || url.startsWith("https://textures.minecraft.net/texture/");
    }

    public static String normalizeMojangTextureUrl(String url) {
        if (url == null) return "";
        if (url.startsWith("https://textures.minecraft.net/texture/")) {
            return MOJANG_TEXTURE_PREFIX + url.substring("https://textures.minecraft.net/texture/".length());
        }
        return url;
    }

    /**
     * @return 128-bit Base64URL skin id (~22 chars), or null if the image is invalid.
     */
    public static String skinIdFromImage(BufferedImage image) {
        if (image == null) return null;
        try {
            byte[] canonical = canonicalPixels(image);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical);
            byte[] truncated = new byte[16];
            System.arraycopy(hash, 0, truncated, 0, 16);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(truncated);
        } catch (NoSuchAlgorithmException | IOException e) {
            return null;
        }
    }

    public static byte[] canonicalPixels(BufferedImage image) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage argb = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        argb.getGraphics().drawImage(image, 0, 0, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);
        data.writeInt(width);
        data.writeInt(height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data.writeInt(argb.getRGB(x, y));
            }
        }
        data.flush();
        return out.toByteArray();
    }

    /**
     * Decode a Mojang {@code textures} property value and return the SKIN URL, if present.
     */
    public static String mojangUrlFromTextureProperty(String textureValue) {
        if (textureValue == null || textureValue.isEmpty()) return null;
        try {
            String decoded = new String(Base64.getDecoder().decode(textureValue), StandardCharsets.UTF_8);
            int urlKey = decoded.indexOf("\"url\"");
            if (urlKey < 0) return null;
            int colon = decoded.indexOf(':', urlKey);
            if (colon < 0) return null;
            int startQuote = decoded.indexOf('"', colon + 1);
            if (startQuote < 0) return null;
            int endQuote = decoded.indexOf('"', startQuote + 1);
            if (endQuote < 0) return null;
            return normalizeMojangTextureUrl(decoded.substring(startQuote + 1, endQuote));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
