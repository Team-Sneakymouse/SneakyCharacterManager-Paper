package net.sneakycharactermanager.paper.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class ChatUtility {

    public static Component convertToComponent(String message) {
        message = unescapeUnicode(message);
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

    private static String unescapeUnicode(String input) {
        if (input == null || input.isEmpty()) return input;

        StringBuilder out = new StringBuilder();
        Matcher matcher = Pattern.compile("\\\\u([0-9a-fA-F]{4})|\\\\U([0-9a-fA-F]{8})").matcher(input);
        int lastEnd = 0;

        while (matcher.find()) {
            out.append(input, lastEnd, matcher.start());

            String shortCode = matcher.group(1);
            String longCode = matcher.group(2);

            int codePoint = shortCode != null
                    ? Integer.parseInt(shortCode, 16)
                    : Integer.parseInt(longCode, 16);

            out.append(new String(Character.toChars(codePoint)));
            lastEnd = matcher.end();
        }

        out.append(input.substring(lastEnd));
        return out.toString();
    }

    public static String stripFormatting(String input) {
        if (input == null) return null;
        Pattern pattern = Pattern.compile("<[^>]*>|&[0-9A-FK-ORa-fk-or]");
        Matcher matcher = pattern.matcher(input);
        return matcher.replaceAll("");
    }

}
