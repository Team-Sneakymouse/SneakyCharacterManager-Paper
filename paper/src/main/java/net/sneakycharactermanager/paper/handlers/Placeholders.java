package net.sneakycharactermanager.paper.handlers;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

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
            case "character_skin" :
                return character.getSkin();
            case "character_slim" :
                return character.isSlim() + "";
        }
        
        return null;
    }
    
}
