package net.sneakycharactermanager.paper.consolecommands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.SneakyCharacterManager;

public abstract class CommandBaseConsole extends Command {

    public CommandBaseConsole(String name) {
        super(name);
        this.setPermission(SneakyCharacterManager.IDENTIFIER + "console.command." + this.getName());
    }

    @Override
    public abstract boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args);
    
}