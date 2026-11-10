package net.sneakycharactermanager.paper.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.meta.BookMeta;

import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.util.ChatUtility;

/**
 * Signs written books with the active character's name instead of the account name.
 * The server fills in the account name when a book is signed; this replaces it before the book is created.
 */
public class BookSignListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBookSign(PlayerEditBookEvent event) {
        if (!event.isSigning()) return;

        Character character = Character.get(event.getPlayer());
        if (character == null) return;

        String author = authorName(character.getName());
        if (author == null) return;

        BookMeta meta = event.getNewBookMeta();
        meta.setAuthor(author);
        event.setNewBookMeta(meta);
    }

    /** Plain-text author from a character name, or null when there is nothing usable. */
    static String authorName(String characterName) {
        String plain = ChatUtility.stripFormatting(characterName);
        if (plain == null) return null;
        plain = plain.strip();
        return plain.isEmpty() ? null : plain;
    }
}
