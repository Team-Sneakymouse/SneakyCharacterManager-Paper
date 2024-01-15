package net.sneakycharactermanager.paper.handlers;

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
        if (character == null) return "";

        String placeholder = params.toLowerCase();

        if (placeholder.equals("character_uuid")) {
            return character.getCharacterUUID();
        } else if (placeholder.equals("character_name")) {
            return character.getName();
        } else if (placeholder.equals("character_name_noformat")) {
            return character.getNameUnformatted();
        } else if (placeholder.equals("character_skin")) {
            return character.getSkin();
        } else if (placeholder.equals("character_slim")) {
            return character.isSlim() + "";
        } else if (placeholder.equals("character_tags")) {
            return character.getTagsJoined();
        } else if (placeholder.startsWith("character_hastag_")) {
            return character.hasTag(placeholder.replace("character_hastag_", "")) + "";
        }

        return null;
    }
    
}
