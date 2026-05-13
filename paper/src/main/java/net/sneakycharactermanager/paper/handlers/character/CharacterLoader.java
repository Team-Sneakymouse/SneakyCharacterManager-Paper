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
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;
import net.sneakycharactermanager.paper.handlers.skins.SkinData;
import net.sneakycharactermanager.paper.handlers.skins.SkinState;
import net.sneakycharactermanager.paper.handlers.skins.SkinStateManager;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.util.SkinUtil;

import org.jetbrains.annotations.Nullable;

public class CharacterLoader {

	public static boolean loadCharacter(Character character) {
		String url = character.getSkin();
		String skinUUID = character.getSkinUUID();
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

			SkinState existingState = SneakyCharacterManager.getInstance().skinStateManager
					.latestForCharacter(player.getUniqueId(), character.getCharacterUUID());
			if (existingState != null) {
				SneakyCharacterManager.getInstance().getLogger().info("[SkinState] Restoring session state for character: " + character.getName());
				ProfileProperty prop = new ProfileProperty("textures", existingState.texture(), existingState.signature());
				SkinUtil.applySkin(player, prop);
				SneakyCharacterManager.getInstance().skinStateManager.setCurrent(player.getUniqueId(), existingState.id());
			} else if (character.getTexture() != null && !character.getTexture().isEmpty() && character.getSignature() != null && !character.getSignature().isEmpty()) {
				SneakyCharacterManager.getInstance().getLogger().info("[SkinCache] Using cached texture and signature for character: " + character.getName());
				ProfileProperty prop = new ProfileProperty("textures", character.getTexture(), character.getSignature());
				SkinUtil.applySkin(player, prop);
				SneakyCharacterManager.getInstance().skinStateManager.record(
						player, "Regular", character.getTexture(), character.getSignature(),
						character.getCharacterUUID(), character.getSkin(), false);
			} else if (profileProperty != null) {
				SneakyCharacterManager.getInstance().getLogger().info("[SkinCache] Using memory-cached ProfileProperty for " + player.getName());
				SkinUtil.applySkin(player, profileProperty);
				
				String textureUrl = SkinUtil.getTextureUrl(profileProperty);
				String signature = profileProperty.getSignature();
				if (textureUrl != null && signature != null) {
					BungeeMessagingUtil.sendByteArray(player, "updateCharacter", player.getUniqueId().toString(), character.getCharacterUUID(), 1, character.getSkin(), character.getSkinUUID(), textureUrl, signature, character.isSlim());
					SneakyCharacterManager.getInstance().skinStateManager.record(
							player, "Regular", profileProperty.getValue(), signature,
							character.getCharacterUUID(), character.getSkin(), false);
				}
			} else if (!shouldSkipLoading(character)) {
				SneakyCharacterManager.getInstance().getLogger().info("[SkinCache] No cached data for " + character.getName() + " - Triggering MineSkin fetch.");
				SkinData.getOrCreate(url, skinUUID, character.isSlim(), SkinQueue.PRIO_LOAD, player, character.getCharacterUUID(), character.getName());
			}

			SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(player, character.getDisplayName());

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

	public static void updateSkin(Player player, String characterUUID, String url, Boolean slim) {
		updateSkin(player, characterUUID, url, slim, null);
	}

	/**
	 * Queues a skin fetch/apply for {@link SkinQueue#PRIO_SKIN}. When {@code skinStateName} is non-blank,
	 * the resulting skin state uses that label in chat (hover / re-apply text).
	 */
	public static void updateSkin(Player player, String characterUUID, String url, Boolean slim, @Nullable String skinStateName) {
		PlayerProfile playerProfile = player.getPlayerProfile();

		if (slim == null) {
			Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (s) -> {
				checkSlimThenSetSkin(url,
						playerProfile.getTextures().getSkinModel().equals(PlayerTextures.SkinModel.SLIM), player, characterUUID, skinStateName);
			});
		} else {
			Character character = Character.get(player);
			String name = (character != null && character.getCharacterUUID().equals(characterUUID)) ? character.getName() : null;
			SkinData sd = SkinData.getOrCreate(url, "", slim, SkinQueue.PRIO_SKIN, player, characterUUID, name);
			if (sd != null) {
				sd.setSkinStateLabel(skinStateName);
			}
		}
	}

	private static void checkSlimThenSetSkin(String url, boolean slim, Player player, String characterUUID, @Nullable String skinStateName) {		try {
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
		final String skinStateLabel = skinStateName;
		Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
			Character character = Character.get(player);
			String name = (character != null && character.getCharacterUUID().equals(characterUUID)) ? character.getName() : null;
			SkinData sd = SkinData.getOrCreate(url, "", slimFinal, SkinQueue.PRIO_SKIN, player, characterUUID, name);
			if (sd != null) {
				sd.setSkinStateLabel(skinStateLabel);
			}
		});	}

}
