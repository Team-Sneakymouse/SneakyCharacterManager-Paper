package net.sneakycharactermanager.paper.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.sneakyutils.util.TextUtility;

import net.sneakycharactermanager.paper.SneakyCharacterManager;

public class CommandTitle extends CommandBase {

	public CommandTitle() {
		super("title");
		this.description = "Select a title for your current character.";
		this.usageMessage = "/title";
	}

	@Override
	public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(TextUtility.INSTANCE.convertToComponent("&4Must be a player to run this command"));
			return false;
		}

		SneakyCharacterManager.getInstance().titleSelectionMenu.open(player);
		return true;
	}
}
