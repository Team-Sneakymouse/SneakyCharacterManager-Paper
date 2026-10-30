package net.sneakycharactermanager.paper.storage;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerTextures;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.sneakyutils.util.TextUtility;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;
import net.sneakycharactermanager.paper.handlers.skins.SkinApplyContext;
import net.sneakycharactermanager.paper.handlers.skins.SkinApplyService;
import net.sneakycharactermanager.paper.handlers.skins.SkinCache;
import net.sneakycharactermanager.paper.handlers.skins.SkinData;
import net.sneakycharactermanager.paper.util.ProxyMessagingUtil;

public final class CharacterResultEffects {
    private CharacterResultEffects() {}
    public static void load(Character character, boolean forced) {
        Player player = character.getPlayer();
        if (player == null) return;
        if (!forced && !Character.canPlayerLoadCharacter(player, character.getCharacterUUID())) {
            player.sendMessage(TextUtility.INSTANCE.convertToComponent("&4You cannot access this character right now."));
            return;
        }
        character.load();
    }
    public static void titles(Map<String,String> titles) {
        SneakyCharacterManager.getInstance().titleRegistry.replaceAll(titles);
        for (Player online : Bukkit.getOnlinePlayers()) {
            Character titled = Character.get(online);
            if (titled != null) SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(online, titled.getDisplayName());
        }
    }
    public static void defaultSkin(String owner, String id) {
        Player player = Bukkit.getPlayer(UUID.fromString(owner));
        if (player == null) return;
        PlayerTextures textures = player.getPlayerProfile().getTextures();
        if (textures.getSkin() == null) return;
        ProxyMessagingUtil.sendByteArray(player, "defaultSkin", owner, id, textures.getSkin().toString(),
                textures.getSkinModel().equals(PlayerTextures.SkinModel.SLIM));
    }
	public static void handleListCompletion(String baseChannel, String playerUUID, String requesterUUID, List<Character> characters) {
		if ("syncCharacters".equals(baseChannel)) {
			Character.updatePlayerCharacters(UUID.fromString(playerUUID), characters);

			boolean openGUI = false;
			String actualRequester = requesterUUID;
			if (requesterUUID.endsWith("|openGUI")) {
				openGUI = true;
				actualRequester = requesterUUID.substring(0, requesterUUID.length() - "|openGUI".length());
			}

			if (openGUI) {
				SneakyCharacterManager.getInstance().selectionMenu.updateInventory(actualRequester, characters);
				return;
			}

			Player pl = Bukkit.getPlayer(UUID.fromString(playerUUID));
			if (pl == null)
				return;

			// Online player loading their own skins → PRIO_ONLINE.
			// Offline player being bulk-preloaded → PRIO_OFFLINE.
			int skinPrio = actualRequester.equals(playerUUID) ? SkinQueue.PRIO_ONLINE : SkinQueue.PRIO_OFFLINE;

			for (Character c : characters) {
				// Base skin preloading
				String skinUrl = c.getSkin();
				if (skinUrl != null && !skinUrl.isEmpty()) {
					if (net.sneakycharactermanager.paper.handlers.character.CharacterLoader.hasPersistedSkinProperty(c)) {
						ProfileProperty p = new ProfileProperty("textures", c.getTexture(), c.getSignature());
						String cacheKey = net.sneakycharactermanager.paper.util.SkinUtil.getTextureUrl(p);
						if (cacheKey == null || cacheKey.isEmpty()) cacheKey = skinUrl;
						SkinCache.put(playerUUID, cacheKey, p);
						SkinCache.put(playerUUID, skinUrl, p);
						SneakyCharacterManager.getInstance().skinQueue.removePendingForCharacter(pl, c.getCharacterUUID());
					} else {
						SkinApplyService.requestSkin(pl, c.getCharacterUUID(), skinUrl, c.isSlim(),
								skinPrio, SkinApplyContext.preload());
					}
				}

				// Uniform variant preloading
				for (Map.Entry<String, String[]> entry : c.getUniformVariants().entrySet()) {
					String[] vData = entry.getValue();
					String vUUID = vData[0];
					String vUrl = vData[1];
					if (vUrl == null || vUrl.isEmpty()) continue;

					ProfileProperty vp = SkinCache.get(playerUUID, vUrl);

					// If not in memory but we have raw data from proxy (Uniform variant data: 0:uuid, 1:url, 2:texture, 3:signature)
					if (vp == null && vData.length > 2 && vData[2] != null && !vData[2].isEmpty()) {
						vp = new ProfileProperty("textures", vData[2], vData[3]);
						SkinCache.put(playerUUID, vUrl, vp);
					}

					if (vp == null) {
						SkinData.getOrCreate(vUrl, vUUID, c.isSlim(), skinPrio, pl, c.getCharacterUUID(), c.getName())
							.setUniformCacheInfo(c.getSkin(), entry.getKey(), entry.getKey());
					}
				}
			}
		}
	}

}
