package net.sneakycharactermanager.paper.commands;

import java.util.*;

import net.sneakycharactermanager.paper.handlers.character.Character;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.util.ChatUtility;

public class CommandNick extends Command {
    public CommandNick() {
        super("nick");
        this.description = "Change the name of your character!";
        this.usageMessage = "/nick <Name>";
        this.setPermission(SneakyCharacterManager.IDENTIFIER + ".command." + this.getName());
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {

        if (!(sender instanceof Player player)) return false;

        if (args.length == 0) {
            player.sendMessage(ChatUtility.convertToComponent("&4Invalid Usage: " + this.usageMessage));
            return false;
        }

        StringBuilder builder = new StringBuilder();
        for(String word : args) {
            builder.append(word).append(" ");
        }
        String nickname = builder.substring(0, builder.length()-1);

        List<String> bannedWords = SneakyCharacterManager.getInstance().getConfig().getStringList("bannedWords");

        boolean containsBannedWord = false;
        if(!bannedWords.isEmpty()){
            for(String word : bannedWords){
                if(nickname.toLowerCase().contains(word.toLowerCase())){
                    containsBannedWord = true;
                    break;
                }
            }
        }

        if(containsBannedWord){
            player.sendMessage(ChatUtility.convertToComponent("&4Your name contains a banned word! *Insert Ban Message Here*"));
            return false;
        }

        //Name Filtering:
        String pattern = "[^\\p{L}\\p{M}0-9\\-\"' &<>]+";
        if(nickname.matches(".*" + pattern + ".*")){
            player.sendMessage(ChatUtility.convertToComponent("&4Invalid name! It cannot contain special characters! Quotes, Spaces, and Diacritics are okay."));
            return false;
        }
        String pattern2 = "[&<>]";
        if(nickname.matches(".*" + pattern2 + ".*") && !player.hasPermission(SneakyCharacterManager.IDENTIFIER + ".formatnames")){
            player.sendMessage(ChatUtility.convertToComponent("&4Invalid name! It cannot contain special characters! Quotes, Spaces, and Diacritics are okay."));
            return false;
        }

        Character character = Character.get(player);
        if(character == null) {
            player.sendMessage(ChatUtility.convertToComponent("&cSorry! Failed to retrieve character!"));
            return false;
        }
        character.setName(nickname);
        SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(player, nickname);
        BungeeMessagingUtil.sendByteArray(player, "updateCharacter", player.getUniqueId().toString(), 2, nickname);
        player.sendMessage(ChatUtility.convertToComponent("&eName updated to: " + nickname));
        return false;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        return new ArrayList<>();
    }

}
