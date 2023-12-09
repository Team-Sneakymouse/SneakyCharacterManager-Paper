package net.sneakycharactermanager.paper.commands;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.util.ChatUtility;
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
        if(!(sender instanceof Player player)){
            sender.sendMessage(ChatUtility.convertToComponent("&4Must be a player to run this command"));
            return true;
        }

        player.sendMessage(ChatUtility.convertToComponent("&aLoading character menu..."));
        player.sendMessage(ChatUtility.convertToComponent("&aPlease wait..."));

        SneakyCharacterManager.getInstance().selectionMenu.openMenu(player);
        return true;
    }
    
}
