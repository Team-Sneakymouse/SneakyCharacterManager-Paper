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
        if(params.equalsIgnoreCase("character_name")) {
            if (player != null) {
                Character character = Character.get(player);
                if (character != null) return character.getName();
            }
        }
        return null;
    }
    
}
