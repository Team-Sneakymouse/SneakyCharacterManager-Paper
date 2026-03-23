package net.sneakycharactermanager.paper.util;

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

public class SkinUtil {
    
    public static PlayerProfile handleCachedSkin(OfflinePlayer player, ProfileProperty profileProperty) {
        PlayerProfile playerProfile = player.getPlayerProfile();
        playerProfile.removeProperty("textures");
    
        if (profileProperty == null) {
            if(player.isOnline()){
                ((Player)player).sendMessage(ChatUtility.convertToComponent("&4Failed to load skin! Something went wrong!"));
            }
            return null;
        } else {
            playerProfile.setProperty(profileProperty);
            return playerProfile;
        }
    }

	public static void applySkin(Player player, ProfileProperty profileProperty) {
		player.setPlayerProfile(handleCachedSkin(player, profileProperty));

		// Remove player from vehicle and teleport them up slightly to prevent them from getting stuck
		Entity vehicle = player.getVehicle();
		if (vehicle != null) vehicle.removePassenger(player);
		player.teleport(player.getLocation().add(0, 1, 0));

		// Hide the player from all players, and then show the player again. This forces client-sided mods that use skin-encoded
		// settings to do a re-check for this player (such as EntityTextureFeatures)
		for (Player p : Bukkit.getOnlinePlayers()) {
			p.hidePlayer(SneakyCharacterManager.getInstance(), player);
		}
		Bukkit.getScheduler().runTaskLater(SneakyCharacterManager.getInstance(), () -> {
			for (Player p : Bukkit.getOnlinePlayers()) {
				p.showPlayer(SneakyCharacterManager.getInstance(), player);
			}
		}, 1);
	}

	public static String getTextureUrl(ProfileProperty property) {
		if (property == null || !property.getName().equals("textures")) return null;
		try {
			String decoded = new String(Base64.getDecoder().decode(property.getValue()), StandardCharsets.UTF_8);
			JsonObject json = JsonParser.parseString(decoded).getAsJsonObject();
			return json.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
		} catch (Exception e) {
			return null;
		}
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
