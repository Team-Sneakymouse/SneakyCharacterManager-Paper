package net.sneakycharactermanager.paper;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public class LoadCharacterEvent extends PlayerEvent {

	protected static final HandlerList handlers = new HandlerList();

    private final Boolean firstLoad;
    private final String characterUUID;
    private final String characterName;
    private final String skin;

    public LoadCharacterEvent(@NotNull Player player, Boolean firstLoad, String characterUUID, String characterName, String skin) {
        super(player);
        this.firstLoad = firstLoad;
        this.characterUUID = characterUUID;
        this.characterName = characterName;
        this.skin = skin;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }
    
    public Boolean getFirstLoad() {
        return firstLoad;
    }

    public String getCharacterUUID() {
        return characterUUID;
    }

    public String getCharacterName() {
        return characterName;
    }

    public String getSkin() {
        return skin;
    }

}
