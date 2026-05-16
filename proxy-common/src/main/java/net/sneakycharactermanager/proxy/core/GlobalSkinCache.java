package net.sneakycharactermanager.proxy.core;

import net.sneakycharactermanager.proxy.common.ProxyLogger;
import net.sneakycharactermanager.proxy.common.SkinContentHash;
import net.sneakycharactermanager.proxy.common.YamlFiles;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class GlobalSkinCache {

    public static final class Entry {
        public final String skinId;
        public final String mojangTextureUrl;
        public final String texture;
        public final String signature;

        public Entry(String skinId, String mojangTextureUrl, String texture, String signature) {
            this.skinId = skinId == null ? "" : skinId;
            this.mojangTextureUrl = SkinContentHash.normalizeMojangTextureUrl(mojangTextureUrl);
            this.texture = texture != null ? texture : "";
            this.signature = signature != null ? signature : "";
        }

        public Map<String, String> toMap() {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("id", skinId);
            map.put("mojang_url", mojangTextureUrl);
            map.put("texture", texture);
            map.put("signature", signature);
            return map;
        }
    }

    private final File configFile;
    private final ProxyLogger logger;
    private final Map<String, Entry> byId = new ConcurrentHashMap<>();
    private final Map<String, String> urlToId = new ConcurrentHashMap<>();

    public GlobalSkinCache(File dataFolder, ProxyLogger logger) {
        this.configFile = new File(dataFolder, "skin_cache.yml");
        this.logger = logger;
        load();
    }

    public synchronized void load() {
        Map<String, Object> root = YamlFiles.load(configFile, logger);
        if (root == null) return;
        byId.clear();
        urlToId.clear();

        Object skinsObj = root.get("skins");
        if (!(skinsObj instanceof List<?> list)) return;

        for (Object obj : list) {
            if (!(obj instanceof Map<?, ?> item)) continue;
            String id = item.get("id") == null ? "" : String.valueOf(item.get("id"));
            if (id.isEmpty()) continue;
            Entry entry = new Entry(
                    id,
                    item.get("mojang_url") == null ? "" : String.valueOf(item.get("mojang_url")),
                    item.get("texture") == null ? "" : String.valueOf(item.get("texture")),
                    item.get("signature") == null ? "" : String.valueOf(item.get("signature"))
            );
            byId.put(id, entry);
            if (!entry.mojangTextureUrl.isEmpty()) {
                urlToId.put(entry.mojangTextureUrl, id);
            }
        }
    }

    public synchronized void save() {
        Map<String, Object> root = new LinkedHashMap<>();
        List<Map<String, String>> skinsList = new ArrayList<>();
        for (Entry entry : byId.values()) {
            skinsList.add(entry.toMap());
        }
        root.put("skins", skinsList);
        YamlFiles.save(configFile, root, logger);
    }

    public Optional<Entry> getById(String skinId) {
        if (skinId == null || skinId.isEmpty()) return Optional.empty();
        return Optional.ofNullable(byId.get(skinId));
    }

    public Optional<Entry> getByMojangUrl(String url) {
        String normalized = SkinContentHash.normalizeMojangTextureUrl(url);
        if (normalized.isEmpty()) return Optional.empty();
        String id = urlToId.get(normalized);
        if (id == null) return Optional.empty();
        return getById(id);
    }

    public synchronized void put(Entry entry) {
        if (entry == null || entry.skinId.isEmpty()) return;
        byId.put(entry.skinId, entry);
        if (!entry.mojangTextureUrl.isEmpty()) {
            urlToId.put(entry.mojangTextureUrl, entry.skinId);
        }
        save();
    }

    public synchronized void putAndIndexSourceUrl(Entry entry, String sourceUrl) {
        put(entry);
        if (SkinContentHash.isMojangTextureUrl(sourceUrl)) {
            String normalized = SkinContentHash.normalizeMojangTextureUrl(sourceUrl);
            if (!normalized.isEmpty() && !normalized.equals(entry.mojangTextureUrl)) {
                urlToId.put(normalized, entry.skinId);
            }
        }
    }
}
