package net.sneakycharactermanager.paper.admincommands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommandCharScanTest {
    private static final String ID = "0f8b7c1e-5a3d-4c2b-9e6f-1a2b3c4d5e6f";

    @Test
    void namedCharacterShowsNameAndKeepsUuidOnHoverAndClick() {
        Component line = CommandCharScan.line("Steve", "Aldric Vane", ID);

        assertEquals("Item Found: Player: Steve | Character: Aldric Vane", plain(line));
        Component name = line.children().get(0);
        HoverEvent<?> hover = name.hoverEvent();
        assertNotNull(hover);
        assertEquals("Character UUID: " + ID + "\nClick to copy", plain((Component) hover.value()));
        assertEquals(ClickEvent.copyToClipboard(ID), name.clickEvent());
    }

    @Test
    void unknownNameFallsBackToUuid() {
        Component line = CommandCharScan.line("Steve", null, ID);

        assertEquals("Item Found: Player: Steve | Character: " + ID, plain(line));
        assertNull(line.children().get(0).hoverEvent());
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
