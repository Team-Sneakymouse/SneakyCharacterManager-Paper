package net.sneakycharactermanager.paper.commands;


import net.sneakycharactermanager.paper.util.SkinUtility;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;



/**
 * A Dummy command for quick integration testing of functions
 * */
public class CommandTesting extends Command {

    public CommandTesting() {
        super("test");
        this.description = "Simple test command for verifying features";
        this.setPermission("sneakycharacters.testing");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if(!(sender instanceof Player player)) return false;

        SkinUtility.applySkin(
                "https://media.discordapp.net/attachments/1088566171885912155/1169309996069179482/pmcskin3d-alex-slim-arms_77.png",
                player
        );

        return false;
    }
}
