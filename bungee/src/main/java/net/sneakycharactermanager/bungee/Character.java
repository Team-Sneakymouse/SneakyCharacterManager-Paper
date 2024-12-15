package net.sneakycharactermanager.bungee;

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
    private String skinUUID;
    private boolean slim;
    private String tags;

    public Character(String uuid, boolean enabled, String name, String skin, String skinUUID, boolean slim, String tags) {
        this.uuid = uuid;
        this.enabled = enabled;
        this.name = name;
        this.skin = skin;
        this.skinUUID = skinUUID;
        this.slim = slim;
        this.tags = tags;
    }

    public Character(String uuid, String name, String skin, String skinUUID, boolean slim) {
        this(
            uuid,
        true,
            name,
            skin,
            skinUUID,
            slim,
            ""
        );
    }

    public Character(String uuid, Configuration config) {
        this(
            uuid,
            config.getBoolean("enabled"),
            config.getString("name"),
            config.getString("skin"),
            config.getString("skinUUID"),
            config.getBoolean("slim"),
            config.getString("tags")
        );
    }

    public void loadCharacter(ServerInfo serverInfo, String playerUUID, boolean forced) {
        PaperMessagingUtil.sendByteArray(serverInfo, "loadCharacter", playerUUID, this, forced);
        if (this.skin.isEmpty()) {
            PaperMessagingUtil.sendByteArray(serverInfo, "defaultSkin", playerUUID, this.uuid);
        }
    }

    public void loadTempCharacter(ServerInfo serverInfo, String requesterUUID, String sourceUUID) {
        PaperMessagingUtil.sendByteArray(serverInfo, "loadTempCharacter", requesterUUID, this, sourceUUID);
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

    public String getSkinUUID() {
        return  this.skinUUID;
    }

    public void setSkinUUID(String skinUUID) {
        this.skinUUID = skinUUID;
    }

    public boolean isSlim() {
        return slim;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public void setSlim(boolean slim) {
        this.slim = slim;
    }
}
