package net.sneakycharactermanager.paper.storage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerTextures;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.sneakyutils.util.TextUtility;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.LoadReason;
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;
import net.sneakycharactermanager.paper.handlers.skins.SkinApplyContext;
import net.sneakycharactermanager.paper.handlers.skins.SkinApplyService;
import net.sneakycharactermanager.paper.handlers.skins.SkinCache;
import net.sneakycharactermanager.paper.handlers.skins.SkinData;
import net.sneakycharactermanager.paper.util.ProxyMessagingUtil;

public final class CharacterResultEffects {
    private CharacterResultEffects() {}
    /** A body's skin URL and model, or a null URL for none. */
    record BodySkin(String url, boolean slim) {}
    /** How long a legacy load's skin waits for the proxy's {@code defaultSkin} message that follows it. */
    static final long LEGACY_SKIN_TTL_NANOS = TimeUnit.SECONDS.toNanos(30);
    private record LegacySkin(UUID body, BodySkin skin, long capturedAt) {}
    /**
     * Legacy mode: the skin each skinless own character's body had the moment its load put it on (before the load's
     * {@code PuppetLeaveEvent} and {@code CharacterLoadedEvent} listeners could put another character and skin on
     * the body), for the proxy's {@code defaultSkin} message that follows the load. Kept until that message, the
     * body's quit, a later load of the character, or {@link #LEGACY_SKIN_TTL_NANOS}. Main thread only.
     */
    private static final Map<CharacterKey, LegacySkin> legacySkins = new HashMap<>();
    /**
     * Legacy mode: loads a storage result for the owner's own character onto the owner: a join or create
     * ({@code forced}, no lock check) or a {@code /char} pick. The holder rule decides what happens if another body
     * wears it. A skinless character's body skin is kept for the proxy's {@code defaultSkin} message (see
     * {@link #legacySkins}); the proxy decides whether it sends one.
     */
    public static void load(Character character, boolean forced) {
        Player player = character.getPlayer();
        if (player == null) return;
        CharacterKey key = character.getKey();
        legacySkins.remove(key);
        if (!forced && !canLoad(player, character.getCharacterUUID())) return;
        boolean skinless = character.getSkin() == null || character.getSkin().isEmpty();
        character.load(LoadReason.ownResult(forced), !skinless ? null : () -> {
            long now = System.nanoTime();
            legacySkins.values().removeIf(kept -> now - kept.capturedAt() > LEGACY_SKIN_TTL_NANOS);
            legacySkins.put(key, new LegacySkin(player.getUniqueId(), bodySkin(player), now));
        });
    }
    /**
     * SneakyDB mode: {@link #load(Character, boolean)}, and if {@code defaultSkin} (the character has no skin and no
     * skin ID), saves the body's skin as the character's skin the moment the load puts it on: before the load's
     * {@code PuppetLeaveEvent} and {@code CharacterLoadedEvent} listeners can change the body, and queued before
     * anything they ask of storage (so a {@code /char} pick they make sees the write and isn't dropped as stale).
     *
     * @return true if the default skin was handled here; false if the load didn't go through
     */
    public static boolean load(Character character, boolean forced, boolean defaultSkin) {
        Player player = character.getPlayer();
        if (player == null) return false;
        if (!forced && !canLoad(player, character.getCharacterUUID())) return false;
        CharacterKey key = character.getKey();
        boolean[] handled = {false};
        character.load(LoadReason.ownResult(forced), !defaultSkin ? null : () -> {
            handled[0] = true;
            sendDefaultSkin(player, key.owner().toString(), key.characterId(), bodySkin(player));
        });
        return handled[0];
    }
    /** The body quit: its legacy load skins are no longer wanted. */
    public static void forget(Player body) {
        legacySkins.values().removeIf(kept -> kept.body().equals(body.getUniqueId()));
    }
    /** The character-lock check for an owner picking their own character; tells them if it fails. */
    static boolean canLoad(Player player, String characterId) {
        if (Character.canPlayerLoadCharacter(player, characterId)) return true;
        player.sendMessage(TextUtility.INSTANCE.convertToComponent("&4You cannot access this character right now."));
        return false;
    }
    public static void titles(Map<String,String> titles) {
        SneakyCharacterManager.getInstance().titleRegistry.replaceAll(titles);
        for (Player online : Bukkit.getOnlinePlayers()) {
            Character titled = Character.get(online);
            if (titled != null) SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(online, titled.getDisplayName());
        }
    }
    /**
     * Saves the owner's skin as character {@code id}'s skin (it has none). The legacy proxy's {@code defaultSkin}
     * message, after its load: uses the skin the body had when that load put the character on (see
     * {@link #legacySkins}); the live skin only if no load of it went through (refused, cancelled) or its kept skin
     * expired, as before. SneakyDB mode handles it in {@link #load(Character, boolean, boolean)}, and calls this only
     * for a load that didn't go through.
     */
    public static void defaultSkin(String owner, String id) {
        Player player = Bukkit.getPlayer(UUID.fromString(owner));
        if (player == null) return;
        LegacySkin kept = legacySkins.remove(CharacterKey.of(owner, id));
        boolean usable = kept != null && kept.body().equals(player.getUniqueId())
                && System.nanoTime() - kept.capturedAt() <= LEGACY_SKIN_TTL_NANOS;
        sendDefaultSkin(player, owner, id, usable ? kept.skin() : bodySkin(player));
    }
    private static void sendDefaultSkin(Player player, String owner, String id, BodySkin skin) {
        if (skin.url() == null) return;
        ProxyMessagingUtil.sendByteArray(player, "defaultSkin", owner, id, skin.url(), skin.slim());
    }
    private static BodySkin bodySkin(Player player) {
        PlayerTextures textures = player.getPlayerProfile().getTextures();
        if (textures.getSkin() == null) return new BodySkin(null, false);
        return new BodySkin(textures.getSkin().toString(), textures.getSkinModel().equals(PlayerTextures.SkinModel.SLIM));
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
				SneakyCharacterManager.getInstance().selectionMenu.updateInventory(actualRequester, playerUUID, characters);
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
						SneakyCharacterManager.getInstance().skinQueue.removePendingForCharacter(pl, c.getKey());
					} else {
						SkinApplyService.requestSkin(pl, c.getKey(), skinUrl, c.isSlim(),
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
						SkinData.getOrCreate(vUrl, vUUID, c.isSlim(), skinPrio, pl, c.getKey(), c.getName())
							.setUniformCacheInfo(c.getSkin(), entry.getKey(), entry.getKey());
					}
				}
			}
		}
	}

}
