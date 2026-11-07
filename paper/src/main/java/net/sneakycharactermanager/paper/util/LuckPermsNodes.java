package net.sneakycharactermanager.paper.util;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.sneakycharactermanager.paper.SneakyCharacterManager;

/** LuckPerms user-node helpers. Only call when {@code SneakyCharacterManager#luckPermsActive} is true. */
public final class LuckPermsNodes {

	private LuckPermsNodes() {
	}

	/** The loaded LuckPerms user for an online player, or null. */
	public static User user(@NotNull Player player) {
		return LuckPermsProvider.get().getUserManager().getUser(player.getUniqueId());
	}

	/**
	 * Adds a permanent, global {@code true} node to the player and saves the user.
	 *
	 * @return the save future; it completes exceptionally when the storage backend fails
	 * @throws IllegalStateException when the player's LuckPerms user is not loaded
	 */
	public static CompletableFuture<Void> addPermanent(@NotNull Player player, @NotNull String node) {
		LuckPerms luckPerms = LuckPermsProvider.get();
		User user = luckPerms.getUserManager().getUser(player.getUniqueId());
		if (user == null) throw new IllegalStateException("LuckPerms user not loaded for " + player.getName());
		user.data().add(Node.builder(node).build());
		return luckPerms.getUserManager().saveUser(user).whenComplete((ok, error) -> {
			if (error != null) {
				SneakyCharacterManager.getInstance().getLogger().log(Level.WARNING,
						"Failed to save LuckPerms node " + node + " for " + player.getName(), error);
			}
		});
	}

	/**
	 * Highest integer n among the user's own permanent, context-free {@code true} nodes {@code <prefix><n>}
	 * (inherited, temporary and context-limited nodes are ignored), or 0.
	 */
	public static int highestOwnPermanentSuffix(@NotNull User user, @NotNull String prefix) {
		int max = 0;
		for (Node node : user.getNodes()) {
			if (!node.getValue() || node.hasExpiry() || !node.getContexts().isEmpty()) continue;
			String key = node.getKey();
			if (!key.startsWith(prefix)) continue;
			try {
				int value = Integer.parseInt(key.substring(prefix.length()));
				if (value > max) max = value;
			} catch (NumberFormatException ignored) {
			}
		}
		return max;
	}
}
