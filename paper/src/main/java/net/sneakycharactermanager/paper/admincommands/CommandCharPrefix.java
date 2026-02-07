package net.sneakycharactermanager.paper.admincommands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.util.ChatUtility;

public class CommandCharPrefix extends CommandBaseAdmin {

	public CommandCharPrefix() {
		super("charprefix");
		this.description = "Set or clear a temporary name prefix for a character.";
		this.usageMessage = "/charprefix <playerName> \"Prefix\"|clear";
	}

	@Override
	public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
		if (args.length < 2) {
			sender.sendMessage(ChatUtility.convertToComponent("&4Invalid Usage: " + this.usageMessage));
			return false;
		}

		Player target = Bukkit.getPlayer(args[0]);

		if (target == null) {
			sender.sendMessage(ChatUtility.convertToComponent("&aUnknown player: &b" + args[0]));
			return false;
		}

		Character character = Character.get(target);

		if (character == null) {
			sender.sendMessage(
					ChatUtility.convertToComponent("&aThis player is not currently on a character: &b" + args[0]));
			return false;
		}

		if (args.length == 2 && args[1].equalsIgnoreCase("clear")) {
			character.setNamePrefix("");
			SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(target, character.getDisplayName());

			sender.sendMessage(ChatUtility
					.convertToComponent("&aCleared name prefix for &b'" + character.getNameUnformatted() + "'&a."));

			if (!sender.equals(target))
				target.sendMessage(ChatUtility.convertToComponent("&eYour name prefix was cleared."));

			return true;
		}

		String rawInput = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();

		if (rawInput.length() < 2 || !rawInput.startsWith("\"") || !rawInput.endsWith("\"")) {
			sender.sendMessage(ChatUtility.convertToComponent("&4Invalid Usage: " + this.usageMessage + " &7(Remember the quotes)"));
			return false;
		}

		String prefix = rawInput.substring(1, rawInput.length() - 1);

		character.setNamePrefix(prefix);
		SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(target, character.getDisplayName());

		sender.sendMessage(ChatUtility.convertToComponent(
				"&aSet name prefix for &b'" + character.getNameUnformatted() + "'&a to: &r" + prefix));

		if (!sender.equals(target))
			target.sendMessage(
					ChatUtility.convertToComponent("&eYour name prefix was set to: &r" + prefix));

		return true;
	}

	@Override
	public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
		if (args.length == 1) {
			List<String> playerNames = new ArrayList<>();

			for (Player player : Bukkit.getOnlinePlayers()) {
				if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())
						&& !player.getName().equals("CMI-Fake-Operator"))
					playerNames.add(player.getName());
			}

			return playerNames;
		} else if (args.length == 2) {
			return List.of("clear");
		}

		return new ArrayList<>();
	}

}

