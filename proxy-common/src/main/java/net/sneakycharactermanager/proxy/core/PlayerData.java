package net.sneakycharactermanager.proxy.core;

import net.sneakycharactermanager.proxy.common.ProxyPlatform;
import net.sneakycharactermanager.proxy.common.ProxyServerConnection;
import net.sneakycharactermanager.proxy.common.SkinContentHash;
import net.sneakycharactermanager.proxy.common.SkinImageFetcher;
import net.sneakycharactermanager.proxy.common.YamlFiles;

import java.io.File;
import java.util.*;

public final class PlayerData {

    private final ProxyPlatform platform;
    private final PlayerDataRepository repo;
    private final String playerUUID;
    private final File playerFile;

    private String lastPlayedCharacter;
    private final Map<String, CharacterData> characterMap = new LinkedHashMap<>();

    PlayerData(ProxyPlatform platform, PlayerDataRepository repo, String playerUUID) {
        this.platform = platform;
        this.repo = repo;
        this.playerUUID = playerUUID;
        this.playerFile = new File(repo.characterDataFolder(), playerUUID + ".yml");

        ensureInitialized();
        storeCharacters();
    }

    private void ensureInitialized() {
        Map<String, Object> root = YamlFiles.load(playerFile, platform.logger());
        if (root == null) {
            // File exists but failed to parse -- do NOT overwrite it.
            platform.logger().severe("Player data file is unreadable, refusing to overwrite: " + playerFile.getAbsolutePath());
            this.lastPlayedCharacter = "";
            return;
        }

        this.lastPlayedCharacter = stringOrEmpty(root.get("lastPlayedCharacter"));

        if (this.lastPlayedCharacter == null || this.lastPlayedCharacter.isEmpty()) {
            // Only create a default character when the file truly doesn't exist yet.
            if (playerFile.exists() && playerFile.length() > 0) {
                platform.logger().severe("Player data file exists but has no lastPlayedCharacter, refusing to overwrite: " + playerFile.getAbsolutePath());
                return;
            }

            String firstCharacterUUID = UUID.randomUUID().toString();
            this.lastPlayedCharacter = firstCharacterUUID;
            root.put("lastPlayedCharacter", firstCharacterUUID);

            Map<String, Object> section = new LinkedHashMap<>();
            section.put("enabled", true);
            if ("template".equals(playerUUID)) {
                section.put("name", "template");
                section.put("skin", "http://textures.minecraft.net/texture/b90696ebc74ce7a900ec8abeec0dc1ccb3534c1b8ba6cbd9e83c5cd7f381fb48");
                section.put("skinUUID", "");
            } else {
                String name = platform.playerName(UUID.fromString(playerUUID));
                section.put("name", name == null ? "" : name);
                section.put("skin", "");
                section.put("skinUUID", "");
            }
            section.put("texture", "");
            section.put("signature", "");
            section.put("slim", false);
            section.put("tags", "");
            section.put("gender", "");

            root.put(firstCharacterUUID, section);
            YamlFiles.save(playerFile, root, platform.logger());
        }
    }

    private void storeCharacters() {
        Map<String, Object> root = YamlFiles.load(playerFile, platform.logger());
        if (root == null) return;
        characterMap.clear();

        Object lpc = root.get("lastPlayedCharacter");
        this.lastPlayedCharacter = stringOrEmpty(lpc);

        for (Map.Entry<String, Object> e : root.entrySet()) {
            String key = e.getKey();
            if ("lastPlayedCharacter".equalsIgnoreCase(key)) continue;
            if (!(e.getValue() instanceof Map<?, ?> section)) continue;

            boolean enabled = boolOrDefault(section.get("enabled"), true);
            String name = stringOrEmpty(section.get("name"));
            String skinId = stringOrEmpty(section.get("skinId"));
            String skin = stringOrEmpty(section.get("skin"));
            String skinUUID = stringOrEmpty(section.get("skinUUID"));
            String texture = stringOrEmpty(section.get("texture"));
            String signature = stringOrEmpty(section.get("signature"));
            boolean slim = boolOrDefault(section.get("slim"), false);
            String tags = normalizeTags(section.get("tags"));
            String gender = stringOrEmpty(section.get("gender"));

            characterMap.put(key, new CharacterData(key, enabled, name, skinId, skin, skinUUID, texture, signature, slim, tags, gender));
        }
    }

    private void saveCharacter(CharacterData character) {
        Map<String, Object> root = YamlFiles.load(playerFile, platform.logger());
        if (root == null) {
            platform.logger().severe("Cannot save character -- file is unreadable: " + playerFile.getAbsolutePath());
            return;
        }

        Object sectionObj = root.get(character.uuid());
        Map<String, Object> section = new LinkedHashMap<>();
        if (sectionObj instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null) section.put(String.valueOf(e.getKey()), e.getValue());
            }
        }

        section.put("enabled", character.enabled());
        section.put("name", character.name());
        section.put("slim", character.slim());
        section.put("tags", character.tags());
        section.put("gender", Gender.toConfigKeyNullable(character.gender()));

        if (character.usesSkinIdFormat()) {
            section.put("skinId", character.skinId());
            section.remove("skin");
            section.remove("skinUUID");
            section.remove("texture");
            section.remove("signature");
        } else {
            section.remove("skinId");
            section.put("skin", character.skin());
            section.put("skinUUID", character.skinUUID());
            section.put("texture", character.texture());
            section.put("signature", character.signature());
        }

        root.put(character.uuid(), section);
        root.put("lastPlayedCharacter", lastPlayedCharacter == null ? "" : lastPlayedCharacter);
        YamlFiles.save(playerFile, root, platform.logger());
    }

    private void saveLastPlayed() {
        Map<String, Object> root = YamlFiles.load(playerFile, platform.logger());
        if (root == null) {
            platform.logger().severe("Cannot save lastPlayedCharacter -- file is unreadable: " + playerFile.getAbsolutePath());
            return;
        }
        root.put("lastPlayedCharacter", lastPlayedCharacter == null ? "" : lastPlayedCharacter);
        YamlFiles.save(playerFile, root, platform.logger());
    }

    public void loadCharacter(ProxyServerConnection server, ProxyMessenger messenger, String characterUUID, boolean forced) {
        storeCharacters();
        CharacterData character = characterMap.get(characterUUID);
        if (character == null) {
            platform.logger().severe("Attempted to load a character that does not exist [" + playerUUID + ", " + characterUUID + "]");
            return;
        }

        messenger.send(server, "loadCharacter", playerUUID, character, forced);
        CharacterSkinWire.Resolved resolved = CharacterSkinWire.resolve(character, repo.globalSkinCache(), platform.logger());
        if (resolved.skin().isEmpty() && !character.usesSkinIdFormat()) {
            messenger.send(server, "defaultSkin", playerUUID, character.uuid());
        }

        if (!characterUUID.equals(lastPlayedCharacter)) {
            lastPlayedCharacter = characterUUID;
            saveLastPlayed();
        }

        updateCharacterList(server, messenger);
    }

    public void loadTempCharacter(ProxyServerConnection server, ProxyMessenger messenger, String requesterUUID, String characterUUID) {
        storeCharacters();
        CharacterData character = characterMap.get(characterUUID);
        if (character == null) {
            messenger.send(server, "loadTempCharacterFailed", requesterUUID, characterUUID);
            return;
        }
        messenger.send(server, "loadTempCharacter", requesterUUID, character, playerUUID);
    }

    public void loadLastPlayedCharacter(ProxyServerConnection server, ProxyMessenger messenger) {
        loadCharacter(server, messenger, lastPlayedCharacter, true);
    }

    public CharacterData getCharacter(String characterUUID) {
        storeCharacters();
        return characterMap.get(characterUUID);
    }

    public String createNewCharacter(String name) {
        return createNewCharacter(UUID.randomUUID().toString(), name, "", "", false);
    }

    public String createNewCharacter(String uuid, String name, String skin, String skinUUID, boolean slim) {
        storeCharacters();
        CharacterData character = new CharacterData(uuid, true, name, "", skin, skinUUID, "", "", slim, "", "");
        characterMap.put(character.uuid(), character);
        saveCharacter(character);
        return character.uuid();
    }

    public void setCharacterSkin(String characterUUID, String skin, String skinUUID, String texture, String signature, boolean slim) {
        storeCharacters();
        CharacterData character = characterMap.get(characterUUID);
        if (character == null) {
            platform.logger().severe("Attempted to reskin missing character [" + playerUUID + ", " + characterUUID + "]");
            return;
        }
        character.slim(slim);
        migrateLegacySkinToCacheAndId(character, skin, texture, signature);
        saveCharacter(character);
    }

    public void setCharacterSkinById(String characterUUID, String skinId, boolean slim) {
        storeCharacters();
        CharacterData character = characterMap.get(characterUUID);
        if (character == null) {
            platform.logger().severe("Attempted to reskin missing character [" + playerUUID + ", " + characterUUID + "]");
            return;
        }
        if (skinId == null || skinId.isEmpty()) {
            platform.logger().warning("Refusing to set empty skinId on character " + characterUUID);
            return;
        }
        if (repo.globalSkinCache().getById(skinId).isEmpty()) {
            platform.logger().warning("skinId " + skinId + " not in global cache when saving character " + characterUUID);
        }
        character.skinId(skinId);
        character.skin("");
        character.skinUUID("");
        character.texture("");
        character.signature("");
        character.slim(slim);
        saveCharacter(character);
    }

    private void migrateLegacySkinToCacheAndId(CharacterData character, String skin, String texture, String signature) {
        String mojangUrl = skin;
        if (mojangUrl == null || mojangUrl.isEmpty()) {
            mojangUrl = SkinContentHash.mojangUrlFromTextureProperty(texture);
        }
        if (mojangUrl != null && !mojangUrl.isEmpty()) {
            mojangUrl = SkinContentHash.normalizeMojangTextureUrl(mojangUrl);
        }

        String skinId = "";
        if (texture != null && !texture.isEmpty() && signature != null && !signature.isEmpty()) {
            Optional<GlobalSkinCache.Entry> existing = mojangUrl != null && !mojangUrl.isEmpty()
                    ? repo.globalSkinCache().getByMojangUrl(mojangUrl)
                    : Optional.empty();
            if (existing.isPresent()) {
                skinId = existing.get().skinId;
            } else if (mojangUrl != null && !mojangUrl.isEmpty()) {
                try {
                    SkinImageFetcher fetcher = new SkinImageFetcher(5, 1024 * 1024);
                    String computed = SkinContentHash.skinIdFromImage(fetcher.downloadImage(mojangUrl));
                    if (computed != null) {
                        skinId = computed;
                        GlobalSkinCache.Entry entry = new GlobalSkinCache.Entry(skinId, mojangUrl, texture, signature);
                        repo.globalSkinCache().putAndIndexSourceUrl(entry, mojangUrl);
                    }
                } catch (Exception e) {
                    platform.logger().warning("Could not compute skinId during migration: " + e.getMessage());
                }
            }
        }

        if (!skinId.isEmpty()) {
            character.skinId(skinId);
            character.skin("");
            character.skinUUID("");
            character.texture("");
            character.signature("");
        } else {
            character.skinId("");
            character.skin(skin == null ? "" : skin);
            character.skinUUID("");
            character.texture(texture == null ? "" : texture);
            character.signature(signature == null ? "" : signature);
        }
    }

    public void setCharacterName(String characterUUID, String name) {
        storeCharacters();
        CharacterData character = characterMap.get(characterUUID);
        if (character == null) {
            platform.logger().severe("Attempted to rename missing character [" + playerUUID + ", " + characterUUID + "]");
            return;
        }
        character.name(name);
        saveCharacter(character);
    }

    public void setCharacterEnabled(String characterUUID, boolean enabled) {
        storeCharacters();
        CharacterData character = characterMap.get(characterUUID);
        if (character == null) {
            platform.logger().severe("Attempted to enable/disable missing character [" + playerUUID + ", " + characterUUID + "]");
            return;
        }
        character.enabled(enabled);
        saveCharacter(character);
    }

    public void setCharacterTags(String characterUUID, String tags) {
        storeCharacters();
        CharacterData character = characterMap.get(characterUUID);
        if (character == null) {
            platform.logger().severe("Attempted to tag missing character [" + playerUUID + ", " + characterUUID + "]");
            return;
        }
        character.tags(tags);
        saveCharacter(character);
    }

    public void setCharacterGender(String characterUUID, String gender) {
        storeCharacters();
        CharacterData character = characterMap.get(characterUUID);
        if (character == null) {
            platform.logger().severe("Attempted to set gender on missing character [" + playerUUID + ", " + characterUUID + "]");
            return;
        }
        character.gender(Gender.fromString(gender));
        saveCharacter(character);
    }

    public void sendEnabledCharacters(ProxyServerConnection server, ProxyMessenger messenger, String subChannel, String requesterUUID) {
        storeCharacters();
        List<CharacterData> enabledCharacters = new ArrayList<>();
        for (CharacterData c : characterMap.values()) if (c.enabled()) enabledCharacters.add(c);

        messenger.send(server, subChannel + "Start", playerUUID, requesterUUID, enabledCharacters.size());
        for (CharacterData c : enabledCharacters) messenger.send(server, subChannel + "Item", playerUUID, requesterUUID, c);
    }

    public void loadCharacterByName(ProxyServerConnection server, ProxyMessenger messenger, String characterName) {
        storeCharacters();
        for (CharacterData c : characterMap.values()) {
            if (c.enabled()
                    && !Objects.equals(lastPlayedCharacter, c.uuid())
                    && c.nameUnformatted().toLowerCase().startsWith(characterName.toLowerCase())) {
                loadCharacter(server, messenger, c.uuid(), false);
                return;
            }
        }
        messenger.send(server, "selectCharacterByNameFailed", playerUUID);
    }

    public void updateCharacterList(ProxyServerConnection server, ProxyMessenger messenger) {
        storeCharacters();
        List<String> enabledCharacterNames = new ArrayList<>();
        for (CharacterData c : characterMap.values()) {
            if (c.enabled() && !Objects.equals(lastPlayedCharacter, c.uuid())) enabledCharacterNames.add(c.nameUnformatted());
        }
        if (!enabledCharacterNames.isEmpty()) messenger.send(server, "updateCharacterList", playerUUID, enabledCharacterNames);
    }

    private static String stringOrEmpty(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static boolean boolOrDefault(Object o, boolean def) {
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) return Boolean.parseBoolean(s);
        return def;
    }

    private static String normalizeTags(Object tagsObj) {
        if (tagsObj == null) return "";
        if (tagsObj instanceof String s) return s;
        if (tagsObj instanceof List<?> list) {
            // Legacy: list of tags. Keep as a single string joined by ',' to avoid breaking downstream.
            List<String> out = new ArrayList<>();
            for (Object o : list) out.add(String.valueOf(o));
            return String.join(",", out);
        }
        return String.valueOf(tagsObj);
    }
}

