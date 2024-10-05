package net.sneakycharactermanager.paper.handlers.character;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import javax.imageio.ImageIO;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerTextures;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.consolecommands.ConsoleCommandCharTemp;
import net.sneakycharactermanager.paper.handlers.skins.SkinCache;
import net.sneakycharactermanager.paper.handlers.skins.SkinData;
import net.sneakycharactermanager.paper.util.SkinUtil;

public class CharacterLoader {

	public static boolean loadCharacter(Character character) {
		String url = character.getSkin();
		Player player = character.getPlayer();

		LoadCharacterEvent event = new LoadCharacterEvent(
				player,
				character.isFirstLoad(),
				character.getCharacterUUID(),
				character.getName(),
				url,
				character.isSlim(),
				character.getTagsAsString());

		Bukkit.getServer().getPluginManager().callEvent(event);

		if (!event.isCancelled()) {
			ConsoleCommandCharTemp.playerTempCharRemove(player.getUniqueId().toString());

			ProfileProperty profileProperty = SkinCache.get(player.getUniqueId().toString(), url);

			character.setFirstLoad(false);

			if (profileProperty == null) {
				if (!shouldSkipLoading(character)) {
					SkinData.getOrCreate(url, character.isSlim(), 2, player);
				}
			} else {
				player.setPlayerProfile(SkinUtil.handleCachedSkin(player, profileProperty));
			}

			SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(player, character.getName());

			return true;
		}
		return false;
	}

	private static boolean shouldSkipLoading(Character character) {
		String url = character.getSkin();

		if (url == null || url.isEmpty() || !url.startsWith("http")) {
			if (url != null && !url.isEmpty()) {
				SneakyCharacterManager.getInstance().getLogger()
						.warning("Invalid Skin URL Received? Was this our fault?");
			}

			if (character.getName() == null || character.getName().isEmpty()) {
				return true;
			}

			return true;
		}
		return false;
	}

	public static void updateSkin(Player player, String url, Boolean slim) {
		PlayerProfile playerProfile = player.getPlayerProfile();

		if (slim == null) {
			Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (s) -> {
				checkSlimThenSetSkin(url,
						playerProfile.getTextures().getSkinModel().equals(PlayerTextures.SkinModel.SLIM), player);
			});
		} else {
			SkinData.getOrCreate(url, slim, 3, player);
		}
	}

	private static void checkSlimThenSetSkin(String url, boolean slim, Player player) {
		try {
			HttpClient httpClient = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder().uri(
					new URI(url.replace("imgur", "filmot")))
					.timeout(Duration.ofSeconds(2))
					.build();
			HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

			// Check the HTTP response status
			int statusCode = response.statusCode();

			if (statusCode == 200) {
				try (InputStream inputStream = response.body()) {
					if (inputStream != null) {
						BufferedImage image = ImageIO.read(inputStream);

						if (image != null) {
							int pixel = image.getRGB(55, 20);
							int alpha = (pixel >> 24) & 0xFF;
							slim = alpha == 0;
						}
					}
				}
			}
		} catch (IOException | InterruptedException | URISyntaxException e) {
			e.printStackTrace();
		}

		final boolean slimFinal = slim;
		Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
			SkinData.getOrCreate(url, slimFinal, 3, player);
		});
	}

}
