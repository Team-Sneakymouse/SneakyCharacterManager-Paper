package net.sneakycharactermanager.bungee;

import java.util.Map;
import java.util.UUID;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.config.Configuration;
import net.sneakycharactermanager.bungee.util.PaperMessagingUtil;

public class Character {
    private final String uuid;
    private boolean enabled;
    private String name;
    private String skin;

    public Character(String uuid, Configuration config) {
        this.uuid = uuid;
        this.enabled = config.getBoolean("enabled");
        this.name = config.getString("name");
        this.skin = config.getString("skin");
    }

    public Character(String name, String skin) {
        this.uuid = UUID.randomUUID().toString();
        this.enabled = true;
        this.name = name;
        this.skin = skin;
    }

    public void loadCharacter(String subChannel, ServerInfo serverInfo, String playerUUID) {
        PaperMessagingUtil.sendByteArray(serverInfo, subChannel, playerUUID, this.uuid, this.name, this.skin);
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
    
}
