package net.sneakycharactermanager.paper.handlers.character;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.SneakyCharacterManager;

/** Character slot count from {@code sneakycharacters.characterslots.<n>} permissions (the highest n wins). */
public final class CharacterSlots {

    private CharacterSlots() {
    }

    /** True when the player has no slot limit ({@code sneakycharacters.*} or {@code characterslots.*}). */
    public static boolean isUnlimited(@NotNull Player player) {
        return player.hasPermission(SneakyCharacterManager.IDENTIFIER + ".*")
                || player.hasPermission(CharacterSelectionMenu.CHARACTER_SLOTS_PERMISSION_NODE + "*")
                || player.hasPermission(CharacterSelectionMenu.CHARACTER_SLOTS_PERMISSION_NODE + ".*");
    }

    /** Highest granted slot count, or {@link Integer#MAX_VALUE} when unlimited. */
    public static int max(@NotNull Player player) {
        if (isUnlimited(player)) return Integer.MAX_VALUE;
        return parseMax(player);
    }

    /** Highest numeric slot node, ignoring wildcards. */
    public static int parseMax(@NotNull Player player) {
        int max = 0;
        for (PermissionAttachmentInfo permission : player.getEffectivePermissions()) {
            if (!permission.getValue()) continue;
            String node = permission.getPermission();
            if (!node.startsWith(CharacterSelectionMenu.CHARACTER_SLOTS_PERMISSION_NODE)) continue;
            Integer value = parseSlotValue(node.substring(CharacterSelectionMenu.CHARACTER_SLOTS_PERMISSION_NODE.length()));
            if (value != null && value > max) max = value;
        }
        return max;
    }

    static Integer parseSlotValue(String suffix) {
        try {
            int value = Integer.parseInt(suffix);
            return value >= 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
