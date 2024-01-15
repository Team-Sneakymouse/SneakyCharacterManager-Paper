package net.sneakycharactermanager.paper.handlers.character;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public class LoadCharacterEvent extends PlayerEvent implements Cancellable {

	protected static final HandlerList handlers = new HandlerList();
    private boolean cancel = false;

    private final boolean firstLoad;
    private final String characterUUID;
    private final String characterName;
    private final String skin;
    private final boolean slim;
    private final List<String> tags;

    public LoadCharacterEvent(@NotNull Player player, boolean firstLoad, String characterUUID, String characterName, String skin, boolean slim, List<String> tags) {
        super(player);
        this.firstLoad = firstLoad;
        this.characterUUID = characterUUID;
        this.characterName = characterName;
        this.skin = skin;
        this.slim = slim;
        this.tags = tags;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    @Override
    public boolean isCancelled() {
        return this.cancel;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancel = cancel;
    }
    
    public boolean getFirstLoad() {
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

    public boolean isSlim() {
        return slim;
    }
    public List<String> getTags() {
        return tags;
    }

}
