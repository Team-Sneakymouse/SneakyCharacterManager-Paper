package net.sneakycharactermanager.paper.commands;

import java.util.*;

import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.CharacterNickChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.consolecommands.ConsoleCommandCharTemp;
import net.sneakycharactermanager.paper.util.ProxyMessagingUtil;
import net.sneakycharactermanager.paper.util.ChatUtility;

public class CommandNick extends CommandBase {

    public CommandNick() {
        super("nick");
        this.description = "Change the name of your character!";
        this.usageMessage = "/nick [Name]";
        this.setPermission(SneakyCharacterManager.IDENTIFIER + ".command." + this.getName());
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatUtility.convertToComponent("&4Must be a player to run this command"));
            return false;
        }

        if (ConsoleCommandCharTemp.isPlayerTempChar(player.getUniqueId().toString())) {
            player.sendMessage(ChatUtility.convertToComponent("&4You are currently on a template character, which do not support /nick and /skin."));
            return false;
        };

        if (args.length == 0) {
            player.sendMessage(ChatUtility.convertToComponent("&4Invalid Usage: " + this.usageMessage));
            return false;
        }

        StringBuilder builder = new StringBuilder();
        for(String word : args) {
            builder.append(word).append(" ");
        }
        String nickname = builder.substring(0, builder.length()-1);

        if (nickname.length() > 32 && !player.hasPermission(SneakyCharacterManager.IDENTIFIER + ".formatnames")) {
            player.sendMessage(ChatUtility.convertToComponent("&4That name is too long! No more than 32 characters."));
            return false;
        }

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
        String pattern = "^.*[^áéíóúýÁÉÍÓÚÝàèìòùÀÈÌÒÙâêîôûÂÊÎÔÛäëïöüÿÄËÏÖÜŸãñõÃÑÕçÇåÅčďěňřšťžČĎĚŇŘŠŤŽąęĄĘÆØæø\\w\\-\\'\\\"\\`<>&\\. ].*$";
        if(nickname.matches(pattern) || (!player.hasPermission(SneakyCharacterManager.IDENTIFIER + ".formatnames") && nickname.matches("^.*[<>&].*$"))){
            player.sendMessage(ChatUtility.convertToComponent("&4Invalid name! It cannot contain special characters! Quotes, Spaces, and Diacritics are okay."));
            return false;
        }

        Character character = Character.get(player);
        if(character == null) {
            player.sendMessage(ChatUtility.convertToComponent("&cSorry! Failed to retrieve character!"));
            return false;
        }

        CharacterNickChangeEvent event = new CharacterNickChangeEvent(player, character.getCharacterUUID(), character.getName(), nickname);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        character.setName(nickname);
        SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(player, character.getDisplayName());
        ProxyMessagingUtil.sendByteArray(player, "updateCharacter", player.getUniqueId().toString(), character.getCharacterUUID(), 2, nickname);
        player.sendMessage(ChatUtility.convertToComponent("&eName updated to: " + nickname));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        return new ArrayList<>();
    }

}
