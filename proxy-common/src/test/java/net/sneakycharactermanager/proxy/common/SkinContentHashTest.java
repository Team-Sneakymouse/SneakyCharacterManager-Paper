package net.sneakycharactermanager.proxy.common;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class SkinContentHashTest {

    @Test
    void samePixelsProduceSameSkinId() {
        BufferedImage a = solidImage(64, 64, Color.RED.getRGB());
        BufferedImage b = solidImage(64, 64, Color.RED.getRGB());
        assertEquals(SkinContentHash.skinIdFromImage(a), SkinContentHash.skinIdFromImage(b));
    }

    @Test
    void differentPixelsProduceDifferentSkinId() {
        BufferedImage a = solidImage(64, 64, Color.RED.getRGB());
        BufferedImage b = solidImage(64, 64, Color.BLUE.getRGB());
        assertNotEquals(SkinContentHash.skinIdFromImage(a), SkinContentHash.skinIdFromImage(b));
    }

    @Test
    void skinIdLengthIsBase64Url128Bit() {
        String id = SkinContentHash.skinIdFromImage(solidImage(8, 8, 0xFF00FF00));
        assertNotNull(id);
        assertEquals(22, id.length());
    }

    @Test
    void bundledFinalizedSkinsHaveDistinctIds() throws Exception {
        String idA = SkinContentHash.skinIdFromImage(
                javax.imageio.ImageIO.read(getClass().getResourceAsStream("/finalized_lSze2Y74.png")));
        String idB = SkinContentHash.skinIdFromImage(
                javax.imageio.ImageIO.read(getClass().getResourceAsStream("/finalized_kS5thaRK.png")));
        assertNotNull(idA);
        assertNotNull(idB);
        assertNotEquals(idA, idB);
        assertEquals("7W3yUM_EAEIH68Iuw-kDsA", idA);
        assertEquals("8-kvlTdUR7-JqdGQi8GYLA", idB);
    }

    @Test
    void recognizesMojangTextureUrl() {
        assertTrue(SkinContentHash.isMojangTextureUrl(
                "http://textures.minecraft.net/texture/abc"));
        assertTrue(SkinContentHash.isMojangTextureUrl(
                "https://textures.minecraft.net/texture/abc"));
        assertFalse(SkinContentHash.isMojangTextureUrl("https://example.com/skin.png"));
    }

    private static BufferedImage solidImage(int w, int h, int argb) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }
}
