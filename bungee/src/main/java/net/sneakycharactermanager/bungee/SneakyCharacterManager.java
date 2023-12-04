package net.sneakycharactermanager.bungee;

import java.io.File;

import net.md_5.bungee.api.plugin.Plugin;
import net.sneakycharactermanager.bungee.listeners.PluginMessageListener;

public class SneakyCharacterManager extends Plugin {

    private static SneakyCharacterManager instance;

    @Override
    public void onEnable() {
        instance = this;

        getProxy().registerChannel("sneakymouse:sneakycharactermanager");
        getProxy().getPluginManager().registerListener(this, new PluginMessageListener());
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

}