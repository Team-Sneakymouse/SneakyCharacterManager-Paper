package net.sneakycharactermanager.paper.listeners;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerHideEntityEvent;
import org.bukkit.event.player.PlayerShowEntityEvent;

public class VanishEvents implements Listener {

    /**
     * On a player (Bukkit metadata, any value) while another plugin keeps their nameplate for them: it hides them
     * without vanishing them (SneakyRacials' turtles in their shells), so their plate isn't taken off when they're hidden,
     * shown or teleported meanwhile. That plugin moves the plate and sends its text itself (NametagManager.refreshNickname).
     */
    public static final String KEEP_NAMEPLATE = "sneakycharactermanager:keep-nameplate";

    @EventHandler
    public void onVanish(PlayerHideEntityEvent event){
        if(!(event.getEntity() instanceof Player hidden)) return;
        if (hidden.hasMetadata(KEEP_NAMEPLATE)) return;
        SneakyCharacterManager.getInstance().nametagManager.unnicknamePlayer(hidden);
    }

    @EventHandler
    public void onVanish(PlayerShowEntityEvent event){
        if(!(event.getEntity() instanceof Player hidden)) return;
        if (hidden.hasMetadata(KEEP_NAMEPLATE)) return;
        SneakyCharacterManager.getInstance().nametagManager.unnicknamePlayer(hidden);
        Bukkit.getScheduler().runTaskLater(SneakyCharacterManager.getInstance(), ()->{
            Character character = Character.get(hidden);
            if(character == null) return;
            SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(hidden, character.getDisplayName());
        }, 2);
    }

}
