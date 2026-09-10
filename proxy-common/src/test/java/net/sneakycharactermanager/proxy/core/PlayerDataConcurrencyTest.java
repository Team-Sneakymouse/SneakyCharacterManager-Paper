package net.sneakycharactermanager.proxy.core;

import net.sneakycharactermanager.proxy.common.ProxyLogger;
import net.sneakycharactermanager.proxy.common.ProxyPlatform;
import net.sneakycharactermanager.proxy.common.YamlFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDataConcurrencyTest {

    @TempDir
    File dataFolder;

    @Test
    void concurrentCharacterCreationDoesNotLoseUpdates() throws Exception {
        ProxyPlatform platform = platform(dataFolder);
        PlayerDataRepository repository = new PlayerDataRepository(
                platform,
                new GlobalSkinCache(dataFolder, platform.logger())
        );
        String playerUuid = UUID.randomUUID().toString();
        PlayerData playerData = repository.get(playerUuid);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<?>> creations = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                int characterNumber = i;
                creations.add(executor.submit(() -> {
                    start.await();
                    playerData.createNewCharacter(
                            "character-" + characterNumber,
                            "Character " + characterNumber,
                            "",
                            "",
                            false
                    );
                    return null;
                }));
            }

            start.countDown();
            for (Future<?> creation : creations) {
                creation.get(15, TimeUnit.SECONDS);
            }
        }

        File playerFile = new File(new File(dataFolder, "characterdata"), playerUuid + ".yml");
        Map<String, Object> saved = YamlFiles.load(playerFile, platform.logger());
        assertNotNull(saved);
        for (int i = 0; i < 40; i++) {
            assertTrue(saved.containsKey("character-" + i), "missing character " + i);
        }
    }

    private static ProxyPlatform platform(File dataFolder) {
        ProxyLogger logger = new ProxyLogger() {
            @Override public void info(String message) {}
            @Override public void warning(String message) {}
            @Override public void severe(String message) {}
            @Override public void severe(String message, Throwable throwable) {}
        };

        return new ProxyPlatform() {
            @Override public File dataFolder() {
                return dataFolder;
            }

            @Override public ProxyLogger logger() {
                return logger;
            }

            @Override public String playerName(UUID playerUniqueId) {
                return "Player";
            }
        };
    }
}
