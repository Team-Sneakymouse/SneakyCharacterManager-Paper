package net.sneakycharactermanager.paper.admincommands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.sneakyutils.util.TextUtility;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;

public class CommandCharReload extends CommandBaseAdmin {

	public CommandCharReload() {
		super("charreload");
		this.description = "Reload SneakyCharacterManager config.yml from disk.";
		this.usageMessage = "/charreload";
	}

	@Override
	public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
		SneakyCharacterManager plugin = SneakyCharacterManager.getInstance();
		plugin.reloadConfig();

		for (Player player : Bukkit.getOnlinePlayers()) {
			Character character = Character.get(player);
			if (character == null) {
				continue;
			}
			plugin.nametagManager.nicknamePlayer(player, character.getDisplayName());
		}

		sender.sendMessage(TextUtility.INSTANCE.convertToComponent("&aReloaded SneakyCharacterManager config."));
		return true;
	}

}
