package net.sneakycharactermanager.proxy.core;

import net.sneakycharactermanager.proxy.common.ProxyLogger;
import net.sneakycharactermanager.proxy.common.ProxyPlatform;
import net.sneakycharactermanager.proxy.common.YamlFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PlayerDataRepositoryTest {
    @TempDir File dataFolder;
    static final String OWNER = "11111111-1111-1111-1111-111111111111";
    static final String TAKEN = "22222222-2222-2222-2222-222222222222";

    @Test void ownersOfCharacterIgnoresTheLastPlayedPointer() {
        ProxyLogger logger = new ProxyLogger() {
            @Override public void info(String message) {}
            @Override public void warning(String message) {}
            @Override public void severe(String message) { fail(message); }
            @Override public void severe(String message, Throwable throwable) { fail(message, throwable); }
        };
        Map<String, Object> player = new LinkedHashMap<>();
        player.put("lastPlayedCharacter", TAKEN);
        player.put(TAKEN, new LinkedHashMap<>(Map.of("enabled", true, "name", "Mouse")));
        YamlFiles.save(new File(new File(dataFolder, "characterdata"), OWNER + ".yml"), player, logger);

        PlayerDataRepository repository = new PlayerDataRepository(new ProxyPlatform() {
            @Override public File dataFolder() { return dataFolder; }
            @Override public ProxyLogger logger() { return logger; }
            @Override public String playerName(UUID uuid) { return "Player"; }
        }, null);
        assertEquals(List.of(OWNER), repository.ownersOfCharacter(TAKEN));
        assertEquals(List.of(), repository.ownersOfCharacter("lastPlayedCharacter"));
        assertEquals(List.of(), repository.ownersOfCharacter("missing"));
    }
}
