package net.sneakycharactermanager.paper.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;

public class CommandChar extends Command {

    public CommandChar() {
        super("char");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        BungeeMessagingUtil.sendByteArray("characterSelectionGUI", ((Player) sender).getUniqueId().toString());
        return true;
    }
    
}
