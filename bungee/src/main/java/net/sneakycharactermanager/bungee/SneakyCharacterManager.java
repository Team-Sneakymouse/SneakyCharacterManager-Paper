package net.sneakycharactermanager.bungee;

import net.md_5.bungee.api.plugin.Plugin;
import net.sneakycharactermanager.bungee.listeners.PluginMessageListener;

public class SneakyCharacterManager extends Plugin {

    private static SneakyCharacterManager instance;

    @Override
    public void onEnable() {
        instance = this;

        getProxy().registerChannel("SneakyCharacterManager");
        getProxy().getPluginManager().registerListener(this, new PluginMessageListener());
    }

    public static SneakyCharacterManager getInstance() {
        return instance;
    }

}