package net.sneakycharactermanager.paper;

import java.io.File;

import net.sneakycharactermanager.paper.commands.CommandTesting;
import net.sneakycharactermanager.paper.handlers.nametags.NametagManager;
import org.bukkit.plugin.java.JavaPlugin;

import net.sneakycharactermanager.paper.commands.CommandChar;
import net.sneakycharactermanager.paper.commands.CommandSkin;
import net.sneakycharactermanager.paper.listeners.BungeeMessageListener;
import net.sneakycharactermanager.paper.listeners.ConnectionEventListeners;

public class SneakyCharacterManager extends JavaPlugin {

    private static SneakyCharacterManager instance = null;

    public NametagManager nametagManager;

    @Override
    public void onEnable() {
        instance = this;
        nametagManager = new NametagManager();

        saveDefaultConfig();

        if (getConfig().getBoolean("deleteCharacterDataOnServerStart")) {
            deleteFolderContents(getCharacterDataFolder());
        }

        getServer().getCommandMap().register("sneakycharactermanager", new CommandChar());
        getServer().getCommandMap().register("sneakycharactermanager", new CommandSkin());
        getServer().getCommandMap().register("sneakycharactermanager", new CommandTesting());

        getServer().getMessenger().registerIncomingPluginChannel(this, "sneakymouse:sneakycharactermanager", new BungeeMessageListener());
        getServer().getMessenger().registerOutgoingPluginChannel(this, "sneakymouse:sneakycharactermanager");

        getServer().getPluginManager().registerEvents(new ConnectionEventListeners(), this);

        //TODO: When the plugin reloads, the characterMap in Character.java needs to be rebuilt
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