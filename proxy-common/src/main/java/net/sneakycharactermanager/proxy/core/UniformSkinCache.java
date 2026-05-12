package net.sneakycharactermanager.proxy.core;

import net.sneakycharactermanager.proxy.common.YamlFiles;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class UniformSkinCache {

    public static final class Variant {
        public final String uniformHash;
        public final String skinUUID;
        public final String textureUrl;
        public final String texture;
        public final String signature;

        public Variant(String uniformHash, String skinUUID, String textureUrl, String texture, String signature) {
            this.uniformHash = uniformHash;
            this.skinUUID = skinUUID;
            this.textureUrl = textureUrl;
            this.texture = texture != null ? texture : "";
            this.signature = signature != null ? signature : "";
        }

        public Map<String, String> toMap() {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("hash", uniformHash);
            map.put("uuid", skinUUID);
            map.put("url", textureUrl);
            map.put("texture", texture);
            map.put("signature", signature);
            return map;
        }
    }

    private final File configFile;
    private final Map<String, List<Variant>> cache = new ConcurrentHashMap<>();

    public UniformSkinCache(File dataFolder) {
        this.configFile = new File(dataFolder, "uniform_skin_cache.yml");
        load();
    }

    public synchronized void load() {
        Map<String, Object> root = YamlFiles.loadOrEmpty(configFile);
        cache.clear();

        Object top = root.get("cache");
        if (!(top instanceof List<?> list)) return;

        for (Object obj : list) {
            if (!(obj instanceof Map<?, ?> entryMap)) continue;
            String baseUrl = entryMap.get("base_url") == null ? null : String.valueOf(entryMap.get("base_url"));
            if (baseUrl == null || baseUrl.isEmpty()) continue;

            Object variantsObj = entryMap.get("variants");
            List<Variant> variants = new ArrayList<>();
            if (variantsObj instanceof List<?> vList) {
                for (Object v : vList) {
                    if (!(v instanceof Map<?, ?> item)) continue;
                    variants.add(new Variant(
                            item.get("hash") == null ? "" : String.valueOf(item.get("hash")),
                            item.get("uuid") == null ? "" : String.valueOf(item.get("uuid")),
                            item.get("url") == null ? "" : String.valueOf(item.get("url")),
                            item.get("texture") == null ? "" : String.valueOf(item.get("texture")),
                            item.get("signature") == null ? "" : String.valueOf(item.get("signature"))
                    ));
                }
            }

            cache.put(baseUrl, variants);
        }
    }

    public synchronized void save() {
        Map<String, Object> root = new LinkedHashMap<>();
        List<Map<String, Object>> topList = new ArrayList<>();

        for (Map.Entry<String, List<Variant>> entry : cache.entrySet()) {
            Map<String, Object> entryMap = new LinkedHashMap<>();
            entryMap.put("base_url", entry.getKey());

            List<Map<String, String>> variantsList = new ArrayList<>();
            for (Variant v : entry.getValue()) variantsList.add(v.toMap());
            entryMap.put("variants", variantsList);
            topList.add(entryMap);
        }

        root.put("cache", topList);
        YamlFiles.save(configFile, root);
    }

    public List<Variant> getVariants(String baseUrl) {
        return cache.getOrDefault(baseUrl, Collections.emptyList());
    }

    public synchronized void addVariant(String baseUrl, String uniformHash, String skinUUID, String textureUrl, String texture, String signature) {
        List<Variant> variants = cache.computeIfAbsent(baseUrl, _k -> new ArrayList<>());
        variants.removeIf(v -> v.uniformHash.equals(uniformHash));
        variants.add(new Variant(uniformHash, skinUUID, textureUrl, texture, signature));
        save();
    }
}

