package net.sneakycharactermanager.paper.handlers.nametags;

import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeEqualityPredicate;
import net.luckperms.api.util.Tristate;
import net.sneakycharactermanager.paper.SneakyCharacterManager;

public final class NamesPreferenceHandler {

    private static final String NODE_ON = SneakyCharacterManager.IDENTIFIER + ".names.on";
    private static final String NODE_OFF = SneakyCharacterManager.IDENTIFIER + ".names.off";
    private static final String NODE_CHARACTER = SneakyCharacterManager.IDENTIFIER + ".names.character";

    private NamesPreferenceHandler() {
    }

    public static boolean isAvailable() {
        return SneakyCharacterManager.getInstance().luckPermsActive;
    }

    public static @NotNull NamesPreference get(@NotNull Player player) {
        @Nullable User user = LuckPermsProvider.get().getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return NamesPreference.CHARACTER;
        }

        // Only read preference nodes set directly on the user (e.g. via /names).
        // Inherited group nodes such as sneakycharacters.* must not affect this.
        NodeMap userData = user.data();
        if (userData.contains(Node.builder(NODE_OFF).build(), NodeEqualityPredicate.ONLY_KEY) == Tristate.TRUE) {
            return NamesPreference.OFF;
        }
        if (userData.contains(Node.builder(NODE_ON).build(), NodeEqualityPredicate.ONLY_KEY) == Tristate.TRUE) {
            return NamesPreference.ON;
        }
        return NamesPreference.CHARACTER;
    }

    public static void set(@NotNull Player player, @NotNull NamesPreference preference) {
        @NonNull LuckPerms luckPerms = LuckPermsProvider.get();
        @Nullable User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            throw new IllegalStateException("LuckPerms user not loaded for " + player.getName());
        }

        @NonNull NodeMap nodeMap = user.data();

        nodeMap.remove(Node.builder(NODE_ON).build());
        nodeMap.remove(Node.builder(NODE_OFF).build());
        nodeMap.remove(Node.builder(NODE_CHARACTER).build());

        if (preference == NamesPreference.ON) {
            nodeMap.add(Node.builder(NODE_ON).build());
        } else if (preference == NamesPreference.OFF) {
            nodeMap.add(Node.builder(NODE_OFF).build());
        }

        luckPerms.getUserManager().saveUser(user);
    }
}
