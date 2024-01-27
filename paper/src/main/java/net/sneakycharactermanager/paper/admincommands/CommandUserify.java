package net.sneakycharactermanager.paper.admincommands;

import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.util.ChatUtility;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CommandUserify extends CommandBaseAdmin {
    public CommandUserify() {
        super("userify");
        this.usageMessage = this.getName() + " <Character Name>";
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {

        if(args.length == 0) {
            sender.sendMessage(ChatUtility.convertToComponent("&4Invalid Usage: " + this.usageMessage));
            return false;
        }
        if(!(sender instanceof Player player)) return false;

        StringBuilder userBuilder = new StringBuilder();
        for(int i = 0; i < args.length; i++){
            userBuilder.append(args[i]).append(" ");
        }
        String characterName = userBuilder.substring(0, userBuilder.length()-1);

        BungeeMessagingUtil.sendByteArray(player, "getAllCharacters", player.getUniqueId().toString(), characterName);
        return false;
    }
}
