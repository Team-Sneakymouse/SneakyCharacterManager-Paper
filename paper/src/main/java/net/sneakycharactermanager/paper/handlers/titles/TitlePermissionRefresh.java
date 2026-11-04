package net.sneakycharactermanager.paper.handlers.titles;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.sneakycharactermanager.paper.SneakyCharacterManager;

/**
 * Re-resolves a player's title line when LuckPerms recalculates their permissions, so a revoked (or re-granted)
 * {@code sneakycharacters.title.<key>} takes effect without waiting for the next plate rebuild.
 */
public final class TitlePermissionRefresh {

	private TitlePermissionRefresh() {
	}

	/** LuckPerms may post this event off the main thread; the manager hops back before touching Bukkit state. */
	public static EventSubscription<UserDataRecalculateEvent> register(SneakyCharacterManager plugin) {
		return LuckPermsProvider.get().getEventBus().subscribe(plugin, UserDataRecalculateEvent.class,
				event -> plugin.nametagManager.refreshTitleSoon(event.getUser().getUniqueId()));
	}
}
