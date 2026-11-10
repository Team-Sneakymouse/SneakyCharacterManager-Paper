package net.sneakycharactermanager.paper.handlers.character;

import java.awt.image.BufferedImage;

import org.bukkit.Bukkit;
import com.sneakyutils.util.TextUtility;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerTextures;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.handlers.skins.SkinApplyContext;
import net.sneakycharactermanager.paper.handlers.skins.SkinApplyService;
import net.sneakycharactermanager.paper.handlers.skins.SkinFetching;
import net.sneakycharactermanager.proxy.common.SkinFetchException;
import net.sneakycharactermanager.proxy.common.SkinUrlPolicy;
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
				character.getKey(),
				character.isFirstLoad(),
				character.getName(),
				url,
				character.isSlim(),
				character.getTagsAsString());

		Bukkit.getServer().getPluginManager().callEvent(event);

		if (!event.isCancelled()) {
			character.setFirstLoad(false);

			SkinState existingState = SneakyCharacterManager.getInstance().skinStateManager
					.latestForCharacter(player.getUniqueId(), character.getKey());
			if (existingState != null) {
				SneakyCharacterManager.getInstance().getLogger().info("[SkinState] Restoring session state for character: " + character.getName());
				// The restored state wins: queued preloads of the record skin must not paint over it later.
				SneakyCharacterManager.getInstance().skinQueue.removePendingForCharacter(player, character.getKey());
				ProfileProperty prop = new ProfileProperty("textures", existingState.texture(), existingState.signature());
				SkinUtil.applySkin(player, prop);
				SneakyCharacterManager.getInstance().skinStateManager.setCurrent(player.getUniqueId(), existingState.id());
			} else if (hasPersistedSkinProperty(character)) {
				SneakyCharacterManager.getInstance().getLogger().info("[SkinCache] Using cached texture and signature for character: " + character.getName());
				SneakyCharacterManager.getInstance().skinQueue.removePendingForCharacter(player, character.getKey());
				ProfileProperty prop = new ProfileProperty("textures", character.getTexture(), character.getSignature());
				SkinUtil.applySkin(player, prop);
				SneakyCharacterManager.getInstance().skinStateManager.record(
						player, "Regular", character.getTexture(), character.getSignature(),
						character.getKey(), character.getSkin(), false);
			} else if (!shouldSkipLoading(character)) {
				SneakyCharacterManager.getInstance().getLogger().info("[SkinCache] Resolving skin for " + character.getName());
				SkinApplyService.requestSkin(player, character.getKey(), url, character.isSlim(),
						SkinQueue.PRIO_LOAD, SkinApplyContext.defaults());
			}

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

	/**
	 * Queues a skin fetch/apply for {@link SkinQueue#PRIO_SKIN}. When {@code skinStateName} is non-blank,
	 * the resulting skin state uses that label in chat (hover / re-apply text).
	 *
	 * @deprecated a bare character ID doesn't say whose character it is. Use
	 *             {@link #updateSkin(Player, CharacterKey, String, Boolean, String)}. Kept for SneakyMannequins and
	 *             SneakyRacials: it only works for the character the player is wearing, and is dropped otherwise.
	 */
	@Deprecated
	public static void updateSkin(Player player, String characterUUID, String url, Boolean slim, @Nullable String skinStateName) {
		CharacterKey key = Character.wornKey(player, characterUUID);
		if (key == null) {
			SneakyCharacterManager.getInstance().getLogger().warning("Dropped a skin change for character " + characterUUID
					+ ": " + player.getName() + " isn't wearing it, so its owner is unknown");
			return;
		}
		updateSkin(player, key, url, slim, skinStateName);
	}

	/**
	 * Queues a skin fetch/apply for {@link SkinQueue#PRIO_SKIN} and saves it to {@code key}. {@code player} gets the
	 * skin applied if it still wears that character when the skin is ready. When {@code skinStateName} is non-blank,
	 * the resulting skin state uses that label in chat (hover / re-apply text).
	 */
	public static void updateSkin(Player player, CharacterKey key, String url, Boolean slim, @Nullable String skinStateName) {
		updateSkin(player, key, url, slim, skinStateName, SkinQueue.PRIO_SKIN);
	}

	public static void updateSkin(Player player, CharacterKey key, String url, Boolean slim, @Nullable String skinStateName, int priority) {
		SneakyCharacterManager.getInstance().skinQueue.supersede(player, key);
		String sourceUrl = SkinUtil.isMojangTextureUrl(url) ? SkinUtil.normalizeMojangTextureUrl(url) : url;
		SkinApplyContext ctx = skinStateName != null && !skinStateName.isBlank()
				? SkinApplyContext.withSkinStateLabel(skinStateName.trim())
				: SkinApplyContext.defaults();

		if (slim != null) {
			SkinApplyService.requestSkin(player, key, sourceUrl, slim, priority, ctx);
			return;
		}

		PlayerProfile playerProfile = player.getPlayerProfile();
		SkinUrlPolicy policy = SkinFetching.policy();
		Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (s) -> {
			checkSlimThenSetSkin(sourceUrl,
					playerProfile.getTextures().getSkinModel().equals(PlayerTextures.SkinModel.SLIM),
					player, key, ctx, priority, policy);
		});
	}

	private static void checkSlimThenSetSkin(String url, boolean slim, Player player, CharacterKey key,
	                                         SkinApplyContext ctx, int priority, SkinUrlPolicy policy) {
		try {
			BufferedImage image = SkinFetching.fetcher().fetchImage(url.replace("imgur", "filmot"), policy);
			int alpha = (image.getRGB(55, 20) >> 24) & 0xFF;
			slim = alpha == 0;
		} catch (SkinFetchException e) {
			SneakyCharacterManager.getInstance().getLogger().warning(
					"Slim detection failed for " + url + ": " + e.getMessage());
			if (e.isRejection()) {
				Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
					if (player.isOnline()) player.sendMessage(TextUtility.INSTANCE.convertToComponent(SkinFetching.REJECTED_MESSAGE));
				});
				return;
			}
		} catch (Exception e) {
			SneakyCharacterManager.getInstance().getLogger().warning(
					"Slim detection failed for " + url + ": " + e.getMessage());
		}
		final boolean slimFinal = slim;
		Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () ->
				SkinApplyService.requestSkin(player, key, url, slimFinal, priority, ctx));
	}

}
