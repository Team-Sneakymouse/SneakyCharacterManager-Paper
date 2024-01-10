package net.sneakycharactermanager.paper.handlers.nametags;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.sneakycharactermanager.paper.util.ChatUtility;

public class Nickname {

    private String nickname;
    private final String uuid;
    private final NicknameEntity nametag;

    /**
     * Nickname type helper class. Handles most function calling and string formatting for Nicknames.
     * @param player Player this nickname is owned by
     * @param nickname Starter nickname for the player
     * */
    public Nickname(Player player, String nickname) {
        this.nametag = new NicknameEntity(player);
        this.uuid = player.getUniqueId().toString();
        this.nickname = nickname;
        this.setNickname(nickname);
    }

    /**
     * Set the nickname of the owning player.
     * This nickname by default supports MiniMessages! So you can format the name as needed!
     * @see MiniMessage
     * @param nickname New nickname to set.
     * */
    public void setNickname(String nickname) {
        Player player = Bukkit.getPlayer(UUID.fromString(this.uuid));
        if (player == null) return;
        this.nickname = nickname;
        nametag.setName(ChatUtility.convertToComponent(nickname));
        nametag.updateComponents(nickname);
    }

    /**
     * Send the requester the nicknamed players real name!
     * This function is called by another player, not the person who owns this nickname.
     * @param requester Player who requested to see the real name of this player
     * @param enabled Are they turning the feature on or off
     * */
    public void showRealName(Player requester, boolean enabled) {
        if (enabled) {
            nametag.refreshOn(requester);
        }
        else {
            nametag.refreshCharacter(requester);
        }
    }

    /**
     * Remove the nickname of a player.
     * This destroys the fake nameplate
     * */
    public void unNick() {
        nametag.destroy();
    }

    /**
     * Set the nametag hidden state for the requester
     * @param requester Player who wants to hide names
     * @param state To hide or show the name
     * */
    public void hideName(Player requester) {
        nametag.refreshOff(requester);
    }

    public void hideNameFromOwner() {
        nametag.hideFromOwner();
    }

    /**
     * Get the players current Nickname!
     * @return The Players Nickname
     * */
    public String getNickname() { return nickname; }

}
