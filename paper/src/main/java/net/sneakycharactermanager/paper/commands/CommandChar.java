package net.sneakycharactermanager.paper.commands;

import java.util.*;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.util.ChatUtility;

public class CommandChar extends Command {

    public static Map<Player, String> deleteConfirmationMap = new HashMap<>();
    public static Map<String, List<String>> tabCompleteMap = new HashMap<>();

    public CommandChar() {
        super("char");
        this.description = "Switch between your different characters!";
        this.setUsage("/char (Character Name. The name is not case sensitive, and just the first characters is enough)");
        this.setPermission(SneakyCharacterManager.IDENTIFIER + ".command." + this.getName());
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatUtility.convertToComponent("&4Must be a player to run this command"));
            return true;
        }

        if (args != null && args.length > 0) {
            if (args[0].equals("confirm")) {
                if (deleteConfirmationMap.containsKey(player)) {
                    String[] s = deleteConfirmationMap.get(player).split(";");
                    if (System.currentTimeMillis() < Long.valueOf(s[0]) + 10000) {
                        BungeeMessagingUtil.sendByteArray(player, "deleteCharacter", player.getUniqueId().toString(), s[1]);
                        sender.sendMessage(ChatUtility.convertToComponent("&aDeleting character..."));
                        deleteConfirmationMap.remove(player);
                    } else {
                        sender.sendMessage(ChatUtility.convertToComponent("&aYou tried to confirm a character deletion but it appears that you took more than 10 seconds to confirm. Please start over."));
                        deleteConfirmationMap.remove(player);
                    }
                } else {
                    sender.sendMessage(ChatUtility.convertToComponent("&aYou tried to confirm a character deletion but you aren't deleting a character right now."));
                }
                return true;
            } else {
                String name = String.join(" ", args);
                player.sendMessage(ChatUtility.convertToComponent("&aLoading character &b`" + name + "`&a... Please Wait..."));
                BungeeMessagingUtil.sendByteArray(player, "selectCharacterByName", player.getUniqueId().toString(), name);
                return true;
            }
        }

        player.sendMessage(ChatUtility.convertToComponent("&aLoading character menu..."));

        SneakyCharacterManager.getInstance().selectionMenu.openMenu(player);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        if(!(sender instanceof Player player)) return List.of();
        if (tabCompleteMap.containsKey(player.getUniqueId().toString()) && args.length == 1) {
            List<String> names = new ArrayList<>();

            for (String name : tabCompleteMap.get(player.getUniqueId().toString())) {
                if (!names.contains(name) && name.toLowerCase().startsWith(args[0].toLowerCase())) names.add(name);
            }

            return names;
        } else {
            return new ArrayList<>();
        }
    }
    
}
