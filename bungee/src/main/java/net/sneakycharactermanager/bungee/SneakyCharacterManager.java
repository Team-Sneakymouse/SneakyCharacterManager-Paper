package net.sneakycharactermanager.bungee;

import net.md_5.bungee.api.plugin.Plugin;
import net.sneakycharactermanager.bungee.listeners.ConnectionEventListeners;
import net.sneakycharactermanager.bungee.listeners.PluginMessageListener;
import net.sneakycharactermanager.proxy.common.ProxyConstants;
import net.sneakycharactermanager.proxy.core.ProxyCore;
import net.sneakycharactermanager.proxy.core.ProxyKeys;

public class SneakyCharacterManager extends Plugin {

    private static SneakyCharacterManager instance;

    private ProxyCore core;

    @Override
    public void onEnable() {
        instance = this;

        ProxyKeys keys = ProxyKeys.loadOrGenerate(getDataFolder(), new BungeeLogger(getLogger()));
        core = new ProxyCore(new BungeePlatform(this), keys);

        getProxy().registerChannel(ProxyConstants.CHANNEL);
        getProxy().getPluginManager().registerListener(this, new PluginMessageListener(core));
        getProxy().getPluginManager().registerListener(this, new ConnectionEventListeners(core));
    }

    public static SneakyCharacterManager getInstance() {
        return instance;
    }
}