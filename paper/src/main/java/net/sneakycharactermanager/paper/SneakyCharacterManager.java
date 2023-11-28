package net.sneakycharactermanager.paper;

import org.bukkit.plugin.java.JavaPlugin;

import net.sneakycharactermanager.paper.commands.CommandChar;
import net.sneakycharactermanager.paper.commands.CommandSkin;

public class SneakyCharacterManager extends JavaPlugin {

    private static SneakyCharacterManager instance = null;

    @Override
    public void onEnable() {
        instance = this;

        getServer().getCommandMap().register("sneakycharactermanager", new CommandChar("char"));
        getServer().getCommandMap().register("sneakycharactermanager", new CommandSkin("skin"));
    }

    public static SneakyCharacterManager getInstance() {
        return instance;
    }

}