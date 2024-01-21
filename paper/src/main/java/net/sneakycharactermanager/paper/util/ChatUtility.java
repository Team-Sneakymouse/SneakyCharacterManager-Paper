package net.sneakycharactermanager.paper.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class ChatUtility {

    public static Component convertToComponent(String message) {
        message = message.replaceAll("\\x{00A7}", "&");

        message = message.replace("&1", "<dark_blue>");
        message = message.replace("&2", "<dark_green>");
        message = message.replace("&3", "<dark_aqua>");
        message = message.replace("&4", "<dark_red>");
        message = message.replace("&5", "<dark_purple>");
        message = message.replace("&6", "<gold>");
        message = message.replace("&7", "<gray>");
        message = message.replace("&8", "<dark_gray>");
        message = message.replace("&9", "<blue>");
        message = message.replace("&0", "<black>");

        message = message.replace("&a", "<green>");
        message = message.replace("&A", "<green>");
        message = message.replace("&b", "<aqua>");
        message = message.replace("&B", "<aqua>");
        message = message.replace("&c", "<red>");
        message = message.replace("&C", "<red>");
        message = message.replace("&d", "<light_purple>");
        message = message.replace("&D", "<light_purple>");
        message = message.replace("&e", "<yellow>");
        message = message.replace("&E", "<yellow>");
        message = message.replace("&f", "<white>");
        message = message.replace("&F", "<white>");


        message = message.replace("&k", "<obf>");
        message = message.replace("&K", "<obf>");
        message = message.replace("&l", "<b>");
        message = message.replace("&L", "<b>");
        message = message.replace("&m", "<st>");
        message = message.replace("&M", "<st>");
        message = message.replace("&n", "<u>");
        message = message.replace("&N", "<u>");
        message = message.replace("&o", "<i>");
        message = message.replace("&O", "<i>");

        message = message.replaceAll("&#([A-Fa-f0-9]{6})", "<color:#$1>");

        message = message.replace("&r", "<reset>");
        message = message.replace("&R", "<reset>");

        return MiniMessage.miniMessage().deserialize(message).decoration(TextDecoration.ITALIC, false);
    }

}
