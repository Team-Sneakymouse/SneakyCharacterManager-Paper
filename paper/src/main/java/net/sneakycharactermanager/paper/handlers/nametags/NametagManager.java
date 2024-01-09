package net.sneakycharactermanager.paper.handlers.nametags;

import java.util.*;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;


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
        if (player.isDead()) return;

        if (!nicknames.containsKey(player.getUniqueId().toString())) {
            nicknames.put(player.getUniqueId().toString(), new Nickname(player, nickname));
        }else{
            nicknames.get(player.getUniqueId().toString()).setNickname(nickname);
        }

        Bukkit.getScheduler().runTaskLater(SneakyCharacterManager.getInstance(), ()->{
            for(String uuid : showingRealNames) {
                Player requester = Bukkit.getPlayer(UUID.fromString(uuid));
                if (requester == null || ! requester.isOnline()) continue;
                for(Nickname name : nicknames.values()) {
                    name.showRealName(requester, true);
                }
            }

            for(Map.Entry<String, Boolean> showingNameplates : isShowingNameplates.entrySet()) {
                Player requester = Bukkit.getPlayer(UUID.fromString(showingNameplates.getKey()));
                if (requester == null || !requester.isOnline()) continue;
                if (!showingNameplates.getValue()) {
                    for(Nickname name : nicknames.values()) {
                        name.hideName(requester);
                    }
                }
            }
        }, 20);

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
            showingRealNames.add(requester.getUniqueId().toString());
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
