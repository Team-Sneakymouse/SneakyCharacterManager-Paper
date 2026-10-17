package net.sneakycharactermanager.paper.admincommands;

import org.bukkit.command.SimpleCommandMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CommandUserifyTest {
    @Test
    void whoisResolvesToUserifyWithTheExistingPermission() {
        CommandUserify command = new CommandUserify();
        SimpleCommandMap commands = new SimpleCommandMap(null, new java.util.HashMap<>());
        commands.register("sneakycharacters", command);

        assertSame(command, commands.getCommand("userify"));
        assertSame(command, commands.getCommand("whois"));
        assertSame(command, commands.getCommand("sneakycharacters:whois"));
        assertEquals("sneakycharacters.admin.command.userify", command.getPermission());
    }
}
