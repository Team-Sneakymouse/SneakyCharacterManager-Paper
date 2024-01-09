package net.sneakycharactermanager.paper.commands;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.util.ChatUtility;

public class CommandCharadmin extends Command {

    public CommandCharadmin() {
        super("charadmin");
        this.description = "Switch between your different characters!";
        this.setUsage("/charadmin [playerName]");
        this.setPermission(SneakyCharacterManager.IDENTIFIER + ".command." + this.getName());
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player opener)) {
            sender.sendMessage(ChatUtility.convertToComponent("&4Must be a player to run this command"));
            return true;
        }

        if (args.length != 1) {
            opener.sendMessage(ChatUtility.convertToComponent("&4Invalid Usage: " + this.usageMessage));
            return false;
        }

        Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (s) -> {
            OfflinePlayer player = Bukkit.getOfflinePlayer(args[0]);
            Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
                String playerUUID = player.getUniqueId().toString();
                File playerDir = new File(SneakyCharacterManager.getCharacterDataFolder(), playerUUID);
                if (playerDir.exists()) {
                    opener.sendMessage(ChatUtility.convertToComponent("&aLoading character admin menu for player: &b" + args[0]));
                    SneakyCharacterManager.getInstance().selectionMenu.openAdminMenu(player, opener);
                } else {
                    opener.sendMessage(ChatUtility.convertToComponent("&aUnknown player: &b" + args[0]));
                }
            });
        });
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        if (args.length == 1) {
            List<String> playerNames = new ArrayList<>();

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) playerNames.add(player.getName());
            }

            return playerNames;
        } else {
            return Collections.emptyList(); // or simply "return new ArrayList<>();"
        }
    }
    
}
