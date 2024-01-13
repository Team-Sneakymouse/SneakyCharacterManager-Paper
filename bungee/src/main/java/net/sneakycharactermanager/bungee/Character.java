package net.sneakycharactermanager.bungee;

import java.util.UUID;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.config.Configuration;
import net.sneakycharactermanager.bungee.util.PaperMessagingUtil;

public class Character {
    private final String uuid;
    private boolean enabled;
    private String name;
    private String skin;
    private boolean slim;

    public Character(String uuid, Configuration config) {
        this.uuid = uuid;
        this.enabled = config.getBoolean("enabled");
        this.name = config.getString("name");
        this.skin = config.getString("skin");
        this.slim = config.getBoolean("slim");
    }

    public Character(String name) {
        this.uuid = UUID.randomUUID().toString();
        this.enabled = true;
        this.name = name;
        this.skin = "";
        this.slim = false;
    }

    public void loadCharacter(String subChannel, ServerInfo serverInfo, String playerUUID, boolean forced) {
        PaperMessagingUtil.sendByteArray(serverInfo, subChannel, playerUUID, this, forced);
        if (this.skin.isEmpty()) {
            PaperMessagingUtil.sendByteArray(serverInfo, "defaultSkin", playerUUID, this.uuid);
        }
    }

    public String getUUID() {
        return this.uuid;
    }
    
    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSkin() {
        return this.skin;
    }

    public void setSkin(String skin) {
        this.skin = skin;
    }

    public boolean isSlim() {
        return slim;
    }

    public void setSlim(boolean slim) {
        this.slim = slim;
    }
    
}
