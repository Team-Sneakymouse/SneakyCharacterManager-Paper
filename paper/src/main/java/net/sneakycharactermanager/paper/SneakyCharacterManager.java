package net.sneakycharactermanager.paper;

import java.io.File;

import org.bukkit.plugin.java.JavaPlugin;

import net.sneakycharactermanager.paper.commands.CommandChar;
import net.sneakycharactermanager.paper.commands.CommandSkin;

public class SneakyCharacterManager extends JavaPlugin {

    private static SneakyCharacterManager instance = null;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        if (!getCharacterDataFolder().exists()) {
            getCharacterDataFolder().mkdirs();
        }

        if (getConfig().getBoolean("deleteCharacterDataOnServerStart")) {
            deleteFolderContents(getCharacterDataFolder());
        }

        getServer().getCommandMap().register("sneakycharactermanager", new CommandChar());
        getServer().getCommandMap().register("sneakycharactermanager", new CommandSkin());
    }

    public static SneakyCharacterManager getInstance() {
        return instance;
    }

    private static File getCharacterDataFolder() {
        return new File(SneakyCharacterManager.getInstance().getServer().getWorldContainer(), "characterdata");
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