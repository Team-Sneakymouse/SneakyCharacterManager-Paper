package net.sneakycharactermanager.paper.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.consolecommands.ConsoleCommandCharTemp;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.Gender;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.util.ChatUtility;

public class CommandCharGender extends CommandBase {

	private static final List<String> OPTIONS = Arrays.asList("masculine", "feminine", "nonbinary", "clear");

	public CommandCharGender() {
		super("chargender");
		this.description = "Set the gender for your current character.";
		this.usageMessage = "/chargender <masculine|feminine|nonbinary|clear>";
		this.setPermission(SneakyCharacterManager.IDENTIFIER + ".command." + this.getName());
	}

	@Override
	public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {

		if (!(sender instanceof Player player)) {
			sender.sendMessage(ChatUtility.convertToComponent("&4Must be a player to run this command"));
			return false;
		}

		if (ConsoleCommandCharTemp.isPlayerTempChar(player.getUniqueId().toString())) {
			player.sendMessage(ChatUtility.convertToComponent("&4Template characters do not support /chargender."));
			return false;
		}

		if (args.length != 1) {
			player.sendMessage(ChatUtility.convertToComponent("&4Invalid Usage: " + this.usageMessage));
			return false;
		}

		String input = args[0].toLowerCase();
		if (!OPTIONS.contains(input)) {
			player.sendMessage(ChatUtility.convertToComponent("&4Invalid gender. Use masculine, feminine, nonbinary, or clear."));
			return false;
		}

		Character character = Character.get(player);
		if (character == null) {
			player.sendMessage(ChatUtility.convertToComponent("&cFailed to retrieve character."));
			return false;
		}

		Gender gender = input.equals("clear") ? null : Gender.fromString(input);
		character.setGender(gender);
		SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(player, character.getDisplayName());

		BungeeMessagingUtil.sendByteArray(player, "updateCharacter", player.getUniqueId().toString(),
				character.getCharacterUUID(), 5, Gender.toConfigKeyNullable(gender));

		if (gender == null) {
			player.sendMessage(ChatUtility.convertToComponent("&aCleared gender for this character."));
		} else {
			player.sendMessage(ChatUtility.convertToComponent("&aSet gender to &b" + Gender.toConfigKeyNullable(gender) + "&a."));
		}
		return true;
	}

	@Override
	public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
		if (args.length == 1) {
			List<String> matches = new ArrayList<>();
			for (String option : OPTIONS) {
				if (option.startsWith(args[0].toLowerCase())) {
					matches.add(option);
				}
			}
			return matches;
		}
		return new ArrayList<>();
	}
}

