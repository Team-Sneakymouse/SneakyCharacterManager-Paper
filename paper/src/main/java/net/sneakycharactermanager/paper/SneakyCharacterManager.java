package net.sneakycharactermanager.paper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
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
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;
import net.sneakycharactermanager.paper.handlers.skins.SkinStateManager;
import net.sneakycharactermanager.paper.handlers.titles.TitleRegistry;
import net.sneakycharactermanager.paper.handlers.titles.TitleSelectionMenu;
import net.sneakycharactermanager.paper.util.ProxyMessagingUtil;
import net.sneakycharactermanager.paper.storage.StorageMode;
import net.sneakycharactermanager.paper.storage.CharacterBackend;
import net.sneakycharactermanager.paper.storage.SneakyDbBootstrap;
import net.sneakycharactermanager.common.io.AsyncAtomicFileWriter;
public class SneakyCharacterManager extends JavaPlugin implements Listener {

	public static final String IDENTIFIER = "sneakycharacters";
	public static final String AUTHORS = "Team Sneakymouse";
	public static final String VERSION = BuildVersion.VALUE;

	private static SneakyCharacterManager instance = null;
	private PrivateKey privateKey;
	private PublicKey publicKey;
	private final AsyncAtomicFileWriter fileWriter = new AsyncAtomicFileWriter("sneakycharacters-file-writer");

	private static Map<Player, Integer> taskIdMap = new HashMap<>();
	public boolean papiActive = false;
	public boolean luckPermsActive = false;

	public NametagManager nametagManager;
	public CharacterSelectionMenu selectionMenu;
	public SkinQueue skinQueue;
	public SkinStateManager skinStateManager;
	public NameTagRefresher nameTagRefresher;
	public TitleRegistry titleRegistry;
	public TitleSelectionMenu titleSelectionMenu;
	private Object svcIntegration;
    private StorageMode storageMode = StorageMode.LEGACY;
    private CharacterBackend characterBackend;
    public CharacterBackend getCharacterBackend() { return characterBackend; }
    public StorageMode getStorageMode() { return storageMode; }
    public boolean validateStorageReload() {
        try {
            return StorageMode.parse(getConfig().getString("storage.backend")) == storageMode;
        } catch (IllegalArgumentException error) { return false; }
    }


	@Override
	public void onEnable() {
		instance = this;
		nametagManager = new NametagManager();
		selectionMenu = new CharacterSelectionMenu();
		skinQueue = new SkinQueue();
		skinStateManager = new SkinStateManager();
		nameTagRefresher = new NameTagRefresher();
		titleRegistry = new TitleRegistry();
		titleSelectionMenu = new TitleSelectionMenu();

		saveDefaultConfig();
        try {
            storageMode = StorageMode.parse(getConfig().getString("storage.backend"));
            if (storageMode == StorageMode.SNEAKYDB) {
                Object configuredTimeout = getConfig().get("storage.requestTimeoutSeconds", 15);
                if (!(configuredTimeout instanceof Integer))
                    throw new IllegalArgumentException("storage.requestTimeoutSeconds must be an integer 1..60");
                int timeout = (Integer) configuredTimeout;
                if (timeout < 1 || timeout > 60) throw new IllegalArgumentException("storage.requestTimeoutSeconds must be 1..60");
                characterBackend = SneakyDbBootstrap.open(this, timeout);
            } else loadKeys();
        } catch (Exception | LinkageError error) {
            getLogger().severe("SCM storage initialization refused: " + error.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

		if (getConfig().getBoolean("deleteCharacterDataOnServerStart", false)) {
			deleteFolderContents(getCharacterDataFolder());
		}

		getServer().getCommandMap().register(IDENTIFIER, new CommandChar());
		getServer().getCommandMap().register(IDENTIFIER, new CommandCharGender());
		getServer().getCommandMap().register(IDENTIFIER, new CommandSkin());
		getServer().getCommandMap().register(IDENTIFIER, new CommandNames());
		getServer().getCommandMap().register(IDENTIFIER, new CommandNick());
		getServer().getCommandMap().register(IDENTIFIER, new CommandHideName());
		getServer().getCommandMap().register(IDENTIFIER, new CommandTitle());

		getServer().getCommandMap().register(IDENTIFIER, new CommandCharAdmin());
		getServer().getCommandMap().register(IDENTIFIER, new CommandCharScan());
		getServer().getCommandMap().register(IDENTIFIER, new CommandCharTag());
		getServer().getCommandMap().register(IDENTIFIER, new CommandCharPrefix());
		getServer().getCommandMap().register(IDENTIFIER, new CommandUniform());
		getServer().getCommandMap().register(IDENTIFIER, new CommandSaveTemplateChar());
		getServer().getCommandMap().register(IDENTIFIER, new CommandUserify());
		getServer().getCommandMap().register(IDENTIFIER, new CommandMigrateInventories());
		getServer().getCommandMap().register(IDENTIFIER, new CommandSkinQueue());
		getServer().getCommandMap().register(IDENTIFIER, new CommandTitleGrant());
		getServer().getCommandMap().register(IDENTIFIER, new CommandCharReload());

		getServer().getCommandMap().register(IDENTIFIER, new ConsoleCommandCharDisable());
		getServer().getCommandMap().register(IDENTIFIER, new ConsoleCommandCharEnable());
		getServer().getCommandMap().register(IDENTIFIER, new ConsoleCommandCharTemp());

        if (storageMode == StorageMode.LEGACY) {
            getServer().getMessenger().registerIncomingPluginChannel(this, "sneakymouse:" + IDENTIFIER, new ProxyMessageListener());
            getServer().getMessenger().registerOutgoingPluginChannel(this, "sneakymouse:" + IDENTIFIER);
        }

		getServer().getPluginManager().registerEvents(this, this);
		getServer().getPluginManager().registerEvents(new ConnectionEventListeners(), this);
		getServer().getPluginManager().registerEvents(selectionMenu, this);
		getServer().getPluginManager().registerEvents(titleSelectionMenu, this);
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
		getServer().getPluginManager().addPermission(new Permission(IDENTIFIER + ".names.on"));
		getServer().getPluginManager().addPermission(new Permission(IDENTIFIER + ".names.off"));
		getServer().getPluginManager().addPermission(new Permission(IDENTIFIER + ".names.character"));
		getServer().getPluginManager().addPermission(new Permission(IDENTIFIER + ".title.*"));

		if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
			papiActive = true;
			new Placeholders().register();
		}

		if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
			luckPermsActive = true;
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
					ProxyMessagingUtil.sendByteArray(player, "playerJoin", player.getUniqueId().toString());
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
            if (characterBackend != null) characterBackend.close();
			Character.saveAll();
			this.nametagManager.unnickAll();

			Bukkit.getScheduler().cancelTasks(this);
			Bukkit.getAsyncScheduler().cancelTasks(this);
			teardownVoiceChatIntegration();
			if (!fileWriter.shutdown(Duration.ofSeconds(30))) {
				getLogger().severe("Timed out while waiting for pending character data writes");
			}
		}
	}

	public CompletableFuture<Void> writeFile(Path target, String content) {
		return reportWriteFailure(target, fileWriter.writeUtf8(target, content));
	}

	public CompletableFuture<Void> updateFile(Path target, AsyncAtomicFileWriter.Utf8Update update) {
		return reportWriteFailure(target, fileWriter.updateUtf8(target, update));
	}

	private CompletableFuture<Void> reportWriteFailure(Path target, CompletableFuture<Void> write) {
		write.whenComplete((ignored, failure) -> {
			if (failure != null) {
				getLogger().log(Level.SEVERE, "Failed to save " + target.toAbsolutePath(), failure);
			}
		});
		return write;
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
