package net.sneakycharactermanager.paper.handlers.nametags;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.util.ChatUtility;

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
    public Nickname(Player player, String nickname) {
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
    public void setNickname(String nickname) {
        Player player = Bukkit.getPlayer(UUID.fromString(this.uuid));
        if (player == null) return;
        if (!player.hasPermission(SneakyCharacterManager.IDENTIFIER + ".colournames")) {
            nickname = MiniMessage.miniMessage().escapeTags(nickname);
        }
        this.nickname = nickname;
        nametag.setName(ChatUtility.convertToComponent(nickname));
    }

    /**
     * Send the requester the nicknamed players real name!
     * This function is called by another player, not the person who owns this nickname.
     * @param requester Player who requested to see the real name of this player
     * @param enabled Are they turning the feature on or off
     * */
    public void showRealName(Player requester, boolean enabled) {
        if (enabled) {
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
    public void unNick() {
        nametag.destroy();
    }

    /**
     * Set the nametag hidden state for the requester
     * @param requester Player who wants to hide names
     * @param state To hide or show the name
     * */
    public void hideName(Player requester, boolean state) {
        if (state) {
            nametag.setLocalizedName(Component.text(""), requester);
        }else{
            nametag.setLocalizedName(MiniMessage.miniMessage().deserialize(this.nickname), requester);
        }
    }

    /**
     * Load the nickname for the player.
     * @param player Player who needs to load this nickname
     * */
    public void loadNickname(Player player) {
        if (player.getUniqueId().toString().equals(this.uuid)) return; //The player doesn't need to see their own name
        nametag.spawn(player);
    }

    /**
     * Get the players current Nickname!
     * @return The Players Nickname
     * */
    public String getNickname() { return nickname; }

    /**
     * Updates the nametag's entity data for all observers
     * */
    public void update() {
        nametag.update();;
    }

}
