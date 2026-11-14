package net.sneakycharactermanager.paper.handlers.character;

import org.bukkit.entity.Player;

import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.util.ProxyMessagingUtil;

/**
 * Saves an identity edit (name, tags, gender, title, skin, enabled) to a character's record. The record is addressed by
 * the character's key; {@code via} is only the player whose connection carries the message on the legacy proxy path.
 */
public final class CharacterUpdates {

    public static final int LEGACY_SKIN = 1;
    public static final int NAME = 2;
    public static final int ENABLED = 3;
    public static final int TAGS = 4;
    public static final int GENDER = 5;
    public static final int SKIN_ID = 6;
    public static final int TITLE = 7;

    private CharacterUpdates() {}

    public static void send(Player via, CharacterKey key, int type, Object... values) {
        Object[] payload = new Object[values.length + 3];
        payload[0] = key.owner().toString();
        payload[1] = key.characterId();
        payload[2] = type;
        System.arraycopy(values, 0, payload, 3, values.length);
        ProxyMessagingUtil.sendByteArray(via, "updateCharacter", payload);
    }
}
