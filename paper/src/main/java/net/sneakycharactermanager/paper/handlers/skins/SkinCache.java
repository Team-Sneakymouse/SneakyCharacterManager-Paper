package net.sneakycharactermanager.paper.handlers.skins;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.Nullable;

import com.destroystokyo.paper.profile.ProfileProperty;

public class SkinCache {

    private static ConcurrentMap<String, ConcurrentMap<String, ProfileProperty>> skinCache = new ConcurrentHashMap<>();

    @Nullable
    public static ProfileProperty get(String playerUUID, String url) {
        ConcurrentMap<String, ProfileProperty> cachedProperties = skinCache.get(playerUUID);

        if (cachedProperties == null) return getFromAll(playerUUID, url);

        ProfileProperty profileProperty = cachedProperties.get(url);

        if (profileProperty == null) return getFromAll(playerUUID, url);

        if (profileProperty.isSigned()) return profileProperty;
        else {
            cachedProperties.remove(url);
            return getFromAll(playerUUID, url);
        }
    }

    private static ProfileProperty getFromAll(String playerUUID, String url) {
        for (String uuid : skinCache.keySet()) {
            if (uuid.equals(playerUUID)) continue;

            ConcurrentMap<String, ProfileProperty> cachedProperties = skinCache.get(uuid);

            if (cachedProperties == null) continue;

            ProfileProperty profileProperty = cachedProperties.get(url);
    
            if (profileProperty == null) continue;
    
            if (profileProperty.isSigned()) {
                put(playerUUID, url, profileProperty);
                return profileProperty;
            } else {
                cachedProperties.remove(url);
                continue;
            }
        }
        return null;
    }

    public static void put(String playerUUID, String url, ProfileProperty profileProperty) {
        ConcurrentMap<String, ProfileProperty> cachedProperties = skinCache.computeIfAbsent(playerUUID, key -> new ConcurrentHashMap<>());
        cachedProperties.put(url, profileProperty);
    }

    public static void remove(String playerUUID) {
        skinCache.remove(playerUUID);
    }
}
