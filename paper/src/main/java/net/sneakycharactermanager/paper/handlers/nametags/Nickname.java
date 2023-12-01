package net.sneakycharactermanager.paper.handlers.nametags;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class Nickname {

    private String nickname;
    private final String realName;
    private final String uuid;
    private final NicknameEntity nametag;

    /**
     * Nickname type helper class. Handles most function calling and string formatting for Nicknames.
     * @param player Player this nickname is owned by
     * @param nickname Starter nickname for the player
     * */
    public Nickname(Player player, String nickname){
        this.nametag = new NicknameEntity(player);
        this.uuid = player.getUniqueId().toString();
        this.realName = player.getName();
        this.nickname = nickname;
        this.setNickname(nickname);
    }

    /**
     * Set the nickname of the owning player.
     * This nickname by default supports MiniMessages! So you can format the name as needed!
     * @see MiniMessage
     * @param nickname New nickname to set.
     * */
    public void setNickname(String nickname){
        Player player = Bukkit.getPlayer(UUID.fromString(this.uuid));
        if(player == null) return;
        if(!player.hasPermission("sneakycharacters.colournames")){
            nickname = MiniMessage.miniMessage().escapeTags(nickname);
        }
        this.nickname = nickname;
        nametag.setName(MiniMessage.miniMessage().deserialize(nickname));
    }

    /**
     * Send the requester the nicknamed players real name!
     * This function is called by another player, not the person who owns this nickname.
     * @param requester Player who requested to see the real name of this player
     * @param enabled Are they turning the feature on or off
     * */
    public void showRealName(Player requester, boolean enabled){
        if(enabled){
            nametag.setLocalizedName(MiniMessage.miniMessage().deserialize(
                    "<white>" + this.nickname + "<newline><gray>[" + this.realName + "]"
            ), requester);
        }
        else {
            nametag.setLocalizedName(MiniMessage.miniMessage().deserialize(this.nickname), requester);
        }
    }

    /**
     * Remove the nickname of a player.
     * This destroys the fake nameplate
     * */
    public void unNick(){
        nametag.destroy();
    }

    /**
     * Load the nickname for the player.
     * @param player Player who needs to load this nickname
     * */
    public void loadNickname(Player player){
        nametag.spawn(player);
    }



}
