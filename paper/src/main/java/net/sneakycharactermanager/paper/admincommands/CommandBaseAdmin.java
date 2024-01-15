package net.sneakycharactermanager.paper.admincommands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.SneakyCharacterManager;

public class CommandBaseAdmin extends Command {

    public CommandBaseAdmin(String name) {
        super(name);
        this.setPermission(SneakyCharacterManager.IDENTIFIER + ".admin.command." + this.getName());
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {return true;}
    
}