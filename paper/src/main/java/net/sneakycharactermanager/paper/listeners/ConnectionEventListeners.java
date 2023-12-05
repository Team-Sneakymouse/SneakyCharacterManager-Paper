package net.sneakycharactermanager.paper.listeners;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.Character;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ConnectionEventListeners implements Listener {

    private static Map<Player, Integer> taskIdMap = new HashMap<Player, Integer>();

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        //Loading the nicknames for newly connected player
        //TODO: This will be moved to Character.load()
        SneakyCharacterManager.getInstance().nametagManager.loadNames(player);

        // TODO: Fine-tune this delay. 1 tick might already be fine
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
        //Un-Nick player who is disconnecting from the server
        SneakyCharacterManager.getInstance().nametagManager.unnicknamePlayer(event.getPlayer());

        Character.get(event.getPlayer()).save();
        Character.remove(event.getPlayer());
    }

}
