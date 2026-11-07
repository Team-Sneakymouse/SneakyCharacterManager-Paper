package net.sneakycharactermanager.paper.handlers.titles;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.util.LuckPermsNodes;

public final class TitlePermissionGranter {

	private TitlePermissionGranter() {
	}

	public static boolean isAvailable() {
		return SneakyCharacterManager.getInstance().luckPermsActive;
	}

	/** @throws IllegalStateException when the player's LuckPerms user is not loaded */
	public static void grant(@NotNull Player player, @NotNull String titleKey) {
		LuckPermsNodes.addPermanent(player, TitleRegistry.permissionNode(titleKey));
	}
}
