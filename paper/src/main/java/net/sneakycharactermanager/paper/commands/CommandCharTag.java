package net.sneakycharactermanager.paper.commands;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.util.ChatUtility;

public class CommandCharTag extends Command {

    public CommandCharTag() {
        super("chartag");
        this.description = "Add or remove a tag to a character.";
        this.usageMessage = "/chartag [playerName] [add/remove] [tagName]";
        this.setPermission(SneakyCharacterManager.IDENTIFIER + ".commandadmin." + this.getName());
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player)) return false;

        if (args.length < 3) {
            sender.sendMessage(ChatUtility.convertToComponent("&4Invalid Usage: " + this.usageMessage));
            return false;
        }

        Player player = Bukkit.getPlayer(args[0]);

        if (player == null) {
            sender.sendMessage(ChatUtility.convertToComponent("&aUnknown player: &b" + args[0]));
            return false;
        }

        Character character = Character.get(player);

        if (character == null) {
            sender.sendMessage(ChatUtility.convertToComponent("&aThis player is not currently on a character: &b" + args[0]));
            return false;
        }

        List<String> tags = character.getTags();

        String tag = args[2].toLowerCase();

        if (args[1].equalsIgnoreCase("add")) {
            tags.add(tag);
            sender.sendMessage(ChatUtility.convertToComponent("&aThe tag &b`" + tag + "'&a has been added to the character &b'" + character.getNameUnformatted() + "'&a."));
        } else if (args[1].equalsIgnoreCase("remove")) {
            if (tags.remove(tag)) {
                sender.sendMessage(ChatUtility.convertToComponent("&aThe tag &b`" + tag + "'&a has been removed from the character &b'" + character.getNameUnformatted() + "'&a."));
            } else {
                sender.sendMessage(ChatUtility.convertToComponent("&aThe character &b`" + character.getNameUnformatted() + "'&a does not have the tag &b'" + tag + "'&a so nothing was changed."));
                return false;
            }
        } else {
            sender.sendMessage(ChatUtility.convertToComponent("&4Invalid Usage: " + this.usageMessage));
            return false;
        }

        character.setTags(tags);
        BungeeMessagingUtil.sendByteArray(player, "updateCharacter", player.getUniqueId().toString(), 4, tags);

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        if (args.length == 1) {
            List<String> playerNames = new ArrayList<>();

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase()) && !player.getName().equals("CMI-Fake-Operator")) playerNames.add(player.getName());
            }

            return playerNames;
        } else if (args.length == 2) {
            return List.of("add", "remove");
        } else {
            return new ArrayList<>();
        }
    }

}
