package net.sneakycharactermanager.bungee;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;

public class PlayerData {

    private static Map<String, PlayerData> playerDataMap = new HashMap<String, PlayerData>();

    private String playerUUID;
    private String lastPlayedCharacter;
    private Map<String, Character> characterMap = new HashMap<String, Character>();
    
    public PlayerData(String playerUUID) {
        this.playerUUID = playerUUID;
        File playerFile = new File(SneakyCharacterManager.getCharacterDataFolder(), playerUUID + ".yml");

        if (!playerFile.exists()) {
            Map<String, Object> defaultData = new LinkedHashMap<>();

            String firstCharacterUUID = UUID.randomUUID().toString();
            defaultData.put("lastPlayedCharacter", firstCharacterUUID);

            Map<String, Object> playerCharacterData = new LinkedHashMap<>();
            playerCharacterData.put("enabled", true);
            playerCharacterData.put("name", ProxyServer.getInstance().getPlayer(UUID.fromString(playerUUID)).getName());
            playerCharacterData.put("skin", "");

            defaultData.put(firstCharacterUUID, playerCharacterData);

            DumperOptions dumperOptions = new DumperOptions();
            dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            Yaml yaml = new Yaml(dumperOptions);

            String yamlContent = yaml.dump(defaultData);

            try (FileWriter writer = new FileWriter(playerFile)) {
                writer.write(yamlContent);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try (FileReader reader = new FileReader(playerFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> yamlData = yaml.load(reader);

            this.lastPlayedCharacter = (String) yamlData.get("lastPlayedCharacter");

            yamlData.keySet().stream()
            .filter(key -> !key.equals("lastPlayedCharacter"))
            .forEach(key -> {
                Character character = new Character(key, (Map<String, Object>) yamlData.get(key));

                this.characterMap.put(key, character);
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadCharacter(ServerInfo serverInfo, String characterUUID) {
        Character character = this.characterMap.get(characterUUID);

        if (character == null) {
            SneakyCharacterManager.getInstance().getLogger().severe("An attempt was made to load a character that does not exist! [" + this.playerUUID + ", " + characterUUID + "]");
            return;
        } else {
            character.loadCharacter(serverInfo, this.playerUUID);
        }

        if (this.lastPlayedCharacter.equals(characterUUID)) {
            this.lastPlayedCharacter = characterUUID;
            File playerFile = new File(SneakyCharacterManager.getCharacterDataFolder(), playerUUID + ".yml");

            Yaml yaml = new Yaml();
            Map<String, Object> yamlData;
        
            try (FileReader reader = new FileReader(playerFile)) {
                yamlData = yaml.load(reader);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        
            yamlData.put("lastPlayedCharacter", characterUUID);
        
            try (FileWriter writer = new FileWriter(playerFile)) {
                yaml.dump(yamlData, writer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void loadLastPlayedCharacter(ServerInfo serverInfo) {
        this.loadCharacter(serverInfo, this.lastPlayedCharacter);
    }

    public static PlayerData get(String playerUUID) {
        if (playerDataMap.containsKey(playerUUID)) {
            return playerDataMap.get(playerUUID);
        } else {
            PlayerData playerData = new PlayerData(playerUUID);
            playerDataMap.put(playerUUID, playerData);
            return playerData;
        }
    }

    public void createNewCharacter(String name, String skin) {
        Character character = new Character(name, skin);

        this.characterMap.put(character.getUUID(), character);

        File playerFile = new File(SneakyCharacterManager.getCharacterDataFolder(), playerUUID + ".yml");

        Map<String, Object> yamlData;
        if (playerFile.exists()) {
            Yaml yaml = new Yaml();
            try (FileReader reader = new FileReader(playerFile)) {
                yamlData = yaml.load(reader);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        } else {
            SneakyCharacterManager.getInstance().getLogger().severe("An attempt was made to access character data that doesn't exist! [" + this.playerUUID + "]");
            return;
        }

        Map<String, Object> characterData = new LinkedHashMap<>();
        characterData.put("enabled", character.isEnabled());
        characterData.put("name", character.getName());
        characterData.put("skin", character.getSkin());

        yamlData.put(character.getUUID(), characterData);
        
        try (FileWriter writer = new FileWriter(playerFile)) {
            Yaml yaml = new Yaml();
            yaml.dump(yamlData, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateCharacterInYaml(Character character) {
        File playerFile = new File(SneakyCharacterManager.getCharacterDataFolder(), this.playerUUID + ".yml");
    
        Map<String, Object> yamlData;
        if (playerFile.exists()) {
            Yaml yaml = new Yaml();
            try (FileReader reader = new FileReader(playerFile)) {
                yamlData = yaml.load(reader);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        } else {
            yamlData = new HashMap<>();
        }
    
        if (yamlData.containsKey(character.getUUID())) {
            Map<String, Object> characterData = (Map<String, Object>) yamlData.get(character.getUUID());
            characterData.put("enabled", character.isEnabled());
            characterData.put("name", character.getName());
            characterData.put("skin", character.getSkin());
            yamlData.put(character.getUUID(), characterData);
        } else {
            SneakyCharacterManager.getInstance().getLogger().severe("Character not found in YAML data! [" + this.playerUUID + ", " + character.getUUID() + "]");
            return;
        }
    
        try (FileWriter writer = new FileWriter(playerFile)) {
            Yaml yaml = new Yaml();
            yaml.dump(yamlData, writer);
        } catch (IOException e) {
            e.printStackTrace();
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

    public void setCharacterSkin(String characterUUID, String skin) {
        Character character = this.characterMap.get(characterUUID);

        if (character == null) {
            SneakyCharacterManager.getInstance().getLogger().severe("An attempt was made to reskin a character that does not exist! [" + this.playerUUID + ", " + characterUUID + "]");
        } else {
            character.setSkin(skin);
            this.characterMap.put(characterUUID, character);
            this.updateCharacterInYaml(character);
        }
    }

}
