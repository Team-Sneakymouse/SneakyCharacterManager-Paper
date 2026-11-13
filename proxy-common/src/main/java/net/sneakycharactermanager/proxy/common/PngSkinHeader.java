package net.sneakycharactermanager.proxy.common;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Accepts only PNG skins of 64x64 or 64x32, judged from the IHDR header before any decoding, so a small
 * file that claims huge dimensions never reaches {@link ImageIO#read}.
 */
public final class PngSkinHeader {

    /** PNG signature (8) + IHDR length (4) + "IHDR" (4) + width (4) + height (4). */
    public static final int HEADER_BYTES = 24;

    private static final byte[] SIGNATURE = {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'
    };

    private PngSkinHeader() {}

    /** @return {width, height} from a valid skin header. */
    public static int[] check(byte[] bytes) throws SkinFetchException {
        if (bytes == null || bytes.length < HEADER_BYTES) {
            throw new SkinFetchException(SkinFetchException.Reason.NOT_A_SKIN_PNG, "too short for a PNG header");
        }
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (bytes[i] != SIGNATURE[i]) {
                throw new SkinFetchException(SkinFetchException.Reason.NOT_A_SKIN_PNG, "missing PNG signature");
            }
        }
        if (readInt(bytes, 8) != 13 || bytes[12] != 'I' || bytes[13] != 'H' || bytes[14] != 'D' || bytes[15] != 'R') {
            throw new SkinFetchException(SkinFetchException.Reason.NOT_A_SKIN_PNG, "first chunk is not IHDR");
        }
        int width = readInt(bytes, 16);
        int height = readInt(bytes, 20);
        if (width != 64 || (height != 64 && height != 32)) {
            throw new SkinFetchException(SkinFetchException.Reason.NOT_A_SKIN_PNG,
                    "unsupported dimensions " + Integer.toUnsignedString(width) + "x" + Integer.toUnsignedString(height));
        }
        return new int[] { width, height };
    }

    /** Checks the header, then decodes. */
    public static BufferedImage decode(byte[] bytes) throws IOException {
        int[] size = check(bytes);
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException | RuntimeException e) {
            throw new SkinFetchException(SkinFetchException.Reason.NOT_A_SKIN_PNG, "PNG failed to decode", e);
        }
        if (image == null || image.getWidth() != size[0] || image.getHeight() != size[1]) {
            throw new SkinFetchException(SkinFetchException.Reason.NOT_A_SKIN_PNG, "PNG did not decode to its header size");
        }
        return image;
    }

    private static int readInt(byte[] b, int offset) {
        return ((b[offset] & 0xFF) << 24) | ((b[offset + 1] & 0xFF) << 16)
                | ((b[offset + 2] & 0xFF) << 8) | (b[offset + 3] & 0xFF);
    }
}
