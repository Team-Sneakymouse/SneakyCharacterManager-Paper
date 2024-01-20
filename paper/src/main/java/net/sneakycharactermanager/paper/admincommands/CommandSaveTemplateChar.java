package net.sneakycharactermanager.paper.admincommands;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.util.ChatUtility;

public class CommandSaveTemplateChar extends CommandBaseAdmin {

    public CommandSaveTemplateChar() {
        super("savetemplatechar");
        this.description = "Save your current nickname and skin as a new Template character, for use with the console command 'chartemp'";
        this.setUsage("/" + this.getName() + " [characterID]");
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatUtility.convertToComponent("&4Must be a player to run this command"));
            return false;
        }

        if (args.length != 1) {
            player.sendMessage(ChatUtility.convertToComponent("&4Invalid Usage: " + this.usageMessage));
            return false;
        }

        Character character = Character.get(player);

        if (character == null) return false;

        BungeeMessagingUtil.sendByteArray(player, "saveTemplateChar", args[0], character.getName(), character.getSkin(), character.isSlim());

        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        return new ArrayList<>();
    }

}
