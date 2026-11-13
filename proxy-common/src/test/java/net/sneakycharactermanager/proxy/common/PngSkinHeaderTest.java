package net.sneakycharactermanager.proxy.common;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class PngSkinHeaderTest {

    static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF336699);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** A header-only "PNG" claiming the given size; decoding it would allocate width*height pixels. */
    static byte[] headerClaiming(int width, int height) {
        return ByteBuffer.allocate(PngSkinHeader.HEADER_BYTES + 8)
                .put(new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' })
                .putInt(13).put(new byte[] { 'I', 'H', 'D', 'R' })
                .putInt(width).putInt(height)
                .array();
    }

    @Test
    void acceptsModernAndLegacySkinSizes() throws Exception {
        assertEquals(64, PngSkinHeader.decode(png(64, 64)).getHeight());
        assertEquals(32, PngSkinHeader.decode(png(64, 32)).getHeight());
    }

    @Test
    void acceptsRealSkinFixture() throws Exception {
        byte[] bytes = getClass().getResourceAsStream("/finalized_lSze2Y74.png").readAllBytes();
        assertEquals(64, PngSkinHeader.decode(bytes).getWidth());
    }

    @Test
    void rejectsOtherDimensionsBeforeDecoding() {
        assertReason(SkinFetchException.Reason.NOT_A_SKIN_PNG, () -> PngSkinHeader.decode(png(128, 128)));
        assertReason(SkinFetchException.Reason.NOT_A_SKIN_PNG, () -> PngSkinHeader.decode(headerClaiming(30000, 30000)));
        assertReason(SkinFetchException.Reason.NOT_A_SKIN_PNG, () -> PngSkinHeader.decode(headerClaiming(-1, 64)));
    }

    @Test
    void rejectsNonPngAndTruncated() throws Exception {
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB), "jpg", jpeg);
        assertReason(SkinFetchException.Reason.NOT_A_SKIN_PNG, () -> PngSkinHeader.decode(jpeg.toByteArray()));
        assertReason(SkinFetchException.Reason.NOT_A_SKIN_PNG, () -> PngSkinHeader.decode(new byte[10]));
        assertReason(SkinFetchException.Reason.NOT_A_SKIN_PNG, () -> PngSkinHeader.decode(headerClaiming(64, 64)));
    }

    static void assertReason(SkinFetchException.Reason reason, org.junit.jupiter.api.function.Executable call) {
        SkinFetchException e = assertThrows(SkinFetchException.class, call);
        assertEquals(reason, e.reason());
    }
}
