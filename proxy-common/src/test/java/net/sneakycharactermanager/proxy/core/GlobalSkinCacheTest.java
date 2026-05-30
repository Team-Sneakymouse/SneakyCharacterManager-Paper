package net.sneakycharactermanager.proxy.core;

import net.sneakycharactermanager.proxy.common.ProxyLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GlobalSkinCacheTest {

    private static final String MOJANG_URL =
            "http://textures.minecraft.net/texture/88587aa1dc63b2b2f14cc99572bf3af8f95192f57a2a4b1dc76c42d1977698c4";
    private static final String TEXTURE =
            "ewogICJ0aW1lc3RhbXAiIDogMTcyOTY5OTI3MDU4MiwKICAicHJvZmlsZUlkIiA6ICI3NjczNTA4YTI4MTk0ODMwYTY0YzhjYWFjNGJhNmM2NiIsCiAgInByb2ZpbGVOYW1lIiA6ICJKb3Jpc0hlcm9zIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzg4NTg3YWExZGM2M2IyYjJmMTRjYzk5NTcyYmYzYWY4Zjk1MTkyZjU3YTJhNGIxZGM3NmM0MmQxOTc3Njk4YzQiCiAgICB9CiAgfQp9";
    private static final String SIGNATURE = "test-signature";

    @TempDir
    File tempDir;

    @Test
    void getByMojangUrlMatchesHttpAndHttpsForms() throws Exception {
        File dataFolder = new File(tempDir, "proxy-data");
        dataFolder.mkdirs();
        Files.writeString(new File(dataFolder, "skin_cache.yml").toPath(), """
                skins:
                  - id: 0dufMIZkz7xneDSownBi3w
                    mojang_url: %s
                    texture: %s
                    signature: %s
                """.formatted(MOJANG_URL, TEXTURE, SIGNATURE));

        GlobalSkinCache cache = new GlobalSkinCache(dataFolder, noopLogger());

        Optional<GlobalSkinCache.Entry> httpHit = cache.getByMojangUrl(MOJANG_URL);
        assertTrue(httpHit.isPresent());
        assertEquals("0dufMIZkz7xneDSownBi3w", httpHit.get().skinId);
        assertEquals(TEXTURE, httpHit.get().texture);

        String httpsForm = "https://textures.minecraft.net/texture/"
                + "88587aa1dc63b2b2f14cc99572bf3af8f95192f57a2a4b1dc76c42d1977698c4";
        Optional<GlobalSkinCache.Entry> httpsHit = cache.getByMojangUrl(httpsForm);
        assertTrue(httpsHit.isPresent());
        assertEquals(httpHit.get().skinId, httpsHit.get().skinId);
    }

    private static ProxyLogger noopLogger() {
        return new ProxyLogger() {
            @Override public void info(String message) {}
            @Override public void warning(String message) {}
            @Override public void severe(String message) {}
            @Override public void severe(String message, Throwable t) {}
        };
    }
}
