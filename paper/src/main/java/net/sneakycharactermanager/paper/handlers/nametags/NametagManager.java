package net.sneakycharactermanager.paper.handlers.nametags;

import java.util.*;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import me.clip.placeholderapi.PlaceholderAPI;


/**
 * Manager class for any function involving Nicknames & Nametags
 * */
public class NametagManager {

    private final HashMap<String, Boolean> isShowingNameplates;
    private final List<String> showingRealNames;

    private final Map<String, Nickname> nicknames;

    public NametagManager() {
        nicknames = new HashMap<>();

        isShowingNameplates = new HashMap<>();
        showingRealNames = new ArrayList<>();
    }

    /**
     * Set the nickname of the supplied player to the given name
     * @param player Player to create/set the nickname for.
     * @param nickname Nickname to set onto the player
     * */
    public void nicknamePlayer(Player player, String nickname) {
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR || (PlaceholderAPI.setPlaceholders(player, "%cmi_user_vanished_symbol%") != null && !PlaceholderAPI.setPlaceholders(player, "%cmi_user_vanished_symbol%").isEmpty())) return;

        if (!nicknames.containsKey(player.getUniqueId().toString())) {
            nicknames.put(player.getUniqueId().toString(), new Nickname(player, nickname));
        }else{
            nicknames.get(player.getUniqueId().toString()).setNickname(nickname);
        }

        Bukkit.getScheduler().runTaskLater(SneakyCharacterManager.getInstance(), ()->{
            refreshNickname(player);
        }, 5);

    }

    public void refreshNickname(Player player) {
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR || (PlaceholderAPI.setPlaceholders(player, "%cmi_user_vanished_symbol%") != null && !PlaceholderAPI.setPlaceholders(player, "%cmi_user_vanished_symbol%").isEmpty())) return;

        Nickname name = nicknames.get(player.getUniqueId().toString());
        if (name == null) return;
        
        name.hideNameFromOwner();

        List<String> handled = new ArrayList<>();
        for(String uuid : showingRealNames) {
            Player requester = Bukkit.getPlayer(UUID.fromString(uuid));
            if (requester == null || ! requester.isOnline() || !requester.getWorld().getName().equals(player.getWorld().getName()) || requester.getLocation().distanceSquared(player.getLocation()) > 2500) continue;

            name.showRealName(requester, true);
            handled.add(uuid);
        }

        for(Map.Entry<String, Boolean> showingNameplates : isShowingNameplates.entrySet()) {
            if (handled.contains(showingNameplates.getKey())) continue;
            Player requester = Bukkit.getPlayer(UUID.fromString(showingNameplates.getKey()));

            if (requester == null || ! requester.isOnline() || !requester.getWorld().getName().equals(player.getWorld().getName()) || requester.getLocation().distanceSquared(player.getLocation()) > 2500) continue;

            if (!showingNameplates.getValue()) {
                name.hideName(requester);
            } else {
                name.showRealName(requester, false);
            }
        }

    }

    /**
     * Create a localized nickname for the requested player to see!
     * Currently, this just shows the 'real' name of all nicknamed players!
     * @param requester Who requested the localized nickname change?
     * @param enabled Are they enabling or disabling the feature?
     * @deprecated Going to change to 'showRealName' instead of createLocalized
     *               May use "createLocalized" as a sub function for locally changing a players nickname.
     * */
    @Deprecated
    public void createLocalized(Player requester, boolean enabled) {
        if (enabled) {
            isShowingNameplates.put(requester.getUniqueId().toString(), true);
            if (!showingRealNames.contains(requester.getUniqueId().toString())) showingRealNames.add(requester.getUniqueId().toString());
        }else{
            isShowingNameplates.put(requester.getUniqueId().toString(), true);
            showingRealNames.remove(requester.getUniqueId().toString());
        }
        for(Nickname name : nicknames.values()) {
            name.showRealName(requester, enabled);
        }
    }

    /**
     * Remove the nickname of a player
     * @param player Player to remove the nickname from
     * */
    public void unnicknamePlayer(Player player) {
        Nickname nickname = nicknames.remove(player.getUniqueId().toString());
        if (nickname != null) {
            nickname.unNick();
        }
    }

    /**
     * Hide nametags for the requested player
     * @param requester Player who wishes to change hidden name state
     * @param state State of hidden names
     * */
    public void hideNames(Player requester, boolean state) {
        if (state) {
            isShowingNameplates.put(requester.getUniqueId().toString(), false);
            showingRealNames.remove(requester.getUniqueId().toString());

            
            for(Nickname nickname : nicknames.values()) {
                nickname.hideName(requester);
            }
        }
        else {
            isShowingNameplates.put(requester.getUniqueId().toString(), true);
        }
    }

    /**
     * Load all active nicknames for a player.
     * Required because the nickname entities are fake, they do not exist on player connect
     * @param player Player to load names for
     * */
    public void loadNames(Player player) {
        if(!isShowingNameplates.getOrDefault(player.getUniqueId().toString(), true)){
            hideNames(player, true);
        }else{
            hideNames(player, false);
            if(showingRealNames.contains(player.getUniqueId().toString())){
                createLocalized(player, true);
            }else{
                createLocalized(player, false);
            }
        }
    }

    /**
     * Get the players current Nickname!
     * @return The Players Nickname || The players name if they have no Nickname!
     * */
    public String getNickname(Player player) {
        Nickname nickname = nicknames.get(player.getUniqueId().toString());
        if (nickname == null) return player.getName();
        return nickname.getNickname();
    }

    /**
     * Remove the nickname entities of all players.
     * */
    public void unnickAll() {
        nicknames.values().forEach(entry -> {
            entry.unNick();
        });
    }

}
