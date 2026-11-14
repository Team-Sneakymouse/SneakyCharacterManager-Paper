package net.sneakycharactermanager.paper.handlers.skins;

import com.destroystokyo.paper.profile.ProfileProperty;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.CharacterUpdates;
import com.sneakyutils.util.TextUtility;
import net.sneakycharactermanager.paper.util.SkinUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Nullable;

public final class SkinApplyService {

    private SkinApplyService() {}

    /**
     * Resolves {@code sourceUrl} and applies it for {@code characterKey}. The key is fixed now: however late the result
     * arrives, it is saved to this character and only put on {@code player} if the player still wears it.
     */
    public static void requestSkin(Player player, CharacterKey characterKey, String sourceUrl, @Nullable Boolean slim,
                                   int priority, @Nullable SkinApplyContext ctx) {
        if (sourceUrl == null || sourceUrl.isEmpty() || player == null) return;
        SkinApplyContext context = ctx == null ? SkinApplyContext.defaults() : ctx;
        boolean slimValue = slim != null ? slim : false;
        final String resolveUrl = SkinUtil.isMojangTextureUrl(sourceUrl)
                ? SkinUtil.normalizeMojangTextureUrl(sourceUrl)
                : sourceUrl;

        // Always resolve the requested sourceUrl. Do not reuse in-memory texture/signature from a prior
        // /skin — CharacterLoader applies proxy-persisted skins on login without calling requestSkin.
        SkinCache.resolve(player, resolveUrl, slimValue).whenComplete((result, ex) -> {
            SkinCache.ResolveResult resolved = result;
            if (ex != null || resolved == null) {
                resolved = new SkinCache.ResolveResult(
                        SkinCache.ResolveStatus.ERROR, "", "", "", "",
                        ex != null ? ex.getMessage() : "Resolve failed");
            }
            final SkinCache.ResolveResult resultFinal = resolved;
            Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () ->
                    handleResolveResult(player, characterKey, resolveUrl, slimValue, priority, context, resultFinal));
        });
    }

    private static void handleResolveResult(Player player, CharacterKey characterKey, String resolveUrl, boolean slimValue,
                                            int priority, SkinApplyContext context, SkinCache.ResolveResult result) {
        if (!player.isOnline()) return;

        if (result.status == SkinCache.ResolveStatus.REJECTED) {
            player.sendMessage(TextUtility.INSTANCE.convertToComponent(SkinFetching.REJECTED_MESSAGE));
            return;
        }

        if (result.status == SkinCache.ResolveStatus.HIT) {
            ProfileProperty property = result.toProfileProperty();
            if (property == null) {
                player.sendMessage(TextUtility.INSTANCE.convertToComponent(
                        "&4Skin cache returned no texture data for that URL."));
                SneakyCharacterManager.getInstance().getLogger().warning(
                        "Skin cache HIT with empty texture for " + player.getName() + ": " + resolveUrl);
                if (!SkinUtil.isMojangTextureUrl(resolveUrl)) {
                    queueMineSkin(player, characterKey, resolveUrl, slimValue, priority, context);
                }
                return;
            }
            applyResolved(player, characterKey, resolveUrl, result.skinId, result.mojangTextureUrl,
                    property, priority, context, slimValue);
        } else {
            queueMineSkin(player, characterKey, resolveUrl, slimValue, priority, context, result.skinId);
        }
    }

    private static void queueMineSkin(Player player, CharacterKey characterKey, String sourceUrl, boolean slim,
                                      int priority, SkinApplyContext context) {
        queueMineSkin(player, characterKey, sourceUrl, slim, priority, context, "");
    }

    private static void queueMineSkin(Player player, CharacterKey characterKey, String sourceUrl, boolean slim,
                                      int priority, SkinApplyContext context, String knownSkinId) {
        Character character = Character.get(player);
        String name = Character.isWearing(player, characterKey) ? character.getName() : null;

        SkinData sd;
        if (context.skullMeta() != null && context.characterHead() != null && context.inventory() != null) {
            sd = SkinData.getOrCreate(sourceUrl, "", slim, priority, player, characterKey, name,
                    context.skullMeta(), context.characterHead(), context.inventory(), context.index());
        } else {
            sd = SkinData.getOrCreate(sourceUrl, "", slim, priority, player, characterKey, name);
        }

        if (sd == null) return;
        if (context.skinStateLabel() != null) {
            sd.setSkinStateLabel(context.skinStateLabel());
        }
        if (!knownSkinId.isEmpty()) {
            sd.setPendingSkinId(knownSkinId);
        }
    }

    private static void applyResolved(Player player, CharacterKey characterKey, String sourceUrl, String skinId,
                                      String mojangUrl, ProfileProperty property, int priority,
                                      SkinApplyContext context, boolean slimFallback) {
        if (context.skullMeta() != null && context.characterHead() != null && context.inventory() != null) {
            SkullMeta skullMeta = context.skullMeta();
            skullMeta.setPlayerProfile(SkinUtil.handleCachedSkin(player, property));
            context.characterHead().setItemMeta(skullMeta);
            context.inventory().setItem(context.index(), context.characterHead());
            return;
        }

        if (priority >= SkinQueue.PRIO_LOAD) {
            boolean slim = SkinUtil.isSlimModel(property, slimFallback);
            String resolvedUrl = mojangUrl.isEmpty() ? textureUrlFromProperty(property) : mojangUrl;

            boolean worn = Character.isWearing(player, characterKey);
            if (worn) {
                SkinUtil.applySkin(player, property);
                // applySkin fires events on this thread: a listener can switch or release the body before it returns.
                worn = Character.isWearing(player, characterKey);
            }

            if (worn) {
                if (context.recordSkinState()) {
                    String stateName = context.skinStateLabel() != null ? context.skinStateLabel() : "Regular";
                    String textureUrl = resolvedUrl == null ? sourceUrl : resolvedUrl;
                    SkinState state = SneakyCharacterManager.getInstance().skinStateManager.record(
                            player, stateName, property.getValue(), property.getSignature(),
                            characterKey, textureUrl, false);
                    if (priority == SkinQueue.PRIO_SKIN || priority == SkinQueue.PRIO_UNIFORM) {
                        SkinStateManager.sendSkinUpdatedMessage(player, state);
                    }
                }

                Character character = Character.get(player);
                if (resolvedUrl != null) {
                    character.setSkin(resolvedUrl);
                    character.setTexture(property.getValue());
                    character.setSignature(property.getSignature());
                    character.setSlim(slim);
                }
            } else if (context.updateProxyCharacter()) {
                // The player switched away before the skin resolved or while it was put on. It still belongs to the
                // requested character:
                // save it there (below), and keep this body's older session state from overriding it on reload.
                SneakyCharacterManager.getInstance().skinStateManager.forgetCharacter(characterKey);
            }

            if (context.updateProxyCharacter()) {
                if (!skinId.isEmpty()) {
                    SkinCache.updateCharacterSkin(player, characterKey, skinId, slim);
                } else if (resolvedUrl != null) {
                    CharacterUpdates.send(player, characterKey, CharacterUpdates.LEGACY_SKIN,
                            resolvedUrl, "", property.getValue(), property.getSignature(), slim);
                }
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
                CharacterUpdates.send(skinData.getPlayer(), skinData.getCharacterKey(), CharacterUpdates.LEGACY_SKIN,
                        mojangUrl, skinData.getSkinUUID(), property.getValue(), property.getSignature(), skinData.isSlim());
            }
            return;
        }

        SkinCache.updateCharacterSkin(skinData.getPlayer(), skinData.getCharacterKey(), skinId, skinData.isSlim());
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
        return skinData.getCharacterKey() != null
                && skinData.getUniformHash() == null
                && (skinData.getPriority() == SkinQueue.PRIO_SKIN || skinData.getPriority() == SkinQueue.PRIO_LOAD);
    }

    @Nullable
    public static String textureUrlFromProperty(ProfileProperty property) {
        if (property == null) return null;
        return SkinUtil.getTextureUrl(property);
    }
}
