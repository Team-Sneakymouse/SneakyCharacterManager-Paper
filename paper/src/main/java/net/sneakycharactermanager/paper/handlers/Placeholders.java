package net.sneakycharactermanager.paper.handlers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;

public class Placeholders extends PlaceholderExpansion {

    public Placeholders() {}

    @Override
    public @NotNull String getIdentifier() {
        return SneakyCharacterManager.IDENTIFIER;
    }

    @Override
    public @NotNull String getAuthor() {
        return SneakyCharacterManager.AUTHORS;
    }

    @Override
    public @NotNull String getVersion() {
        return SneakyCharacterManager.VERSION;
    }
    
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        Character character = Character.get(player);
        if (character == null) return null;

        switch(params.toLowerCase()) {
            case "character_uuid" :
                return character.getCharacterUUID();
            case "character_name" :
                return character.getName();
            case "character_name_noformat" :
                Pattern pattern = Pattern.compile("\\<[^)]*\\>|&[0-9A-FK-OR]");
                Matcher matcher = pattern.matcher(character.getName());
                return matcher.replaceAll("");
            case "character_skin" :
                return character.getSkin();
            case "character_slim" :
                return character.isSlim() + "";
        }

        return null;
    }
    
}
