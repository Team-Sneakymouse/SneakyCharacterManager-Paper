package net.sneakycharactermanager.paper.commands;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.util.ChatUtility;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CommandNick extends Command {
    public CommandNick() {
        super("nick");
        this.description = "Change the name of your character!";
        this.usageMessage = "/nick <Name>";
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {

        if(!(sender instanceof Player player)) return false;

        if(args.length == 0){
            player.sendMessage(ChatUtility.convertToComponent("&4Invalid Usage: " + this.usageMessage));
            return false;
        }

        StringBuilder builder = new StringBuilder();
        for(String word : args){
            builder.append(word).append(" ");
        }
        String nickname = builder.substring(0, builder.length()-1);
        SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(player, nickname);
        BungeeMessagingUtil.sendByteArray("updateCharacter", player.getUniqueId().toString(), 2, nickname);
        player.sendMessage(ChatUtility.convertToComponent("&eName updated to: " + nickname));
        return false;
    }
}
