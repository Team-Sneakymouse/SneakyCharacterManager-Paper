package net.sneakycharactermanager.paper.listeners;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import net.sneakycharactermanager.paper.util.ChatUtility;

class BookSignListenerTest {

    @Test
    void plainNameIsUsedAsIs() {
        assertEquals("Jimmy", BookSignListener.authorName("Jimmy"));
    }

    @Test
    void surroundingWhitespaceIsTrimmed() {
        assertEquals("Jimmy", BookSignListener.authorName("  Jimmy "));
    }

    @Test
    void formattedNameEndsUpPlain() {
        // Character#getNameUnformatted() delegates to ChatUtility.stripFormatting.
        String unformatted = ChatUtility.stripFormatting("<gold>Monica</gold> &lRupert");
        assertEquals("Monica Rupert", BookSignListener.authorName(unformatted));
    }

    @Test
    void emptyOrMissingNameKeepsAccountName() {
        assertNull(BookSignListener.authorName(null));
        assertNull(BookSignListener.authorName("   "));
        assertNull(BookSignListener.authorName(ChatUtility.stripFormatting("<red></red>")));
    }
}
