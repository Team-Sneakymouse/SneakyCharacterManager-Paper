package net.sneakycharactermanager.proxy.core;

import net.sneakycharactermanager.proxy.common.ProxyPlatform;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerDataRepository {

    private final ProxyPlatform platform;
    private final GlobalSkinCache globalSkinCache;
    private final File characterDataFolder;
    private final ConcurrentMap<String, PlayerData> playerDataMap = new ConcurrentHashMap<>();

    public PlayerDataRepository(ProxyPlatform platform, GlobalSkinCache globalSkinCache) {
        this.platform = platform;
        this.globalSkinCache = globalSkinCache;
        this.characterDataFolder = new File(platform.dataFolder(), "characterdata");
        if (!characterDataFolder.exists()) characterDataFolder.mkdirs();
    }

    public GlobalSkinCache globalSkinCache() {
        return globalSkinCache;
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

    /** Owners whose file holds {@code characterId}; scans every file, so keep it off hot paths. */
    public List<String> ownersOfCharacter(String characterId) {
        File[] files = characterDataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || "lastPlayedCharacter".equalsIgnoreCase(characterId)) return List.of();
        List<String> owners = new ArrayList<>();
        for (File playerFile : files) {
            Map<String, Object> root = net.sneakycharactermanager.proxy.common.YamlFiles.load(playerFile, platform.logger());
            if (root != null && root.get(characterId) instanceof Map<?, ?>) owners.add(playerFile.getName().replace(".yml", ""));
        }
        return owners;
    }

    /**
     * Directory entries for every character in {@code owner}'s file, disabled included, in file (menu) order. Each
     * entry is four strings: id, owner, name, enabled. Reads the file directly so offline accounts are neither
     * created nor cached. The {@code template} pseudo-account is not part of the directory.
     */
    public List<String> directoryOfOwner(String owner) {
        return directoryEntries(owner, null);
    }

    /** Directory entries for {@code characterId} under every non-template owner; more than one means a duplicate. */
    public List<String> directoryOfCharacter(String characterId) {
        List<String> entries = new ArrayList<>();
        for (String owner : ownersOfCharacter(characterId)) entries.addAll(directoryEntries(owner, characterId));
        return entries;
    }

    private List<String> directoryEntries(String owner, String onlyCharacter) {
        // Canonical UUIDs only: excludes 'template' and keeps request input out of file paths.
        if (!isCanonicalUuid(owner)) return List.of();
        File playerFile = new File(characterDataFolder, owner + ".yml");
        if (!playerFile.isFile()) return List.of();
        Map<String, Object> root = net.sneakycharactermanager.proxy.common.YamlFiles.load(playerFile, platform.logger());
        if (root == null) return List.of();
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            String id = entry.getKey();
            if ("lastPlayedCharacter".equalsIgnoreCase(id) || !(entry.getValue() instanceof Map<?, ?> section)) continue;
            if (onlyCharacter != null && !onlyCharacter.equals(id)) continue;
            entries.add(id);
            entries.add(owner);
            entries.add(PlayerData.stringOrEmpty(section.get("name")));
            entries.add(String.valueOf(PlayerData.boolOrDefault(section.get("enabled"), true)));
        }
        return entries;
    }

    static boolean isCanonicalUuid(String value) {
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public List<String> getAllCharacters(String filter) throws IOException {
        if (!characterDataFolder.exists() || characterDataFolder.listFiles() == null) return null;

        List<String> characterInformation = new ArrayList<>();
        for (File playerFile : Objects.requireNonNull(characterDataFolder.listFiles())) {
            if (!playerFile.getName().endsWith(".yml")) continue;
            Map<String, Object> root = net.sneakycharactermanager.proxy.common.YamlFiles.load(playerFile, platform.logger());
            if (root == null) continue;
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

