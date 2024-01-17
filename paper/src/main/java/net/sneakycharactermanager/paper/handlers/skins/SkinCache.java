package net.sneakycharactermanager.paper.handlers.skins;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.destroystokyo.paper.profile.ProfileProperty;

public class SkinCache {

    private static Map<String, Map<String, ProfileProperty>> skinCache = new ConcurrentHashMap<>();

    @Nullable
    public synchronized static ProfileProperty get(String playerUUID, String url) {
        Map<String, ProfileProperty> cachedProperties = skinCache.get(playerUUID);

        if (cachedProperties == null) return null;

        ProfileProperty profileProperty = cachedProperties.get(url);

        if (profileProperty == null) return null;

        if (profileProperty.isSigned()) return profileProperty;
        else {
            cachedProperties.remove(url);
            return null;
        }
    }

    public synchronized static void put(String playerUUID, String url, ProfileProperty profileProperty) {
        Map<String, ProfileProperty> cachedProperties = skinCache.computeIfAbsent(playerUUID, key -> new HashMap<String,ProfileProperty>());
        cachedProperties.put(url, profileProperty);
    }

    public synchronized static void remove(String playerUUID) {
        skinCache.remove(playerUUID);
    }

}
