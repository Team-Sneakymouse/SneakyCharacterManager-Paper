package net.sneakycharactermanager.proxy.core;

import net.sneakycharactermanager.proxy.common.ProxyLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class SkinResolveServiceTest {

    private static final String MOJANG_URL =
            "http://textures.minecraft.net/texture/88587aa1dc63b2b2f14cc99572bf3af8f95192f57a2a4b1dc76c42d1977698c4";

    @TempDir
    File tempDir;

    @Test
    void mojangCacheHitDoesNotThrowRecursiveUpdate() throws Exception {
        File dataFolder = new File(tempDir, "proxy-data");
        dataFolder.mkdirs();
        Files.writeString(new File(dataFolder, "skin_cache.yml").toPath(), """
                skins:
                  - id: 0dufMIZkz7xneDSownBi3w
                    mojang_url: %s
                    texture: eyJ0ZXN0Ijp0cnVlfQ==
                    signature: sig
                """.formatted(MOJANG_URL));

        GlobalSkinCache cache = new GlobalSkinCache(dataFolder, noopLogger());
        SkinResolveService service = new SkinResolveService(cache, 5, 1024 * 1024, noopLogger());

        SkinResolveService.Result first = service.resolve(MOJANG_URL).join();
        assertEquals(SkinResolveService.Status.HIT, first.status);
        assertEquals("0dufMIZkz7xneDSownBi3w", first.skinId);

        SkinResolveService.Result second = service.resolve(MOJANG_URL).join();
        assertEquals(SkinResolveService.Status.HIT, second.status);
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
