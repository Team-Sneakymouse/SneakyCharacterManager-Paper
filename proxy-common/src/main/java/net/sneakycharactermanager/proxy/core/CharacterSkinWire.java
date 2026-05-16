package net.sneakycharactermanager.proxy.core;

import net.sneakycharactermanager.proxy.common.ProxyLogger;
import net.sneakycharactermanager.proxy.common.SkinContentHash;

import java.util.Optional;

/**
 * Resolves {@link CharacterData} skin fields for plugin messages to Paper.
 */
public final class CharacterSkinWire {

    public record Resolved(String skin, String skinUUID, String texture, String signature, String skinId) {}

    private CharacterSkinWire() {}

    public static Resolved resolve(CharacterData character, GlobalSkinCache globalSkinCache, ProxyLogger logger) {
        if (character.usesSkinIdFormat()) {
            Optional<GlobalSkinCache.Entry> entry = globalSkinCache.getById(character.skinId());
            if (entry.isPresent()) {
                GlobalSkinCache.Entry e = entry.get();
                return new Resolved(e.mojangTextureUrl, "", e.texture, e.signature, e.skinId);
            }
            if (hasLegacyTextureFields(character)) {
                logger.warning("Character " + character.uuid() + " skinId " + character.skinId()
                        + " missing from global cache; using legacy texture/signature from player file");
                return legacyResolved(character);
            }
            logger.warning("Character " + character.uuid() + " references missing skinId " + character.skinId());
            return new Resolved("", "", "", "", character.skinId());
        }
        return legacyResolved(character);
    }

    private static boolean hasLegacyTextureFields(CharacterData character) {
        return character.texture() != null && !character.texture().isEmpty()
                && character.signature() != null && !character.signature().isEmpty();
    }

    private static Resolved legacyResolved(CharacterData character) {
        String wireSkin = character.skin() == null ? "" : character.skin();
        String mojangFromTexture = SkinContentHash.mojangUrlFromTextureProperty(character.texture());
        if (mojangFromTexture != null && !mojangFromTexture.isEmpty()) {
            wireSkin = mojangFromTexture;
        }
        return new Resolved(
                wireSkin,
                character.skinUUID() == null ? "" : character.skinUUID(),
                character.texture(),
                character.signature(),
                character.usesSkinIdFormat() ? character.skinId() : ""
        );
    }

    public static String baseSkinUrlForUniforms(Resolved resolved, CharacterData character) {
        String mojangFromTexture = SkinContentHash.mojangUrlFromTextureProperty(character.texture());
        if (mojangFromTexture != null && !mojangFromTexture.isEmpty()) return mojangFromTexture;
        if (!resolved.skin().isEmpty()) return resolved.skin();
        return character.skin() == null ? "" : character.skin();
    }
}
