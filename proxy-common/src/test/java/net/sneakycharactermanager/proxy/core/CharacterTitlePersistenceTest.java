package net.sneakycharactermanager.proxy.core;

import net.sneakycharactermanager.proxy.common.ProxyLogger;
import net.sneakycharactermanager.proxy.common.ProxyPlatform;
import net.sneakycharactermanager.proxy.common.YamlFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CharacterTitlePersistenceTest {

    @TempDir
    File dataFolder;

    @Test
    void titleIsSavedAndReloaded() {
        ProxyPlatform platform = platform(dataFolder);
        PlayerDataRepository repository = new PlayerDataRepository(
                platform,
                new GlobalSkinCache(dataFolder, platform.logger())
        );
        String playerUuid = UUID.randomUUID().toString();
        PlayerData playerData = repository.get(playerUuid);
        String characterUuid = UUID.randomUUID().toString();
        playerData.createNewCharacter(characterUuid, "Test", "", "", false);
        playerData.setCharacterTitle(characterUuid, "master_fisherman");

        File playerFile = new File(new File(dataFolder, "characterdata"), playerUuid + ".yml");
        Map<String, Object> saved = YamlFiles.load(playerFile, platform.logger());
        assertNotNull(saved);
        Object sectionObj = saved.get(characterUuid);
        assertInstanceOf(Map.class, sectionObj);
        @SuppressWarnings("unchecked")
        Map<String, Object> section = (Map<String, Object>) sectionObj;
        assertEquals("master_fisherman", String.valueOf(section.get("title")));

        PlayerData reloaded = new PlayerDataRepository(
                platform,
                new GlobalSkinCache(dataFolder, platform.logger())
        ).get(playerUuid);
        CharacterData character = reloaded.getCharacter(characterUuid);
        assertNotNull(character);
        assertEquals("master_fisherman", character.title());
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

            @Override public String playerName(java.util.UUID uuid) {
                return "Player";
            }
        };
    }
}
