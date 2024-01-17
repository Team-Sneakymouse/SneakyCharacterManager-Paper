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

        if (cachedProperties == null) return null;

        ProfileProperty profileProperty = cachedProperties.get(url);

        if (profileProperty == null) return null;

        if (profileProperty.isSigned()) return profileProperty;
        else {
            cachedProperties.remove(url);
            return null;
        }
    }

    public static void put(String playerUUID, String url, ProfileProperty profileProperty) {
        ConcurrentMap<String, ProfileProperty> cachedProperties = skinCache.computeIfAbsent(playerUUID, key -> new ConcurrentHashMap<>());
        cachedProperties.put(url, profileProperty);
    }

    public static void remove(String playerUUID) {
        skinCache.remove(playerUUID);
    }
}
