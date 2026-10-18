package net.sneakycharactermanager.paper.handlers.titles;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.sneakycharactermanager.paper.SneakyCharacterManager;

public final class TitlePermissionGranter {

	private TitlePermissionGranter() {
	}

	public static boolean isAvailable() {
		return SneakyCharacterManager.getInstance().luckPermsActive;
	}

	public static void grant(@NotNull Player player, @NotNull String titleKey) {
		LuckPerms luckPerms = LuckPermsProvider.get();
		User user = luckPerms.getUserManager().getUser(player.getUniqueId());
		if (user == null) {
			throw new IllegalStateException("LuckPerms user not loaded for " + player.getName());
		}
		String node = TitleRegistry.permissionNode(titleKey);
		user.data().add(Node.builder(node).build());
		luckPerms.getUserManager().saveUser(user);
	}
}
