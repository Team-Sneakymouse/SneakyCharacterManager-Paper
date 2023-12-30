package net.sneakycharactermanager.paper.commands;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.util.ChatUtility;

public class CommandCleanNameplates extends Command {

    public CommandCleanNameplates() {
        super("cleannameplates");
        this.description = "Remove all nameplate entities that aren't attached to a player.";
        this.usageMessage = "/cleanupnameplates";
        this.setPermission(SneakyCharacterManager.IDENTIFIER + ".command." + this.getName());
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        sender.sendMessage(ChatUtility.convertToComponent("&eCleaned up a total of "+ cleanNameplates() + " nameplates."));

        return true;
    }

    public static int cleanNameplates() {
        int total = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                Entity vehicle = entity.getVehicle();
                if (vehicle == null && entity.getScoreboardTags().contains("NicknameEntity")) {
                    entity.remove();
                    total++;
                }
            }
        }
        return total;
    }
    
}
