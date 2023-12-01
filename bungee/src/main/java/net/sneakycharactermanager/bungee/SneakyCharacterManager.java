package net.sneakycharactermanager.bungee;

import net.md_5.bungee.api.plugin.Plugin;
import net.sneakycharactermanager.bungee.listeners.PluginMessageListener;

public class SneakyCharacterManager extends Plugin {

    @Override
    public void onEnable() {
        getProxy().registerChannel("SneakyCharacterManager");
        getProxy().getPluginManager().registerListener(this, new PluginMessageListener());
    }

}