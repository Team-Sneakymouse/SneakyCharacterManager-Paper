package net.sneakycharactermanager.paper.handlers.skins;

import com.destroystokyo.paper.profile.ProfileProperty;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.util.SkinUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Nullable;

public final class SkinApplyService {

    private SkinApplyService() {}

    public static void requestSkin(Player player, String characterUUID, String sourceUrl, @Nullable Boolean slim,
                                   int priority, @Nullable SkinApplyContext ctx) {
        if (sourceUrl == null || sourceUrl.isEmpty() || player == null) return;
        SkinApplyContext context = ctx == null ? SkinApplyContext.defaults() : ctx;
        boolean slimValue = slim != null ? slim : false;

        // Always resolve the requested sourceUrl. Do not reuse in-memory texture/signature from a prior
        // /skin — CharacterLoader applies proxy-persisted skins on login without calling requestSkin.
        SkinCache.resolve(player, sourceUrl, slimValue).thenAccept(result -> {
            Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
                if (!player.isOnline()) return;

                if (result.status == SkinCache.ResolveStatus.HIT) {
                    ProfileProperty property = result.toProfileProperty();
                    if (property == null) {
                        queueMineSkin(player, characterUUID, sourceUrl, slimValue, priority, context);
                        return;
                    }
                    applyResolved(player, characterUUID, sourceUrl, result.skinId, result.mojangTextureUrl,
                            property, priority, context);
                } else if (result.status == SkinCache.ResolveStatus.MISS) {
                    queueMineSkin(player, characterUUID, sourceUrl, slimValue, priority, context, result.skinId);
                } else {
                    SneakyCharacterManager.getInstance().getLogger().warning(
                            "Skin resolve error for " + player.getName() + ": " + result.errorMessage);
                    queueMineSkin(player, characterUUID, sourceUrl, slimValue, priority, context);
                }
            });
        });
    }

    private static void queueMineSkin(Player player, String characterUUID, String sourceUrl, boolean slim,
                                      int priority, SkinApplyContext context) {
        queueMineSkin(player, characterUUID, sourceUrl, slim, priority, context, "");
    }

    private static void queueMineSkin(Player player, String characterUUID, String sourceUrl, boolean slim,
                                      int priority, SkinApplyContext context, String knownSkinId) {
        Character character = Character.get(player);
        String name = (character != null && character.getCharacterUUID().equals(characterUUID))
                ? character.getName() : null;

        SkinData sd;
        if (context.skullMeta() != null && context.characterHead() != null && context.inventory() != null) {
            sd = SkinData.getOrCreate(sourceUrl, "", slim, priority, player, characterUUID, name,
                    context.skullMeta(), context.characterHead(), context.inventory(), context.index());
        } else {
            sd = SkinData.getOrCreate(sourceUrl, "", slim, priority, player, characterUUID, name);
        }

        if (sd == null) return;
        if (context.skinStateLabel() != null) {
            sd.setSkinStateLabel(context.skinStateLabel());
        }
        if (!knownSkinId.isEmpty()) {
            sd.setPendingSkinId(knownSkinId);
        }
    }

    private static void applyResolved(Player player, String characterUUID, String sourceUrl, String skinId,
                                      String mojangUrl, ProfileProperty property, int priority,
                                      SkinApplyContext context) {
        if (context.skullMeta() != null && context.characterHead() != null && context.inventory() != null) {
            SkullMeta skullMeta = context.skullMeta();
            skullMeta.setPlayerProfile(SkinUtil.handleCachedSkin(player, property));
            context.characterHead().setItemMeta(skullMeta);
            context.inventory().setItem(context.index(), context.characterHead());
            return;
        }

        if (priority >= SkinQueue.PRIO_LOAD) {
            SkinUtil.applySkin(player, property);

            if (context.recordSkinState()) {
                String stateName = context.skinStateLabel() != null ? context.skinStateLabel() : "Regular";
                String textureUrl = mojangUrl.isEmpty() ? textureUrlFromProperty(property) : mojangUrl;
                if (textureUrl == null) textureUrl = sourceUrl;
                SneakyCharacterManager.getInstance().skinStateManager.record(
                        player, stateName, property.getValue(), property.getSignature(),
                        characterUUID, textureUrl, false);
            }

            Character character = Character.get(player);
            if (character != null && character.getCharacterUUID().equals(characterUUID)) {
                String resolvedUrl = mojangUrl.isEmpty() ? textureUrlFromProperty(property) : mojangUrl;
                if (resolvedUrl != null) {
                    character.setSkin(resolvedUrl);
                    character.setTexture(property.getValue());
                    character.setSignature(property.getSignature());
                }
            }

            if (context.updateProxyCharacter() && !skinId.isEmpty()) {
                boolean slim = character != null && character.isSlim();
                SkinCache.updateCharacterSkin(player, characterUUID, skinId, slim);
            }
        }
    }

    public static void onMineSkinApplied(SkinData skinData, ProfileProperty property) {
        if (property == null || skinData.getPlayer() == null) return;

        Player player = skinData.getPlayer();
        String mojangUrlRaw = textureUrlFromProperty(property);
        if (mojangUrlRaw == null) mojangUrlRaw = skinData.getUrl();
        final String mojangUrl = mojangUrlRaw;

        String pending = skinData.getPendingSkinId();
        if (pending != null && !pending.isEmpty()) {
            finishRegister(skinData, property, mojangUrl, pending);
            return;
        }

        SkinCache.resolve(player, mojangUrl, skinData.isSlim()).thenAccept(result -> {
            String skinId = result.skinId;
            if (result.status == SkinCache.ResolveStatus.HIT && !result.skinId.isEmpty()) {
                skinId = result.skinId;
            }
            final String idFinal = skinId == null ? "" : skinId;
            Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(),
                    () -> finishRegister(skinData, property, mojangUrl, idFinal));
        });
    }

    /**
     * Registers the applied skin in the proxy global cache ({@code skin_cache.yml}).
     * Does not update the per-character proxy save (use {@link #shouldUpdateProxyCharacterSave}).
     */
    public static void registerGlobalSkinCacheAfterApply(Player player, String sourceUrl,
                                                         ProfileProperty property, boolean slim) {
        if (player == null || property == null) return;
        String mojangUrlRaw = textureUrlFromProperty(property);
        if (mojangUrlRaw == null || mojangUrlRaw.isEmpty()) mojangUrlRaw = sourceUrl;
        final String mojangUrl = mojangUrlRaw;
        String resolveUrl = !mojangUrl.isEmpty() ? mojangUrl : sourceUrl;
        if (resolveUrl == null || resolveUrl.isEmpty()) return;

        SkinCache.resolve(player, resolveUrl, slim).thenAccept(result -> {
            if (result.skinId == null || result.skinId.isEmpty()) return;
            final String skinIdFinal = result.skinId;
            Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
                if (!player.isOnline()) return;
                SkinCache.register(player, skinIdFinal, mojangUrl,
                        property.getValue(), property.getSignature(), sourceUrl);
            });
        });
    }

    private static void finishRegister(SkinData skinData, ProfileProperty property, String mojangUrl, String skinId) {
        registerGlobalSkinCache(skinData, property, mojangUrl, skinId);

        if (!shouldUpdateProxyCharacterSave(skinData)) {
            return;
        }

        if (skinId == null || skinId.isEmpty()) {
            if (skinData.getPlayer() != null) {
                BungeeMessagingUtil.sendByteArray(skinData.getPlayer(), "updateCharacter",
                        skinData.getPlayer().getUniqueId().toString(), skinData.getCharacterUUID(), 1,
                        mojangUrl, skinData.getSkinUUID(), property.getValue(), property.getSignature(), skinData.isSlim());
            }
            return;
        }

        SkinCache.updateCharacterSkin(skinData.getPlayer(), skinData.getCharacterUUID(), skinId, skinData.isSlim());
    }

    private static void registerGlobalSkinCache(SkinData skinData, ProfileProperty property, String mojangUrl, String skinId) {
        if (property == null || skinData.getPlayer() == null) return;

        if (skinId != null && !skinId.isEmpty()) {
            SkinCache.register(skinData.getPlayer(), skinId, mojangUrl,
                    property.getValue(), property.getSignature(), skinData.getUrl());
            return;
        }

        Player player = skinData.getPlayer();
        String resolveUrl = (mojangUrl != null && !mojangUrl.isEmpty()) ? mojangUrl : skinData.getUrl();
        if (resolveUrl == null || resolveUrl.isEmpty()) return;

        SkinCache.resolve(player, resolveUrl, skinData.isSlim()).thenAccept(result -> {
            if (result.skinId == null || result.skinId.isEmpty()) return;
            Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
                if (!player.isOnline()) return;
                SkinCache.register(player, result.skinId, mojangUrl,
                        property.getValue(), property.getSignature(), skinData.getUrl());
            });
        });
    }

    private static boolean shouldUpdateProxyCharacterSave(SkinData skinData) {
        return skinData.getCharacterUUID() != null && !skinData.getCharacterUUID().isEmpty()
                && skinData.getUniformHash() == null
                && (skinData.getPriority() == SkinQueue.PRIO_SKIN || skinData.getPriority() == SkinQueue.PRIO_LOAD);
    }

    @Nullable
    public static String textureUrlFromProperty(ProfileProperty property) {
        if (property == null) return null;
        return SkinUtil.getTextureUrl(property);
    }
}
