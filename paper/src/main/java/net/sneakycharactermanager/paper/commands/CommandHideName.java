package net.sneakycharactermanager.paper.commands;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.util.ChatUtility;

public class CommandHideName extends CommandBase {

    public CommandHideName() {
        super("hidename");
        this.description = "Temporarily hide your character name from other players.";
        this.usageMessage = "/hidename <true|false|toggle>";
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatUtility.convertToComponent("&4Must be a player to run this command"));
            return false;
        }

        if (args.length != 1) {
            player.sendMessage(ChatUtility.convertToComponent("&4Invalid Usage: " + this.usageMessage));
            return false;
        }

        if (Character.get(player) == null) {
            player.sendMessage(ChatUtility.convertToComponent("&4You must have a character loaded to use this command."));
            return false;
        }

        boolean hide;
        if (args[0].equalsIgnoreCase("true")) {
            hide = true;
        } else if (args[0].equalsIgnoreCase("false")) {
            hide = false;
        } else if (args[0].equalsIgnoreCase("toggle")) {
            hide = !SneakyCharacterManager.getInstance().nametagManager.isHidingOwnName(player);
        } else {
            player.sendMessage(ChatUtility.convertToComponent("&4Unknown argument: " + this.usageMessage));
            return false;
        }

        SneakyCharacterManager.getInstance().nametagManager.setHidingOwnName(player, hide);
        if (hide) {
            player.sendMessage(ChatUtility.convertToComponent("&eYour character name is now hidden."));
        } else {
            player.sendMessage(ChatUtility.convertToComponent("&eYour character name is now visible."));
        }

        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("true", "false", "toggle");
        }
        return List.of();
    }
}
