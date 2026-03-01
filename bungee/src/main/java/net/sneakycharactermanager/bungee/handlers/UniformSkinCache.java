package net.sneakycharactermanager.bungee.handlers;

import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.sneakycharactermanager.bungee.SneakyCharacterManager;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UniformSkinCache {

    private static UniformSkinCache instance;
    private final File configFile;
    private Configuration config;
    private final Map<String, List<Variant>> cache = new ConcurrentHashMap<>();

    public static class Variant {
        public final String uniformHash;
        public final String skinUUID;
        public final String textureUrl;

        public Variant(String uniformHash, String skinUUID, String textureUrl) {
            this.uniformHash = uniformHash;
            this.skinUUID = skinUUID;
            this.textureUrl = textureUrl;
        }

        public Map<String, String> toMap() {
            Map<String, String> map = new HashMap<>();
            map.put("hash", uniformHash);
            map.put("uuid", skinUUID);
            map.put("url", textureUrl);
            return map;
        }
    }

    public UniformSkinCache() {
        instance = this;
        configFile = new File(SneakyCharacterManager.getInstance().getDataFolder(), "uniform_skin_cache.yml");
        load();
    }

    public static UniformSkinCache getInstance() {
        return instance;
    }

    public void load() {
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
            cache.clear();

            List<?> topList = config.getList("cache");
            if (topList != null) {
                for (Object obj : topList) {
                    if (obj instanceof Map<?, ?> entryMap) {
                        String baseUrl = (String) entryMap.get("base_url");
                        List<Map<?, ?>> variantsList = (List<Map<?, ?>>) entryMap.get("variants");
                        List<Variant> variants = new ArrayList<>();
                        if (variantsList != null) {
                            for (Map<?, ?> item : variantsList) {
                                variants.add(new Variant(
                                        (String) item.get("hash"),
                                        (String) item.get("uuid"),
                                        (String) item.get("url")
                                ));
                            }
                        }
                        cache.put(baseUrl, variants);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save() {
        // Create a fresh configuration to avoid merging with old broken data
        Configuration newConfig = new Configuration();
        
        List<Map<String, Object>> topList = new ArrayList<>();
        for (Map.Entry<String, List<Variant>> entry : cache.entrySet()) {
            Map<String, Object> entryMap = new HashMap<>();
            entryMap.put("base_url", entry.getKey());
            
            List<Map<String, String>> variantsList = new ArrayList<>();
            for (Variant v : entry.getValue()) {
                variantsList.add(v.toMap());
            }
            entryMap.put("variants", variantsList);
            topList.add(entryMap);
        }
        
        newConfig.set("cache", topList);
        config = newConfig;

        try {
            ConfigurationProvider.getProvider(YamlConfiguration.class).save(config, configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<Variant> getVariants(String baseUrl) {
        return cache.getOrDefault(baseUrl, Collections.emptyList());
    }

    public void addVariant(String baseUrl, String uniformHash, String skinUUID, String textureUrl) {
        List<Variant> variants = cache.computeIfAbsent(baseUrl, k -> new ArrayList<>());
        // Simple check to avoid duplicates
        variants.removeIf(v -> v.uniformHash.equals(uniformHash));
        variants.add(new Variant(uniformHash, skinUUID, textureUrl));
        save();
    }
}
