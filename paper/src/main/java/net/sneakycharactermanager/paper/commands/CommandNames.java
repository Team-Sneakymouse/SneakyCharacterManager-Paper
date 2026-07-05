package net.sneakycharactermanager.paper.commands;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.nametags.NamesPreference;
import net.sneakycharactermanager.paper.handlers.nametags.NamesPreferenceHandler;
import net.sneakycharactermanager.paper.util.ChatUtility;

public class CommandNames extends CommandBase {

    public CommandNames() {
        super("names");
        this.description = "Change player Nickname settings. ON: Show nametags & player names, OFF: Hide Name Tags, Character: Show nicknames hide real names";
        this.usageMessage = "/names [on/off/character]";
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatUtility.convertToComponent("&4Must be a player to run this command"));
            return false;
        }

        if (!NamesPreferenceHandler.isAvailable()) {
            player.sendMessage(ChatUtility.convertToComponent("&4LuckPerms is required to change name display settings."));
            return false;
        }

        if (args.length != 1) {
            player.sendMessage(ChatUtility.convertToComponent("&4Invalid Usage: " + this.usageMessage));
            return false;
        }

        NamesPreference preference;
        String feedback;

        if (args[0].equalsIgnoreCase("on")) {
            preference = NamesPreference.ON;
            feedback = "&eNow showing nicknames & real names!";
        } else if (args[0].equalsIgnoreCase("off")) {
            preference = NamesPreference.OFF;
            feedback = "&eNow hiding names!";
        } else if (args[0].equalsIgnoreCase("character")) {
            preference = NamesPreference.CHARACTER;
            feedback = "&eNow showing nicknames & Hiding real names!";
        } else {
            player.sendMessage(ChatUtility.convertToComponent("&4Unknown argument: " + this.usageMessage));
            return false;
        }

        NamesPreferenceHandler.set(player, preference);
        SneakyCharacterManager.getInstance().nametagManager.applyNamesPreference(player, preference);
        player.sendMessage(ChatUtility.convertToComponent(feedback));

        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        if (args.length == 1) {
            return List.of("on", "off", "character");
        }
        return List.of();
    }
}
