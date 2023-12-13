package net.sneakycharactermanager.paper;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import net.sneakycharactermanager.paper.commands.*;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.CharacterSelectionMenu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;

import net.md_5.bungee.event.EventHandler;
import net.sneakycharactermanager.paper.listeners.BungeeMessageListener;
import net.sneakycharactermanager.paper.listeners.ConnectionEventListeners;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.handlers.nametags.NametagManager;

public class SneakyCharacterManager extends JavaPlugin implements Listener {

    private static SneakyCharacterManager instance = null;
    private static Map<Player, Integer> taskIdMap = new HashMap<Player, Integer>();

    public NametagManager nametagManager;
    public CharacterSelectionMenu selectionMenu;

    @Override
    public void onEnable() {
        instance = this;
        nametagManager = new NametagManager();
        selectionMenu = new CharacterSelectionMenu();

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