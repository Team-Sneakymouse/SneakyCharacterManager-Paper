package net.sneakycharactermanager.paper.commands;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.util.ChatUtility;

public class CommandNames extends Command {

    public CommandNames() {
        super("names");
        this.description = "Change player Nickname settings. ON: Show nametags & player names, OFF: Hide Name Tags, Character: Show nicknames hide real names";
        this.usageMessage = "/names <on/off/character>";
        this.setPermission(SneakyCharacterManager.IDENTIFIER + ".command." + this.getName());
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {

        if (!(sender instanceof Player player)) return false;

        if (args.length != 1) {
            player.sendMessage(ChatUtility.convertToComponent("&4Invalid Usage: " + this.usageMessage));
            return false;
        }

        if (args[0].equalsIgnoreCase("on")) {
            SneakyCharacterManager.getInstance().nametagManager.hideNames(player, false);
            SneakyCharacterManager.getInstance().nametagManager.createLocalized(player, true);
            player.sendMessage(ChatUtility.convertToComponent("&eNow showing nicknames & real names!"));
        }
        else if (args[0].equalsIgnoreCase("off")) {
            SneakyCharacterManager.getInstance().nametagManager.hideNames(player, true);
            player.sendMessage(ChatUtility.convertToComponent("&eNow hiding names!"));
        }
        else if (args[0].equalsIgnoreCase("character")) {
            SneakyCharacterManager.getInstance().nametagManager.hideNames(player, false);
            SneakyCharacterManager.getInstance().nametagManager.createLocalized(player, false);
            player.sendMessage(ChatUtility.convertToComponent("&eNow showing nicknames & Hiding real names!"));
        }
        else {
            player.sendMessage(ChatUtility.convertToComponent("&4Unknown argument: " + this.usageMessage));
        }

        return false;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        if(args.length == 1){
            return List.of("on", "off", "character");
        } else{
            return List.of();
        }
    }
}
