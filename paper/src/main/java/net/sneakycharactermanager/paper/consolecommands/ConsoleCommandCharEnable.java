package net.sneakycharactermanager.paper.consolecommands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ConsoleCommandCharEnable extends CommandBaseConsole {

    public ConsoleCommandCharEnable() {
        super("charenable");
        this.description = "If the given player was chardisabled, this is removed. During login they will automatically load their last played character again.";
        this.setUsage("charenable <Player>)");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (sender instanceof Player) {
            sender.sendMessage("This command can only be run from the console.");
            return false;
        }

        if (args.length != 1) {
            sender.sendMessage("Invalid Usage: " + this.getUsage());
            return false;
        }

        Player player = Bukkit.getPlayer(args[0]);

        if (player == null) {
            sender.sendMessage("Invalid Player: '" + args[0] + "'");
            return false;
        }

        if (!ConsoleCommandCharDisable.isPlayerCharDisabled(player.getUniqueId().toString())) return false;

        ConsoleCommandCharDisable.playerCharEnable(player.getUniqueId().toString());

        sender.sendMessage("The player '" + args[0] + "' has had their character state restored. During login, they will once again load their last played character.");

        return true;
    }
    
}
