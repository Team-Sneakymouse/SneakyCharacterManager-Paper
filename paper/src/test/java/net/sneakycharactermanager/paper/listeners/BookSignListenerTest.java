package net.sneakycharactermanager.paper.listeners;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BookSignListenerTest {

    @Test
    void plainNameIsUsedAsIs() {
        assertEquals("Jimmy", BookSignListener.authorName("Jimmy"));
    }

    @Test
    void formattingIsStripped() {
        assertEquals("Monica Rupert", BookSignListener.authorName("<gold>Monica</gold> &lRupert"));
    }

    @Test
    void emptyOrMissingNameKeepsAccountName() {
        assertNull(BookSignListener.authorName(null));
        assertNull(BookSignListener.authorName("   "));
        assertNull(BookSignListener.authorName("<red></red>"));
    }
}
