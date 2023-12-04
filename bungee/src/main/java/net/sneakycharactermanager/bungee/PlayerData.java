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

                characterMap.put(key, character);
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadCharacter(ServerInfo serverInfo, String characterUUID) {
        Character character = characterMap.get(characterUUID);

        if (character == null) {

        } else {
            character.loadCharacter(serverInfo, this.playerUUID);
        }
    }

    public void loadLastPlayedCharacter(ServerInfo serverInfo) {
        loadCharacter(serverInfo, this.lastPlayedCharacter);
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

}
