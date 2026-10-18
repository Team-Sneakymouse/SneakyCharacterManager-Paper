package net.sneakycharactermanager.paper.handlers.titles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import com.sneakyutils.util.TextUtility;

import net.kyori.adventure.text.Component;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.consolecommands.ConsoleCommandCharTemp;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.util.ProxyMessagingUtil;

public class TitleSelectionMenu implements Listener {

	private static final String CLEAR_KEY = "__clear__";
	private final NamespacedKey titleKey;

	public TitleSelectionMenu() {
		this.titleKey = new NamespacedKey(SneakyCharacterManager.getInstance(), "title_key");
	}

	public void open(Player player) {
		if (ConsoleCommandCharTemp.isPlayerTempChar(player.getUniqueId().toString())) {
			player.sendMessage(TextUtility.INSTANCE.convertToComponent("&4Template characters do not support titles."));
			return;
		}

		Character character = Character.get(player);
		if (character == null) {
			player.sendMessage(TextUtility.INSTANCE.convertToComponent("&cFailed to retrieve character."));
			return;
		}

		TitleRegistry registry = SneakyCharacterManager.getInstance().titleRegistry;
		List<Map.Entry<String, String>> unlocked = new ArrayList<>();
		for (Map.Entry<String, String> entry : registry.all().entrySet()) {
			if (player.hasPermission(TitleRegistry.permissionNode(entry.getKey()))) {
				unlocked.add(entry);
			}
		}

		int size = Math.min(54, Math.max(9, ((unlocked.size() + 1 + 8) / 9) * 9));
		TitleMenuHolder holder = new TitleMenuHolder(player, character.getTitle());
		Inventory inventory = Bukkit.createInventory(holder, size,
				TextUtility.INSTANCE.convertToComponent("&eSelect Title"));
		holder.setInventory(inventory);

		ItemStack clear = new ItemStack(Material.BARRIER);
		ItemMeta clearMeta = clear.getItemMeta();
		clearMeta.displayName(TextUtility.INSTANCE.convertToComponent("&cClear Title"));
		List<Component> clearLore = new ArrayList<>();
		clearLore.add(TextUtility.INSTANCE.convertToComponent("&7Remove the title from this character."));
		clearMeta.lore(clearLore);
		clearMeta.getPersistentDataContainer().set(titleKey, PersistentDataType.STRING, CLEAR_KEY);
		clear.setItemMeta(clearMeta);
		inventory.setItem(0, clear);

		int slot = 1;
		String current = character.getTitle() == null ? "" : character.getTitle();
		for (Map.Entry<String, String> entry : unlocked) {
			if (slot >= size) {
				break;
			}
			ItemStack item = new ItemStack(Material.NAME_TAG);
			ItemMeta meta = item.getItemMeta();
			boolean selected = entry.getKey().equals(current);
			meta.displayName(TextUtility.INSTANCE.convertToComponent((selected ? "&a" : "&e") + entry.getValue()));
			List<Component> lore = new ArrayList<>();
			lore.add(TextUtility.INSTANCE.convertToComponent("&7" + entry.getKey()));
			if (selected) {
				lore.add(TextUtility.INSTANCE.convertToComponent("&aCurrently equipped"));
			} else {
				lore.add(TextUtility.INSTANCE.convertToComponent("&eClick to equip"));
			}
			meta.lore(lore);
			meta.getPersistentDataContainer().set(titleKey, PersistentDataType.STRING, entry.getKey());
			item.setItemMeta(meta);
			inventory.setItem(slot++, item);
		}

		player.openInventory(inventory);
	}

	@EventHandler
	public void onClick(InventoryClickEvent event) {
		if (!(event.getInventory().getHolder() instanceof TitleMenuHolder)) {
			return;
		}
		event.setCancelled(true);
		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}
		ItemStack clicked = event.getCurrentItem();
		if (clicked == null || !clicked.hasItemMeta()) {
			return;
		}
		String key = clicked.getItemMeta().getPersistentDataContainer().get(titleKey, PersistentDataType.STRING);
		if (key == null) {
			return;
		}

		Character character = Character.get(player);
		if (character == null) {
			player.closeInventory();
			return;
		}

		if (CLEAR_KEY.equals(key)) {
			applyTitle(player, character, "");
			player.sendMessage(TextUtility.INSTANCE.convertToComponent("&aCleared title for this character."));
		} else {
			if (!player.hasPermission(TitleRegistry.permissionNode(key))) {
				player.sendMessage(TextUtility.INSTANCE.convertToComponent("&cYou no longer have that title unlocked."));
				player.closeInventory();
				return;
			}
			String display = SneakyCharacterManager.getInstance().titleRegistry.getDisplay(key);
			applyTitle(player, character, key);
			player.sendMessage(TextUtility.INSTANCE.convertToComponent(
					"&aEquipped title &b" + (display == null ? key : display) + "&a."));
		}
		player.closeInventory();
	}

	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		// holder is GC'd with inventory; nothing to clean
	}

	public static void applyTitle(Player player, Character character, String titleKey) {
		String key = (titleKey == null || titleKey.isEmpty())
				? ""
				: TitleRegistry.normalizeTitleKey(titleKey);
		character.setTitle(key);
		ProxyMessagingUtil.sendByteArray(player, "updateCharacter", player.getUniqueId().toString(),
				character.getCharacterUUID(), 7, key);
		SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(player, character.getDisplayName());
	}

	public static final class TitleMenuHolder implements InventoryHolder {
		private final Player player;
		private final String currentTitle;
		private Inventory inventory;

		public TitleMenuHolder(Player player, String currentTitle) {
			this.player = player;
			this.currentTitle = currentTitle;
		}

		void setInventory(Inventory inventory) {
			this.inventory = inventory;
		}

		public Player getPlayer() {
			return player;
		}

		public String getCurrentTitle() {
			return currentTitle;
		}

		@Override
		public @NotNull Inventory getInventory() {
			return inventory;
		}
	}
}
