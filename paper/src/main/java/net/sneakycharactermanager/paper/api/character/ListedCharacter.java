package net.sneakycharactermanager.paper.api.character;

/**
 * One entry of an owner's character list ({@link CharacterLists#of}): enough to draw it in a menu like {@code /char}.
 *
 * @param name      the character's name, with its formatting codes
 * @param skinUrl   the skin's texture URL, or empty
 * @param texture   the signed skin texture, or empty if storage has none cached
 * @param signature the texture's signature, or empty
 * @param current   true for the owner's current character: the one they wear if they're online and wearing one of
 *                  their own, otherwise their last-played one
 */
public record ListedCharacter(CharacterKey key, String name, String skinUrl, String texture, String signature,
        boolean slim, boolean current) {}
