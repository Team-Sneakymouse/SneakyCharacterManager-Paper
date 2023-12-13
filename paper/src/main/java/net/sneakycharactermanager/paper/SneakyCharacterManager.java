package net.sneakycharactermanager.paper;

import java.io.File;
import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import net.sneakycharactermanager.paper.commands.CommandChar;
import net.sneakycharactermanager.paper.commands.CommandNames;
import net.sneakycharactermanager.paper.commands.CommandNick;
import net.sneakycharactermanager.paper.commands.CommandSkin;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.CharacterSelectionMenu;
import net.sneakycharactermanager.paper.handlers.nametags.NametagManager;
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;
import net.sneakycharactermanager.paper.listeners.BungeeMessageListener;
import net.sneakycharactermanager.paper.listeners.ConnectionEventListeners;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;

public class SneakyCharacterManager extends JavaPlugin implements Listener {

    private static SneakyCharacterManager instance = null;
    private static Map<Player, Integer> taskIdMap = new HashMap<>();

    public NametagManager nametagManager;
    public CharacterSelectionMenu selectionMenu;
    public SkinQueue skinQueue;

    @Override
    public void onEnable() {
        instance = this;
        nametagManager = new NametagManager();
        selectionMenu = new CharacterSelectionMenu();
        skinQueue = new SkinQueue();

        saveDefaultConfig();

        if (getConfig().getBoolean("deleteCharacterDataOnServerStart")) {
            deleteFolderContents(getCharacterDataFolder());
        }

        getServer().getCommandMap().register("sneakycharactermanager", new CommandChar());
        getServer().getCommandMap().register("sneakycharactermanager", new CommandSkin());
        getServer().getCommandMap().register("sneakycharactermanager", new CommandNames());
        getServer().getCommandMap().register("sneakycharactermanager", new CommandNick());

        getServer().getMessenger().registerIncomingPluginChannel(this, "sneakymouse:sneakycharactermanager", new BungeeMessageListener());
        getServer().getMessenger().registerOutgoingPluginChannel(this, "sneakymouse:sneakycharactermanager");

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new ConnectionEventListeners(), this);
        getServer().getPluginManager().registerEvents(selectionMenu, this);

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
        for (int taskID : taskIdMap.values()) {
            Bukkit.getScheduler().cancelTask(taskID);
        }

        Character.saveAll();
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