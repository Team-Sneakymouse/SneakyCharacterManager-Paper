package net.sneakycharactermanager.paper.admincommands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandTitleGrantTest {
    @Test
    void generatesKeyFromDisplayText() {
        assertEquals("master_fisherman", CommandTitleGrant.titleKeyFromDisplay("  Master Fisherman  "));
        assertEquals("master_fisherman", CommandTitleGrant.titleKeyFromDisplay("MASTER-FISHERMAN!"));
        assertEquals("", CommandTitleGrant.titleKeyFromDisplay("!!!"));
        assertEquals("", CommandTitleGrant.titleKeyFromDisplay(null));
    }
}
