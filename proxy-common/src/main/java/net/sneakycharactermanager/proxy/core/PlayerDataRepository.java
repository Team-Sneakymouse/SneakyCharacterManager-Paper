package net.sneakycharactermanager.proxy.core;

import net.sneakycharactermanager.proxy.common.ProxyPlatform;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerDataRepository {

    private final ProxyPlatform platform;
    private final File characterDataFolder;
    private final ConcurrentMap<String, PlayerData> playerDataMap = new ConcurrentHashMap<>();

    public PlayerDataRepository(ProxyPlatform platform) {
        this.platform = platform;
        this.characterDataFolder = new File(platform.dataFolder(), "characterdata");
        if (!characterDataFolder.exists()) characterDataFolder.mkdirs();
    }

    public File characterDataFolder() {
        return characterDataFolder;
    }

    public PlayerData get(String playerUUID) {
        return playerDataMap.computeIfAbsent(playerUUID, uuid -> new PlayerData(platform, this, uuid));
    }

    public void remove(String playerUUID) {
        playerDataMap.remove(playerUUID);
    }

    public List<String> getAllCharacters(String filter) throws IOException {
        if (!characterDataFolder.exists() || characterDataFolder.listFiles() == null) return null;

        List<String> characterInformation = new ArrayList<>();
        for (File playerFile : Objects.requireNonNull(characterDataFolder.listFiles())) {
            if (!playerFile.getName().endsWith(".yml")) continue;
            Map<String, Object> root = net.sneakycharactermanager.proxy.common.YamlFiles.loadOrEmpty(playerFile);
            String playerUUID = playerFile.getName().replace(".yml", "");

            for (String key : root.keySet()) {
                if ("lastPlayedCharacter".equalsIgnoreCase(key)) continue;
                Object sectionObj = root.get(key);
                if (!(sectionObj instanceof Map<?, ?> section)) continue;
                Object nameObj = section.get("name");
                String name = nameObj == null ? "" : String.valueOf(nameObj);
                if (name.toLowerCase().contains(filter.toLowerCase())) {
                    // Keep '$' separator for Paper side.
                    characterInformation.add(playerUUID + "$" + name);
                }
            }
        }
        return characterInformation;
    }
}

