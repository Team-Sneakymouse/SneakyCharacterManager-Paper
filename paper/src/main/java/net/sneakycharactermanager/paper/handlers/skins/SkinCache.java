package net.sneakycharactermanager.paper.handlers.skins;

import java.util.*;

import com.destroystokyo.paper.profile.ProfileProperty;

public class SkinCache {

    private static Map<String, Map<String, ProfileProperty>> skinCache = new HashMap<String,Map<String,ProfileProperty>>();

    public static ProfileProperty get(String playerUUID, String url) {
        if (skinCache.containsKey(playerUUID)) {
            ProfileProperty p = skinCache.get(playerUUID).get(url);
            if (p != null && p.isSigned()) {
                return p;
            } else {
                skinCache.get(playerUUID).remove(url);
            }
        }
        return null;
    }

    public static void put(String playerUUID, String url, ProfileProperty profileProperty) {
        if (!skinCache.containsKey(playerUUID)) skinCache.put(playerUUID, new HashMap<String,ProfileProperty>());
        skinCache.get(playerUUID).put(url, profileProperty);
    }

    public static void remove(String playerUUID) {
        skinCache.remove(playerUUID);
    }

}
