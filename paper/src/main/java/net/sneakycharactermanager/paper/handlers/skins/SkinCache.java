package net.sneakycharactermanager.paper.handlers.skins;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.destroystokyo.paper.profile.ProfileProperty;

public class SkinCache {

    private static Map<String, Map<String, ProfileProperty>> skinCache = new ConcurrentHashMap<>();

    public static ProfileProperty get(String playerUUID, String url) {
        return skinCache.compute(playerUUID, (key, value) -> {
            if (value != null) {
                ProfileProperty p = value.get(url);
                if (p != null && p.isSigned()) {
                    return value;
                } else {
                    value.remove(url);
                }
            }
            return null;
        }).get(url);
    }

    public static void put(String playerUUID, String url, ProfileProperty profileProperty) {
        if (!skinCache.containsKey(playerUUID)) skinCache.put(playerUUID, new HashMap<String,ProfileProperty>());
        skinCache.get(playerUUID).put(url, profileProperty);
    }

    public static void remove(String playerUUID) {
        skinCache.remove(playerUUID);
    }

}
