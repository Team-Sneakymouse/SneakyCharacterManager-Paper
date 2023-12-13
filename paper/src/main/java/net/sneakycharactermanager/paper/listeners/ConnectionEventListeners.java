package net.sneakycharactermanager.paper.listeners;

import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.commands.CommandChar;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.skins.SkinCache;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;

public class ConnectionEventListeners implements Listener {

    private static Map<Player, Integer> taskIdMap = new HashMap<>();

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(SneakyCharacterManager.getInstance(), () -> {
            if (!player.isOnline() || Character.isPlayedMapped(player)) {
                Bukkit.getScheduler().cancelTask(taskIdMap.get(player));
                taskIdMap.remove(player);
            } else {
                BungeeMessagingUtil.sendByteArray("playerJoin", event.getPlayer().getUniqueId().toString());
            }
        }, 5, 20);
    
        taskIdMap.put(player, taskId);
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event){
        Player player = event.getPlayer();

        //Un-Nick player who is disconnecting from the server
        SneakyCharacterManager.getInstance().nametagManager.unnicknamePlayer(player);
        CommandChar.tabCompleteMap.remove(player);
        SkinCache.remove(player.getUniqueId().toString());

        Character character = Character.get(player);

        if (character == null) {
            SneakyCharacterManager.getInstance().getLogger().severe("SneakyCharacterManager found a player who quit out but wasn't a character. This should never happen: " + player.getName());
        } else {
            character.save();
        }
        Character.remove(player);
    }

}
