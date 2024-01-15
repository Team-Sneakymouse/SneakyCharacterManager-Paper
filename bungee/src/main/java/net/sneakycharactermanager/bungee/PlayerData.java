package net.sneakycharactermanager.bungee;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.sneakycharactermanager.bungee.util.PaperMessagingUtil;

public class PlayerData {

    private static final Map<String, PlayerData> playerDataMap = new ConcurrentHashMap<>();

    private final String playerUUID;
    private Configuration config;
    private final File playerFile;
    private String lastPlayedCharacter;
    private final Map<String, Character> characterMap = new LinkedHashMap<>();

    private PlayerData(String playerUUID) {
        this.playerUUID = playerUUID;
        playerFile = new File(SneakyCharacterManager.getCharacterDataFolder(), playerUUID + ".yml");


        //Loading YML Configuration
        try {
            if (!playerFile.exists()) {
                if (!playerFile.createNewFile()) {
                    throw new IOException("Failed to create player data file!");
                }
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
        if (!loadConfig()) {
            SneakyCharacterManager.getInstance().getLogger().severe("Failed to load config! (Player Data Constructor)");
            return;
        }

        this.lastPlayedCharacter = this.config.getString("lastPlayedCharacter");

        if (this.lastPlayedCharacter == null || lastPlayedCharacter.isEmpty()) {
            String firstCharacterUUID = UUID.randomUUID().toString();
            this.config.set("lastPlayedCharacter", firstCharacterUUID);
            this.lastPlayedCharacter = firstCharacterUUID;

            Configuration section = this.config.getSection(firstCharacterUUID);

            section.set("enabled", true);
            section.set("name", ProxyServer.getInstance().getPlayer(UUID.fromString(playerUUID)).getName());
            section.set("skin", "");
            section.set("slim", false);
            section.set("tags", new ArrayList<String>());
            saveConfig();
        }

        storeCharacters();
    }

    private void storeCharacters() {
        if (!loadConfig()) {
            SneakyCharacterManager.getInstance().getLogger().severe("Failed to load config! (Store Characters)");
            return;
        }
        characterMap.clear();
//        Iterator<String> iterator = this.config.getKeys().iterator();
//        while (iterator.hasNext()) {
//            String key = iterator.next();
//            if (key.equalsIgnoreCase("lastPlayedCharacter")) continue;
//
//            Character character = new Character(key, this.config.getSection(key));
//            this.characterMap.put(key, character);
//        }
        for(String key : config.getKeys()){
            if(key.equalsIgnoreCase("lastPlayedCharacter")) continue;
            Character character = new Character(key, this.config.getSection(key));
            this.characterMap.put(key, character);
        }
    }

    private boolean loadConfig() {
        try {
            this.config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(playerFile);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void saveConfig() {
        try {
            ConfigurationProvider.getProvider(YamlConfiguration.class).save(this.config, playerFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadCharacter(ServerInfo serverInfo, String characterUUID, boolean forced) {
        storeCharacters();
        Character character = this.characterMap.get(characterUUID);
        if (!loadConfig()) {
            SneakyCharacterManager.getInstance().getLogger().severe("Failed to load config! (Load Character)");
            return;
        }

        if (character == null) {
            SneakyCharacterManager.getInstance().getLogger().severe("An attempt was made to load a character that does not exist! [" + this.playerUUID + ", " + characterUUID + "]");
            return;
        } else {
            character.loadCharacter("loadCharacter", serverInfo, this.playerUUID, forced);
        }

        if (!this.lastPlayedCharacter.equals(characterUUID)) {
            this.lastPlayedCharacter = characterUUID;

            this.config.set("lastPlayedCharacter", characterUUID);
            saveConfig();
        }

        this.updateCharacterList(serverInfo);
    }

    public void loadLastPlayedCharacter(ServerInfo serverInfo) {
        this.loadCharacter(serverInfo, this.lastPlayedCharacter, true);
    }

    public static synchronized PlayerData get(String playerUUID) {
        return playerDataMap.computeIfAbsent(playerUUID, key -> new PlayerData(playerUUID));
    }

    public static synchronized void remove(String playerUUID) {
        playerDataMap.remove(playerUUID);
    }

    public Character getCharacter(String characterUUID) {
        return characterMap.get(characterUUID);
    }

    public String createNewCharacter(String name) {
        if (!loadConfig()){
            SneakyCharacterManager.getInstance().getLogger().severe("Failed to load config! (Create new Character)");
            return null;
        }
        Character character = new Character(name);

        this.characterMap.put(character.getUUID(), character);

        Configuration section = this.config.getSection(character.getUUID());
        section.set("enabled", character.isEnabled());
        section.set("name", character.getName());
        section.set("skin", character.getSkin());
        section.set("slim", character.isSlim());
        section.set("tags", character.getTags());

        saveConfig();

        return character.getUUID();
    }

    private void updateCharacterInYaml(Character character) {
        if (!loadConfig()){
            SneakyCharacterManager.getInstance().getLogger().severe("Failed to load config! (Update Charaqcter in YAML)");
            return;
        }
        Configuration section = this.config.getSection(character.getUUID());

        section.set("enabled", character.isEnabled());
        section.set("name", character.getName());
        section.set("skin", character.getSkin());
        section.set("slim", character.isSlim());
        section.set("tags", character.getTags());

        this.config.set(character.getUUID(), section);

        saveConfig();
    }

    public void setCharacterSkin(String characterUUID, String skin, boolean slim) {
        Character character = this.characterMap.get(characterUUID);

        if (character == null) {
            SneakyCharacterManager.getInstance().getLogger().severe("An attempt was made to reskin a character that does not exist! [" + this.playerUUID + ", " + characterUUID + "]");
        } else {
            character.setSkin(skin);
            character.setSlim(slim);
            this.characterMap.put(characterUUID, character);
            this.updateCharacterInYaml(character);
        }
    }

    public void setCharacterName(String characterUUID, String name) {
        Character character = this.characterMap.get(characterUUID);

        if (character == null) {
            SneakyCharacterManager.getInstance().getLogger().severe("An attempt was made to rename a character that does not exist! [" + this.playerUUID + ", " + characterUUID + "]");
        } else {
            character.setName(name);
            this.characterMap.put(characterUUID, character);
            this.updateCharacterInYaml(character);
        }
    }

    public void setCharacterEnabled(String characterUUID, boolean enabled) {
        Character character = this.characterMap.get(characterUUID);

        if (character == null) {
            SneakyCharacterManager.getInstance().getLogger().severe("An attempt was made to enable/disable a character that does not exist! [" + this.playerUUID + ", " + characterUUID + "]");
        } else {
            character.setEnabled(enabled);
            this.characterMap.put(characterUUID, character);
            this.updateCharacterInYaml(character);
        }
    }

    public void setCharacterTags(String characterUUID, List<String> tags) {
        Character character = this.characterMap.get(characterUUID);

        if (character == null) {
            SneakyCharacterManager.getInstance().getLogger().severe("An attempt was made to tag a character that does not exist! [" + this.playerUUID + ", " + characterUUID + "]");
        } else {
            character.setTags(tags);
            this.characterMap.put(characterUUID, character);
            this.updateCharacterInYaml(character);
        }
    }

    public void sendEnabledCharacters(ServerInfo serverInfo, String subChannel, String requesterUUID) {
        storeCharacters();
        List<Character> enabledCharacters = new ArrayList<>();

        for (Character character: characterMap.values()) {
            if (character.isEnabled()) {
                enabledCharacters.add(character);
            }
        }

        PaperMessagingUtil.sendByteArray(serverInfo, subChannel, this.playerUUID, requesterUUID, enabledCharacters);
    }

    public String getLastPlayedCharacter() {
        return lastPlayedCharacter;
    }

    public void rebuildCharacterMap(ServerInfo serverInfo) {
        Character character = this.characterMap.get(this.lastPlayedCharacter);

        if (character == null) {
            SneakyCharacterManager.getInstance().getLogger().severe("An attempt was made to load a character that does not exist! [" + this.playerUUID + ", " + this.lastPlayedCharacter + "]");
        } else {
            character.loadCharacter("rebuildCharacterMap", serverInfo, this.playerUUID, false);
        }
    }

    public void loadCharacterByName(ServerInfo serverInfo, String characterName) {
        storeCharacters();

        for (Character character: characterMap.values()) {
            if (character.isEnabled() && !this.lastPlayedCharacter.equals(character.getUUID()) && character.getNameUnformatted().toLowerCase().startsWith(characterName.toLowerCase())) {
                loadCharacter(serverInfo, character.getUUID(), false);
                return;
            }
        }

        PaperMessagingUtil.sendByteArray(serverInfo, "selectCharacterByNameFailed", this.playerUUID);
    }

    public void updateCharacterList(ServerInfo serverInfo) {
        storeCharacters();
        List<String> enabledCharacterNames = new ArrayList<>();
    
        Iterator<Map.Entry<String, Character>> iterator = characterMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Character> entry = iterator.next();
            Character character = entry.getValue();
    
            if (character.isEnabled() && !this.lastPlayedCharacter.equals(character.getUUID())) {
                enabledCharacterNames.add(character.getNameUnformatted());
            }
        }
    
        if (enabledCharacterNames.size() > 0) PaperMessagingUtil.sendByteArray(serverInfo, "updateCharacterList", this.playerUUID, enabledCharacterNames);
    }

}
