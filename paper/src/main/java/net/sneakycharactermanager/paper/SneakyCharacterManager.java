package net.sneakycharactermanager.paper;

import java.io.File;

import org.bukkit.plugin.java.JavaPlugin;

import net.sneakycharactermanager.paper.commands.CommandChar;
import net.sneakycharactermanager.paper.commands.CommandSkin;
import net.sneakycharactermanager.paper.listeners.BungeeMessageListener;
import net.sneakycharactermanager.paper.listeners.EventListeners;

public class SneakyCharacterManager extends JavaPlugin {

    private static SneakyCharacterManager instance = null;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        if (getConfig().getBoolean("deleteCharacterDataOnServerStart")) {
            deleteFolderContents(getCharacterDataFolder());
        }

        getServer().getCommandMap().register("sneakycharactermanager", new CommandChar());
        getServer().getCommandMap().register("sneakycharactermanager", new CommandSkin());

        getServer().getPluginManager().registerEvents(new EventListeners(), this);

        getServer().getMessenger().registerIncomingPluginChannel(this, "SneakyCharacterManager", new BungeeMessageListener());
    }

    public static SneakyCharacterManager getInstance() {
        return instance;
    }

    private static File getCharacterDataFolder() {
        File dir = new File(SneakyCharacterManager.getInstance().getServer().getWorldContainer(), "characterdata");

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