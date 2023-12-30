package net.sneakycharactermanager.paper;

import java.io.File;
import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;

import net.sneakycharactermanager.paper.commands.*;
import net.sneakycharactermanager.paper.listeners.*;
import net.sneakycharactermanager.paper.handlers.Placeholders;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.CharacterSelectionMenu;
import net.sneakycharactermanager.paper.handlers.nametags.NametagManager;
import net.sneakycharactermanager.paper.handlers.skins.SkinPreloader;
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;

public class SneakyCharacterManager extends JavaPlugin implements Listener {

    public static final String IDENTIFIER = "sneakycharacters";
    public static final String AUTHORS = "Team Sneakymouse";
    public static final String VERSION = "1.0.0";

    private static SneakyCharacterManager instance = null;
    private static Map<Player, Integer> taskIdMap = new HashMap<>();

    public NametagManager nametagManager;
    public CharacterSelectionMenu selectionMenu;
    public SkinQueue skinQueue;
    public SkinPreloader skinPreloader;

    @Override
    public void onEnable() {
        instance = this;
        nametagManager = new NametagManager();
        selectionMenu = new CharacterSelectionMenu();
        skinQueue = new SkinQueue();
        skinPreloader = new SkinPreloader();

        saveDefaultConfig();

        if (getConfig().getBoolean("deleteCharacterDataOnServerStart")) {
            deleteFolderContents(getCharacterDataFolder());
        }

        getServer().getCommandMap().register(IDENTIFIER, new CommandChar());
        getServer().getCommandMap().register(IDENTIFIER, new CommandSkin());
        getServer().getCommandMap().register(IDENTIFIER, new CommandNames());
        getServer().getCommandMap().register(IDENTIFIER, new CommandNick());
        getServer().getCommandMap().register(IDENTIFIER, new CommandCharadmin());
        getServer().getCommandMap().register(IDENTIFIER, new CommandCharScan());
        getServer().getCommandMap().register(IDENTIFIER, new CommandCleanNameplates());

        getServer().getMessenger().registerIncomingPluginChannel(this, "sneakymouse:" + IDENTIFIER, new BungeeMessageListener());
        getServer().getMessenger().registerOutgoingPluginChannel(this, "sneakymouse:" + IDENTIFIER);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new ConnectionEventListeners(), this);
        getServer().getPluginManager().registerEvents(selectionMenu, this);
        getServer().getPluginManager().registerEvents(new DeathListener(), this);
        getServer().getPluginManager().registerEvents(new GamemodeEvents(), this);
        getServer().getPluginManager().registerEvents(new VanishEvents(), this);

        getServer().getPluginManager().addPermission(new Permission(IDENTIFIER + ".*"));
        getServer().getPluginManager().addPermission(new Permission(CharacterSelectionMenu.CHARACTER_SLOTS_PERMISSION_NODE + "*"));

        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new Placeholders().register();
        }

        CommandCleanNameplates.cleanNameplates();

        for (Player player : getServer().getOnlinePlayers()) {
            int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
                if (!player.isOnline() || Character.isPlayedMapped(player)) {
                    Bukkit.getScheduler().cancelTask(taskIdMap.get(player));
                    taskIdMap.remove(player);
                } else {
                    BungeeMessagingUtil.sendByteArray("rebuildCharacterMap", player.getUniqueId());
                }
            }, 0, 20);
        
            taskIdMap.put(player, taskId);
        }

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            Character.saveAll();
        }, 0, 1200);
    }

    @Override
    public void onDisable() {
        Character.saveAll();
        this.nametagManager.unnickAll();
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

}