package net.sneakycharactermanager.paper.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SCM-only text helpers. Shared MiniMessage conversion lives in
 * {@link com.sneakyutils.util.TextUtility}.
 */
public final class ChatUtility {

    private ChatUtility() {
    }

    public static String stripFormatting(String input) {
        if (input == null) return null;
        Pattern pattern = Pattern.compile("<[^>]*>|&[0-9A-FK-ORa-fk-or]");
        Matcher matcher = pattern.matcher(input);
        return matcher.replaceAll("");
    }

}
