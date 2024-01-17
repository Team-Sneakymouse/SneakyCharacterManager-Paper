package net.sneakycharactermanager.bungee;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.config.Configuration;
import net.sneakycharactermanager.bungee.util.PaperMessagingUtil;

public class Character {
    private final String uuid;
    private boolean enabled;
    private String name;
    private String skin;
    private boolean slim;
    private List<String> tags = new ArrayList<>();

    public Character(String uuid, Configuration config) {
        this.uuid = uuid;
        this.enabled = config.getBoolean("enabled");
        this.name = config.getString("name");
        this.skin = config.getString("skin");
        this.slim = config.getBoolean("slim");
        this.tags = config.getStringList("tags");
    }

    public Character(String name) {
        this.uuid = UUID.randomUUID().toString();
        this.enabled = true;
        this.name = name;
        this.skin = "";
        this.slim = false;
        this.tags = new ArrayList<>();
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

    public String getNameUnformatted() {
        Pattern pattern = Pattern.compile("<[^>]*>|&[0-9A-FK-ORa-fk-or]");
        Matcher matcher = pattern.matcher(this.name);
        return matcher.replaceAll("");
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

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public void setSlim(boolean slim) {
        this.slim = slim;
    }
    
}
