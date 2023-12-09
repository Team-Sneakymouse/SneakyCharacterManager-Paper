package net.sneakycharactermanager.paper.handlers.nametags;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;


/**
 * Manager class for any function involving Nicknames & Nametags
 * */
public class NametagManager {

    private final Map<String, Nickname> nicknames;

    public NametagManager(){
        nicknames = new HashMap<>();
    }

    /**
     * Set the nickname of the supplied player to the given name
     * @param player Player to create/set the nickname for.
     * @param nickname Nickname to set onto the player
     * */
    public void nicknamePlayer(Player player, String nickname){
        if(nickname.equals(player.getName())) return;
        if(!nicknames.containsKey(player.getUniqueId().toString())){
            nicknames.put(player.getUniqueId().toString(), new Nickname(player, nickname));
        }else{
            nicknames.get(player.getUniqueId().toString()).setNickname(nickname);
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
    public void createLocalized(Player requester, boolean enabled){
        for(Nickname name : nicknames.values()){
            name.showRealName(requester, enabled);
        }
    }

    /**
     * Remove the nickname of a player
     * @param player Player to remove the nickname from
     * */
    public void unnicknamePlayer(Player player){
        Nickname nickname = nicknames.remove(player.getUniqueId().toString());
        if(nickname != null){
            nickname.unNick();
        }
    }

    /**
     * Load all active nicknames for a player.
     * Required because the nickname entities are fake, they do not exist on player connect
     * @param player Player to load names for
     * */
    public void loadNames(Player player){
        for(Nickname name : nicknames.values()){
            name.loadNickname(player);
        }
    }


    /**
     * Get the players current Nickname!
     * @return The Players Nickname || The players name if they have no Nickname!
     * */
    public String getNickname(Player player){
        Nickname nickname = nicknames.get(player.getUniqueId().toString());
        if(nickname == null) return player.getName();
        return nickname.getNickname();
    }

}
