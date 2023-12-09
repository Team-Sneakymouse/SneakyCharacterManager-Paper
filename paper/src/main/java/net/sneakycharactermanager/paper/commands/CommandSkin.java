package net.sneakycharactermanager.paper.commands;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.CharacterLoader;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.util.ChatUtility;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CommandSkin extends Command {

    public CommandSkin() {
        super("skin");
        this.description = "Change your skin!";
        this.setUsage("/skin <URL (Must be direct image)>");
        this.setPermission("sneakycharacters.skin");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if(!(sender instanceof Player player)) return true;

        if(args.length != 1){
            player.sendMessage(ChatUtility.convertToComponent("&4Invalid Usage: " + this.getUsage()));
            return true;
        }

        String url = args[0];
        if(!url.startsWith("http")){
            player.sendMessage(ChatUtility.convertToComponent("&4Invalid URL.. Please make sure it starts with HTTP(s)"));
            return true;
        }

        BungeeMessagingUtil.sendByteArray("updateCharacter", player.getUniqueId().toString(), 1, url);
        player.sendMessage(ChatUtility.convertToComponent("&aUpdaing your skin!"));

        CharacterLoader.updateSkin(player, url);

        return true;
    }
    
}
