package net.sneakycharactermanager.paper.consolecommands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.commands.CommandChar;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;

public class ConsoleCommandCharTemp extends CommandBaseConsole {

    private static Map<String, TempCharEntry> tempEntries = new HashMap<>();

    public ConsoleCommandCharTemp() {
        super("chartemp");
        this.description = "The specified player loads a temporary character from a template or another player. They will only use their nick and skin, not their inventory. /nick and /skin will be disabled whilst on a temporary character.";
        this.setUsage("/" + this.getName() + " [playerName] [template/playerName/playerUUID] [characterID]");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (sender instanceof Player) {
            sender.sendMessage("This command can only be run from the console.");
            return false;
        }

        if (args.length != 3) {
            sender.sendMessage("Invalid Usage: " + this.getUsage());
            return false;
        }

        Player player = Bukkit.getPlayer(args[0]);

        if (player == null) {
            sender.sendMessage("Invalid Player: '" + args[0] + "'");
            return false;
        }

        String uuid = args[1];

        boolean valid = uuid.equals("template") ? true : false;

        if (!valid) {
            for (@NotNull OfflinePlayer opl : Bukkit.getOfflinePlayers()) {
                if (opl.getName() != null && opl.getName().equals(uuid)) {
                    valid = true;
                    uuid = opl.getUniqueId().toString();
                    break;
                } else if (opl.getUniqueId() != null && opl.getUniqueId().toString().equals(uuid)) {
                    valid = true;
                    break;
                }
            }
        }

        if (!valid) {
            sender.sendMessage("The provided character source '" + args[1] + "' is not 'template' and it does not match the UUID or name of any known player.");
            return false;
        }

        BungeeMessagingUtil.sendByteArray(player, "tempCharacter", player.getUniqueId().toString(), uuid, args[2]);
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
            List<String> playerNames = new ArrayList<>();
            playerNames.add("template");

            for (@NotNull OfflinePlayer opl : Bukkit.getOfflinePlayers()) {
                if (opl.getName() != null && opl.getName().toLowerCase().startsWith(args[1].toLowerCase()) && !opl.getName().equals("CMI-Fake-Operator")) playerNames.add(opl.getName());
            }

            return playerNames;
        } else if (args.length == 3) {
            String uuid = args[1];

            Player pl = Bukkit.getPlayer(uuid);

            if (pl != null) uuid = pl.getUniqueId().toString();

            List<String> names = new ArrayList<>();
            if (CommandChar.tabCompleteMap.containsKey(uuid)) {
                for (String name : CommandChar.tabCompleteMap.get(uuid)) {
                    if (!names.contains(name) && name.toLowerCase().startsWith(args[2].toLowerCase())) names.add(name);
                }
            }
            return names;
        } else {
            return new ArrayList<>();
        }
    }

    public static boolean isPlayerTempChar(String playerUUID) {
        return tempEntries.keySet().contains(playerUUID);
    }

    public static void playerTempCharAdd(String playerUUID, String characterSource, String characterID) {
        tempEntries.put(playerUUID, new TempCharEntry(characterSource, characterID));
    }

    public static void playerTempCharRemove(String playerUUID) {
        tempEntries.remove(playerUUID);
    }

    public static void reApply(Player requester) {
        String playerUUID = requester.getUniqueId().toString();
        TempCharEntry entry = tempEntries.get(playerUUID);

        if (entry == null) {
            SneakyCharacterManager.getInstance().getLogger().warning("An attempt was made to reApply a temp character for a player that isn't on a temp character: [" + playerUUID + "]");
            return;
        }

        BungeeMessagingUtil.sendByteArray(requester, "tempCharacter", playerUUID, entry.characterSource, entry.characterID);
    }

    private static class TempCharEntry {
        
        private String characterSource;
        private String characterID;

        private TempCharEntry(String characterSource, String characterID) {
            this.characterSource = characterSource;
            this.characterID = characterID;
        }

    }
    
}
