package net.sneakycharactermanager.proxy.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CharacterData {
    private final String uuid;
    private boolean enabled;
    private String name;
    private String skin;
    private String skinUUID;
    private boolean slim;
    private String texture;
    private String signature;
    private String tags;
    private Gender gender;

    public CharacterData(
            String uuid,
            boolean enabled,
            String name,
            String skin,
            String skinUUID,
            String texture,
            String signature,
            boolean slim,
            String tags,
            String gender
    ) {
        this.uuid = uuid;
        this.enabled = enabled;
        this.name = name;
        this.skin = skin;
        this.skinUUID = skinUUID;
        this.texture = texture == null ? "" : texture;
        this.signature = signature == null ? "" : signature;
        this.slim = slim;
        this.tags = tags == null ? "" : tags;
        this.gender = Gender.fromString(gender);
    }

    public String uuid() { return uuid; }
    public boolean enabled() { return enabled; }
    public void enabled(boolean enabled) { this.enabled = enabled; }
    public String name() { return name; }
    public void name(String name) { this.name = name; }
    public String skin() { return skin; }
    public void skin(String skin) { this.skin = skin; }
    public String skinUUID() { return skinUUID; }
    public void skinUUID(String skinUUID) { this.skinUUID = skinUUID; }
    public boolean slim() { return slim; }
    public void slim(boolean slim) { this.slim = slim; }
    public String texture() { return texture; }
    public void texture(String texture) { this.texture = texture == null ? "" : texture; }
    public String signature() { return signature; }
    public void signature(String signature) { this.signature = signature == null ? "" : signature; }
    public String tags() { return tags; }
    public void tags(String tags) { this.tags = tags == null ? "" : tags; }
    public Gender gender() { return gender; }
    public void gender(Gender gender) { this.gender = gender; }

    public String nameUnformatted() {
        Pattern pattern = Pattern.compile("<[^>]*>|&[0-9A-FK-ORa-fk-or]");
        Matcher matcher = pattern.matcher(this.name == null ? "" : this.name);
        return matcher.replaceAll("");
    }
}

