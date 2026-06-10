package net.sneakycharactermanager.paper.handlers.character;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
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
import net.sneakycharactermanager.paper.handlers.skins.SkinApplyContext;
import net.sneakycharactermanager.paper.handlers.skins.SkinApplyService;
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;
import net.sneakycharactermanager.paper.handlers.skins.SkinState;
import net.sneakycharactermanager.paper.handlers.skins.SkinStateManager;
import net.sneakycharactermanager.paper.util.SkinUtil;

import org.jetbrains.annotations.Nullable;

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

			character.setFirstLoad(false);

			SkinState existingState = SneakyCharacterManager.getInstance().skinStateManager
					.latestForCharacter(player.getUniqueId(), character.getCharacterUUID());
			if (existingState != null) {
				SneakyCharacterManager.getInstance().getLogger().info("[SkinState] Restoring session state for character: " + character.getName());
				ProfileProperty prop = new ProfileProperty("textures", existingState.texture(), existingState.signature());
				SkinUtil.applySkin(player, prop);
				SneakyCharacterManager.getInstance().skinStateManager.setCurrent(player.getUniqueId(), existingState.id());
			} else if (hasPersistedSkinProperty(character)) {
				SneakyCharacterManager.getInstance().getLogger().info("[SkinCache] Using cached texture and signature for character: " + character.getName());
				SneakyCharacterManager.getInstance().skinQueue.removePendingForCharacter(player, character.getCharacterUUID());
				ProfileProperty prop = new ProfileProperty("textures", character.getTexture(), character.getSignature());
				SkinUtil.applySkin(player, prop);
				SneakyCharacterManager.getInstance().skinStateManager.record(
						player, "Regular", character.getTexture(), character.getSignature(),
						character.getCharacterUUID(), character.getSkin(), false);
			} else if (!shouldSkipLoading(character)) {
				SneakyCharacterManager.getInstance().getLogger().info("[SkinCache] Resolving skin for " + character.getName());
				SkinApplyService.requestSkin(player, character.getCharacterUUID(), url, character.isSlim(),
						SkinQueue.PRIO_LOAD, SkinApplyContext.defaults());
			}

			SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(player, character.getDisplayName());

			return true;
		}
		return false;
	}

	public static boolean hasPersistedSkinProperty(Character character) {
		return character.getTexture() != null && !character.getTexture().isEmpty()
				&& character.getSignature() != null && !character.getSignature().isEmpty();
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
		updateSkin(player, characterUUID, url, slim, skinStateName, SkinQueue.PRIO_SKIN);
	}

	public static void updateSkin(Player player, String characterUUID, String url, Boolean slim, @Nullable String skinStateName, int priority) {
		String sourceUrl = SkinUtil.isMojangTextureUrl(url) ? SkinUtil.normalizeMojangTextureUrl(url) : url;
		SkinApplyContext ctx = skinStateName != null && !skinStateName.isBlank()
				? SkinApplyContext.withSkinStateLabel(skinStateName.trim())
				: SkinApplyContext.defaults();

		if (slim != null) {
			SkinApplyService.requestSkin(player, characterUUID, sourceUrl, slim, priority, ctx);
			return;
		}

		// Mojang texture URLs are resolved via the proxy global cache — no PNG pre-download for slim detection.
		if (SkinUtil.isMojangTextureUrl(sourceUrl)) {
			boolean slimFromProfile = player.getPlayerProfile().getTextures().getSkinModel()
					.equals(PlayerTextures.SkinModel.SLIM);
			SkinApplyService.requestSkin(player, characterUUID, sourceUrl, slimFromProfile, priority, ctx);
			return;
		}

		PlayerProfile playerProfile = player.getPlayerProfile();
		Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (s) -> {
			checkSlimThenSetSkin(sourceUrl,
					playerProfile.getTextures().getSkinModel().equals(PlayerTextures.SkinModel.SLIM),
					player, characterUUID, ctx, priority);
		});
	}

	private static void checkSlimThenSetSkin(String url, boolean slim, Player player, String characterUUID,
	                                         SkinApplyContext ctx, int priority) {
		try {
			HttpClient httpClient = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder().uri(
					new URI(url.replace("imgur", "filmot")))
					.timeout(Duration.ofSeconds(2))
					.build();
			HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

			if (response.statusCode() == 200) {
				try (InputStream inputStream = response.body()) {
					if (inputStream != null) {
						BufferedImage image = ImageIO.read(inputStream);
						if (image != null && image.getWidth() > 55 && image.getHeight() > 20) {
							int alpha = (image.getRGB(55, 20) >> 24) & 0xFF;
							slim = alpha == 0;
						}
					}
				}
			}
		} catch (Exception e) {
			SneakyCharacterManager.getInstance().getLogger().warning(
					"Slim detection failed for " + url + ": " + e.getMessage());
		} finally {
			final boolean slimFinal = slim;
			Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () ->
					SkinApplyService.requestSkin(player, characterUUID, url, slimFinal, priority, ctx));
		}
	}

}
