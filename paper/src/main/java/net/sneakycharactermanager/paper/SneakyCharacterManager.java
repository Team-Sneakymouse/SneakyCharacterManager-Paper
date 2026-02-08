package net.sneakycharactermanager.paper;

import java.util.HashMap;
import java.util.Map;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.security.PublicKey;
import java.security.PrivateKey;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;

import net.sneakycharactermanager.paper.admincommands.*;
import net.sneakycharactermanager.paper.commands.*;
import net.sneakycharactermanager.paper.consolecommands.*;
import net.sneakycharactermanager.paper.listeners.*;
import net.sneakycharactermanager.paper.handlers.ContextCalculatorCharacterTag;
import net.sneakycharactermanager.paper.handlers.Placeholders;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.CharacterSelectionMenu;
import net.sneakycharactermanager.paper.handlers.nametags.NameTagRefresher;
import net.sneakycharactermanager.paper.handlers.nametags.NametagManager;
import net.sneakycharactermanager.paper.handlers.skins.SkinPreloader;
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
public class SneakyCharacterManager extends JavaPlugin implements Listener {

	public static final String IDENTIFIER = "sneakycharacters";
	public static final String AUTHORS = "Team Sneakymouse";
	public static final String VERSION = "1.0.0";

	private static SneakyCharacterManager instance = null;
	private PrivateKey privateKey;
	private PublicKey publicKey;

	private static Map<Player, Integer> taskIdMap = new HashMap<>();
	public boolean papiActive = false;

	public NametagManager nametagManager;
	public CharacterSelectionMenu selectionMenu;
	public SkinQueue skinQueue;
	public SkinPreloader skinPreloader;
	public NameTagRefresher nameTagRefresher;
	private Object svcIntegration;

	@Override
	public void onEnable() {
		instance = this;
		nametagManager = new NametagManager();
		selectionMenu = new CharacterSelectionMenu();
		skinQueue = new SkinQueue();
		skinPreloader = new SkinPreloader();
		nameTagRefresher = new NameTagRefresher();

		saveDefaultConfig();
		loadKeys();

		if (getConfig().getBoolean("deleteCharacterDataOnServerStart", false)) {
			deleteFolderContents(getCharacterDataFolder());
		}

		getServer().getCommandMap().register(IDENTIFIER, new CommandChar());
		getServer().getCommandMap().register(IDENTIFIER, new CommandCharGender());
		getServer().getCommandMap().register(IDENTIFIER, new CommandSkin());
		getServer().getCommandMap().register(IDENTIFIER, new CommandNames());
		getServer().getCommandMap().register(IDENTIFIER, new CommandNick());

		getServer().getCommandMap().register(IDENTIFIER, new CommandCharAdmin());
		getServer().getCommandMap().register(IDENTIFIER, new CommandCharScan());
		getServer().getCommandMap().register(IDENTIFIER, new CommandCharTag());
		getServer().getCommandMap().register(IDENTIFIER, new CommandCharPrefix());
		getServer().getCommandMap().register(IDENTIFIER, new CommandUniform());
		getServer().getCommandMap().register(IDENTIFIER, new CommandSaveTemplateChar());
		getServer().getCommandMap().register(IDENTIFIER, new CommandUserify());
		getServer().getCommandMap().register(IDENTIFIER, new CommandMigrateInventories());

		getServer().getCommandMap().register(IDENTIFIER, new ConsoleCommandCharDisable());
		getServer().getCommandMap().register(IDENTIFIER, new ConsoleCommandCharEnable());
		getServer().getCommandMap().register(IDENTIFIER, new ConsoleCommandCharTemp());

		getServer().getMessenger().registerIncomingPluginChannel(this, "sneakymouse:" + IDENTIFIER,
				new BungeeMessageListener());
		getServer().getMessenger().registerOutgoingPluginChannel(this, "sneakymouse:" + IDENTIFIER);

		getServer().getPluginManager().registerEvents(this, this);
		getServer().getPluginManager().registerEvents(new ConnectionEventListeners(), this);
		getServer().getPluginManager().registerEvents(selectionMenu, this);
		getServer().getPluginManager().registerEvents(new DeathListener(), this);
		getServer().getPluginManager().registerEvents(new GamemodeEvents(), this);
		getServer().getPluginManager().registerEvents(new VanishEvents(), this);
		getServer().getPluginManager().registerEvents(new TeleportEvents(), this);

		getServer().getPluginManager().addPermission(new Permission(IDENTIFIER + ".*"));
		getServer().getPluginManager()
				.addPermission(new Permission(CharacterSelectionMenu.CHARACTER_SLOTS_PERMISSION_NODE + "*"));
		getServer().getPluginManager().addPermission(new Permission(IDENTIFIER + ".character.*"));
		getServer().getPluginManager().addPermission(new Permission(IDENTIFIER + ".command.*"));
		getServer().getPluginManager().addPermission(new Permission(IDENTIFIER + ".admin.*"));
		getServer().getPluginManager().addPermission(new Permission(IDENTIFIER + ".admin.command.*"));
		getServer().getPluginManager().addPermission(new Permission(IDENTIFIER + ".admin.bypass.*"));
		getServer().getPluginManager().addPermission(new Permission(IDENTIFIER + ".skinfetch.others"));

		if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
			papiActive = true;
			new Placeholders().register();
		}

		if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
			new ContextCalculatorCharacterTag().register();
		}

		// Optional Simple Voice Chat integration (soft-depend)
		initVoiceChatIntegration();

		for (Player player : getServer().getOnlinePlayers()) {
			int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
				if (!player.isOnline() || Character.isPlayedMapped(player)) {
					Bukkit.getScheduler().cancelTask(taskIdMap.get(player));
					taskIdMap.remove(player);
				} else {
					BungeeMessagingUtil.sendByteArray(player, "playerJoin", player.getUniqueId().toString());
				}
			}, 0, 20);

			taskIdMap.put(player, taskId);
		}

		if (getConfig().getBoolean("respawnNameTags", false)) {
			int respawnTimer = getConfig().getInt("respawnTimerSeconds", 600);
			respawnTimer = respawnTimer * 20;

			Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
				for (Player player : Bukkit.getOnlinePlayers()) {
					Character character = Character.get(player);
					if (character == null)
						continue;

					nametagManager.unnicknamePlayer(player);
					nametagManager.nicknamePlayer(player, character.getDisplayName());
				}
			}, respawnTimer, respawnTimer);
		}

		Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
			Character.saveAll();
		}, 0, 1200);
	}

	@EventHandler
	public void onPluginDisable(PluginDisableEvent event) {
		if (event.getPlugin() == this) {
			Character.saveAll();
			this.nametagManager.unnickAll();

			Bukkit.getScheduler().cancelTasks(this);
			Bukkit.getAsyncScheduler().cancelTasks(this);
			teardownVoiceChatIntegration();
		}
	}

	private void initVoiceChatIntegration() {
		try {
			if (Bukkit.getPluginManager().getPlugin("voicechat") == null) {
				getLogger().info("[SVC] Voicechat plugin not present; skipping integration");
				return;
			}
			Class<?> svcClass = Class.forName("de.maxhenkel.voicechat.api.BukkitVoicechatService");
			Object service = Bukkit.getServicesManager().load(svcClass);
			if (service == null) {
				getLogger().info("[SVC] Voicechat service not found; skipping integration");
				return;
			}
			Class<?> pluginIface = Class.forName("de.maxhenkel.voicechat.api.VoicechatPlugin");
			Class<?> implClass = Class.forName("net.sneakycharactermanager.paper.handlers.voice.SimpleVoiceChatIntegration");
			Object impl = implClass.getConstructor().newInstance();
			svcClass.getMethod("registerPlugin", pluginIface).invoke(service, impl);
			svcIntegration = impl;
			getLogger().info("[SVC] Registered SimpleVoiceChatIntegration with voicechat service");
		} catch (ClassNotFoundException e) {
			getLogger().info("[SVC] Voicechat API not on classpath; skipping integration");
		} catch (Exception ex) {
			getLogger().warning("[SVC] Failed to register SimpleVoiceChatIntegration: " + ex.getMessage());
		}
	}

	private void teardownVoiceChatIntegration() {
		if (svcIntegration == null) return;
		try {
			Class<?> pluginIface = Class.forName("de.maxhenkel.voicechat.api.VoicechatPlugin");
			Bukkit.getServicesManager().unregister(pluginIface, svcIntegration);
			getLogger().info("[SVC] Unregistered SimpleVoiceChatIntegration");
		} catch (Throwable ignored) {
		} finally {
			svcIntegration = null;
		}
	}

	public static SneakyCharacterManager getInstance() {
		return instance;
	}

	public static File getCharacterDataFolder() {
		File dir = new File(getInstance().getDataFolder(), "characterdata");

		if (!dir.exists()) {
			dir.mkdirs();
		}

		return dir;
	}

	public static File getUniformFolder() {
		File dir = new File(getInstance().getDataFolder(), "uniforms");

		if (!dir.exists()) {
			dir.mkdirs();
		}

		return dir;
	}

	private static void deleteFolderContents(File folder) {
		if (folder.isDirectory()) {
			for (File file : folder.listFiles()) {
				if (file.isDirectory()) {
					deleteFolderContents(file);
				}
				file.delete();
			}
		}
	}

	private void loadKeys() {
		File keyFile = new File(getDataFolder(), "keys.ser");
		if (!keyFile.exists()) {
			getLogger().severe("Key file not found: " + keyFile.getPath());
			return;
		}

		try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(keyFile))) {
			this.privateKey = (PrivateKey) ois.readObject();
			this.publicKey = (PublicKey) ois.readObject();
		} catch (IOException | ClassNotFoundException e) {
			getLogger().severe("Failed to load keys: " + e.getMessage());
		}
	}

	public PrivateKey getPrivateKey() {
		return privateKey;
	}

	public PublicKey getPublicKey() {
		return publicKey;
	}

}