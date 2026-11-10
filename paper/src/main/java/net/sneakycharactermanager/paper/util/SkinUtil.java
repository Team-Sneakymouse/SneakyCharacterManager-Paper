package net.sneakycharactermanager.paper.util;

import com.sneakyutils.util.TextUtility;
import net.sneakycharactermanager.paper.handlers.character.CharacterSkinAppliedEvent;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.Bukkit;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import org.jetbrains.annotations.Nullable;

public class SkinUtil {

	private static final String MOJANG_TEXTURE_PREFIX = "http://textures.minecraft.net/texture/";

	public static boolean isMojangTextureUrl(String url) {
		if (url == null || url.isEmpty()) return false;
		return url.startsWith(MOJANG_TEXTURE_PREFIX)
				|| url.startsWith("https://textures.minecraft.net/texture/");
	}

	public static String normalizeMojangTextureUrl(String url) {
		if (url == null) return "";
		String trimmed = url.trim();
		if (trimmed.startsWith("https://textures.minecraft.net/texture/")) {
			return MOJANG_TEXTURE_PREFIX + trimmed.substring("https://textures.minecraft.net/texture/".length());
		}
		return trimmed;
	}

    public static PlayerProfile handleCachedSkin(OfflinePlayer player, ProfileProperty profileProperty) {
        PlayerProfile playerProfile = player.getPlayerProfile();
        playerProfile.removeProperty("textures");
    
        if (profileProperty == null) {
            if(player.isOnline()){
                ((Player)player).sendMessage(TextUtility.INSTANCE.convertToComponent("&4Failed to load skin! Something went wrong!"));
            }
            return null;
        } else {
            playerProfile.setProperty(profileProperty);
            return playerProfile;
        }
    }

	public static void applySkin(Player player, ProfileProperty profileProperty) {
		if (!skinAllowed(player, profileProperty)) return;
		putSkin(player, profileProperty);

		// Remove player from vehicle and teleport them up slightly to prevent them from getting stuck
		Entity vehicle = player.getVehicle();
		if (vehicle != null) vehicle.removePassenger(player);
		player.teleport(player.getLocation().add(0, 1, 0));
	}

	/**
	 * Fires {@link CharacterSkinAppliedEvent} for putting {@code profileProperty} on {@code player}; false if a
	 * listener cancelled it. {@link #applySkin} asks this first. A swap asks it for both bodies before anything moves,
	 * so a veto refuses the whole swap, and puts the skins on later with {@link #putSkin}.
	 */
	public static boolean skinAllowed(Player player, ProfileProperty profileProperty) {
		CharacterSkinAppliedEvent event = new CharacterSkinAppliedEvent(player, profileProperty);
		Bukkit.getPluginManager().callEvent(event);
		return !event.isCancelled();
	}

	/**
	 * Sets {@code player}'s profile to wear {@code profileProperty}, where it stands: no event (see
	 * {@link #skinAllowed}), and none of {@link #applySkin}'s dismount and +1 Y teleport. Null wears no
	 * {@code textures} property (the client's default skin).
	 */
	public static void putSkin(Player player, @Nullable ProfileProperty profileProperty) {
		if (profileProperty != null) {
			player.setPlayerProfile(handleCachedSkin(player, profileProperty));
			return;
		}
		PlayerProfile profile = player.getPlayerProfile();
		profile.removeProperty("textures");
		player.setPlayerProfile(profile);
	}

	/** The signed {@code textures} property {@code player} wears right now, or null if it wears none. */
	@Nullable
	public static ProfileProperty wornTextures(Player player) {
		for (ProfileProperty property : player.getPlayerProfile().getProperties()) {
			if (property.getName().equals("textures")) return property;
		}
		return null;
	}

	public static String getTextureUrl(ProfileProperty property) {
		if (property == null || !property.getName().equals("textures")) return null;
		try {
			JsonObject skin = skinJsonFromProperty(property);
			if (skin == null) return null;
			return skin.get("url").getAsString();
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Reads {@code textures.SKIN.metadata.model} from a signed texture property.
	 * Missing {@code metadata} or {@code model} means classic (not slim).
	 *
	 * @param fallbackIfUnparseable used when the property cannot be decoded
	 */
	public static boolean isSlimModel(ProfileProperty property, boolean fallbackIfUnparseable) {
		try {
			JsonObject skin = skinJsonFromProperty(property);
			if (skin == null) return fallbackIfUnparseable;
			if (!skin.has("metadata") || skin.get("metadata").isJsonNull()) return false;
			JsonObject metadata = skin.getAsJsonObject("metadata");
			if (!metadata.has("model") || metadata.get("model").isJsonNull()) return false;
			return "slim".equalsIgnoreCase(metadata.get("model").getAsString());
		} catch (Exception e) {
			return fallbackIfUnparseable;
		}
	}

	@Nullable
	private static JsonObject skinJsonFromProperty(ProfileProperty property) {
		if (property == null || !property.getName().equals("textures")) return null;
		String decoded = new String(Base64.getDecoder().decode(property.getValue()), StandardCharsets.UTF_8);
		JsonObject json = JsonParser.parseString(decoded).getAsJsonObject();
		JsonObject textures = json.getAsJsonObject("textures");
		if (textures == null || !textures.has("SKIN")) return null;
		return textures.getAsJsonObject("SKIN");
	}

	public static String getFileHash(File file) {
		try (FileInputStream fis = new FileInputStream(file)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] byteArray = new byte[8192];
			int bytesCount;
			while ((bytesCount = fis.read(byteArray)) != -1) {
				digest.update(byteArray, 0, bytesCount);
			}
			byte[] bytes = digest.digest();
			StringBuilder sb = new StringBuilder();
			for (byte b : bytes) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			return null;
		}
	}

}
