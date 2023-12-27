package net.sneakycharactermanager.paper.commands;

import java.util.*;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.CharacterLoader;
import net.sneakycharactermanager.paper.util.ChatUtility;

public class CommandSkin extends Command {

    public CommandSkin() {
        super("skin");
        this.description = "Change your skin!";
        this.setUsage("/skin <URL (Must be direct image)>");
        this.setPermission(SneakyCharacterManager.IDENTIFIER + ".command." + this.getName());
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length < 1) {
            player.sendMessage(ChatUtility.convertToComponent("&4Invalid Usage: " + this.getUsage()));
            return true;
        }

        String url = args[0];
        if (!url.startsWith("http")) {
            player.sendMessage(ChatUtility.convertToComponent("&4Invalid URL.. Please make sure it starts with HTTP(s)"));
            return true;
        }

        Boolean slim = null;
        if (args.length > 1) {
            if (args[1].equals("SLIM")) slim = true;
            else slim = false;
        }

        player.sendMessage(ChatUtility.convertToComponent("&aUpdating your skin!"));

        CharacterLoader.updateSkin(player, url, slim);

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        if (args.length == 2) {
            return Arrays.asList("SLIM", "CLASSIC");
        } else {
            return Collections.emptyList(); // or simply "return new ArrayList<>();"
        }
    }
    
    
}
