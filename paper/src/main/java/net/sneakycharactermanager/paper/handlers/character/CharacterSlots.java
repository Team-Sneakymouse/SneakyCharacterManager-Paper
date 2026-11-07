package net.sneakycharactermanager.paper.handlers.character;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.SneakyCharacterManager;

/**
 * Character slot count. Base slots come from {@code sneakycharacters.characterslots.<n>} (the highest n wins); bonus
 * slots (Character Crystals) come from {@code sneakycharacters.characterslots.bonus.<n>} (the highest n) and are added
 * on top, so a rank that raises the base never cancels slots a player bought.
 */
public final class CharacterSlots {

    public static final String BONUS_PREFIX = CharacterSelectionMenu.CHARACTER_SLOTS_PERMISSION_NODE + "bonus.";

    private CharacterSlots() {
    }

    /** Base slots for admins ({@code sneakycharacters.*}); they used to be unlimited. */
    public static final int ADMIN_BASE_SLOTS = 50;

    /** True for admins ({@code sneakycharacters.*}): their base is at least {@link #ADMIN_BASE_SLOTS}. */
    public static boolean isAdmin(@NotNull Player player) {
        return player.hasPermission(SneakyCharacterManager.IDENTIFIER + ".*");
    }

    /** Base plus bonus slots. */
    public static int max(@NotNull Player player) {
        int base = base(player);
        if (isAdmin(player)) base = Math.max(base, ADMIN_BASE_SLOTS);
        return base + bonus(player);
    }

    /** Highest {@code characterslots.<n>} the player has. */
    public static int base(@NotNull Player player) {
        return highest(player, CharacterSelectionMenu.CHARACTER_SLOTS_PERMISSION_NODE);
    }

    /** Highest {@code characterslots.bonus.<n>} the player has. */
    public static int bonus(@NotNull Player player) {
        return highest(player, BONUS_PREFIX);
    }

    private static int highest(Player player, String prefix) {
        int max = 0;
        for (PermissionAttachmentInfo permission : player.getEffectivePermissions()) {
            if (!permission.getValue()) continue;
            String node = permission.getPermission();
            if (!node.startsWith(prefix)) continue;
            Integer value = parseSlotValue(node.substring(prefix.length()));
            if (value != null && value > max) max = value;
        }
        return max;
    }

    /** Non-negative integer suffix, or null ({@code bonus.3}, {@code *} and junk are not base values). */
    static Integer parseSlotValue(String suffix) {
        try {
            int value = Integer.parseInt(suffix);
            return value >= 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
