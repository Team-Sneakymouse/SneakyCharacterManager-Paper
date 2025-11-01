package net.sneakycharactermanager.paper.admincommands;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.util.ChatUtility;
import net.sneakycharactermanager.paper.util.InventoryUtility;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class CommandMigrateInventories extends CommandBaseAdmin {

    private File characterDataFolder;

    public CommandMigrateInventories() {
        super("migrateinventories");
        this.description = "Migrate all character inventories from old base64 format to new YAML format";
        this.usageMessage = "/migrateinventories [confirm]";
        this.characterDataFolder = SneakyCharacterManager.getCharacterDataFolder();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!SneakyCharacterManager.getInstance().getConfig().getBoolean("manageInventories", true)) {
            sender.sendMessage(ChatUtility.convertToComponent("&cmanageInventories is currently set to false in the config, so you probably don't want this command."));
            return false;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatUtility.convertToComponent("&e=== INVENTORY MIGRATION COMMAND ==="));
            sender.sendMessage(ChatUtility.convertToComponent("&eThis command will migrate all character inventories from the old"));
            sender.sendMessage(ChatUtility.convertToComponent("&ebase64 binary format to the new YAML format for better"));
            sender.sendMessage(ChatUtility.convertToComponent("&ebackwards compatibility with Minecraft version changes."));
            sender.sendMessage(ChatUtility.convertToComponent(""));
            sender.sendMessage(ChatUtility.convertToComponent("&c&lWARNING: &cThis will modify all character data files!"));
            sender.sendMessage(ChatUtility.convertToComponent("&cMake sure to backup your character data folder first!"));
            sender.sendMessage(ChatUtility.convertToComponent(""));
            sender.sendMessage(ChatUtility.convertToComponent("&eTo proceed, run: &f/migrateinventories confirm"));
            return true;
        }

        if (!args[0].equalsIgnoreCase("confirm")) {
            sender.sendMessage(ChatUtility.convertToComponent("&cInvalid argument. Use 'confirm' to proceed with migration."));
            return false;
        }

        // Start migration
        sender.sendMessage(ChatUtility.convertToComponent("&eStarting inventory migration..."));
        
        MigrationResult result = performMigration(sender);
        
        // Report results
        sender.sendMessage(ChatUtility.convertToComponent("&e=== MIGRATION COMPLETE ==="));
        sender.sendMessage(ChatUtility.convertToComponent("&aTotal characters processed: " + result.totalProcessed));
        sender.sendMessage(ChatUtility.convertToComponent("&aSuccessfully migrated: " + result.successful));
        sender.sendMessage(ChatUtility.convertToComponent("&cFailed migrations: " + result.failed));
        sender.sendMessage(ChatUtility.convertToComponent("&eSkipped (already YAML): " + result.skipped));
        
        if (result.failed > 0) {
            sender.sendMessage(ChatUtility.convertToComponent("&cSome migrations failed. Check console for details."));
        }
        
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        if (args.length == 1) {
            return List.of("confirm");
        }
        return super.tabComplete(sender, alias, args);
    }

    private MigrationResult performMigration(CommandSender sender) {
        MigrationResult result = new MigrationResult();
        
        if (!characterDataFolder.exists() || !characterDataFolder.isDirectory()) {
            sender.sendMessage(ChatUtility.convertToComponent("&cCharacter data folder not found!"));
            return result;
        }

        File[] playerData = characterDataFolder.listFiles();
        if (playerData == null) {
            sender.sendMessage(ChatUtility.convertToComponent("&cNo player data found!"));
            return result;
        }

        for (File playerFile : playerData) {
            if (!playerFile.isDirectory()) continue;
            
            String playerName = "Unknown";
            try {
                playerName = Bukkit.getOfflinePlayer(UUID.fromString(playerFile.getName())).getName();
            } catch (IllegalArgumentException e) {
                // Invalid UUID, skip
                continue;
            }

            File[] characterData = playerFile.listFiles();
            if (characterData == null) continue;

            for (File characterFile : characterData) {
                if (!characterFile.getName().endsWith(".yml")) continue;
                
                String characterUUID = characterFile.getName().substring(0, characterFile.getName().length() - 4);
                result.totalProcessed++;
                
                try {
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(characterFile);
                    String inventoryData = config.getString("inventory");
                    
                    if (inventoryData == null) {
                        // No inventory data, skip
                        result.skipped++;
                        continue;
                    }
                    
                    // Check if it's already in YAML format (contains "size:" and "items:")
                    if (inventoryData.contains("size:") && inventoryData.contains("items:")) {
                        sender.sendMessage(ChatUtility.convertToComponent("&7" + playerName + " - " + characterUUID + " (already YAML)"));
                        result.skipped++;
                        continue;
                    }
                    
                    // Attempt migration using Paper's native serialization
                    String migratedYaml = InventoryUtility.migrateBase64ToPaperYaml(inventoryData);
                    
                    if (migratedYaml != null) {
                        // Migration successful, update the file
                        config.set("inventory", migratedYaml);
                        config.save(characterFile);
                        
                        sender.sendMessage(ChatUtility.convertToComponent("&a" + playerName + " - " + characterUUID + " (migrated)"));
                        result.successful++;
                    } else {
                        // Migration failed
                        sender.sendMessage(ChatUtility.convertToComponent("&c" + playerName + " - " + characterUUID + " (FAILED)"));
                        SneakyCharacterManager.getInstance().getLogger().warning("Failed to migrate inventory for " + playerName + " character " + characterUUID);
                        result.failed++;
                    }
                    
                } catch (IOException e) {
                    sender.sendMessage(ChatUtility.convertToComponent("&c" + playerName + " - " + characterUUID + " (FILE ERROR)"));
                    SneakyCharacterManager.getInstance().getLogger().severe("Failed to save migrated data for " + playerName + " character " + characterUUID + ": " + e.getMessage());
                    result.failed++;
                } catch (Exception e) {
                    sender.sendMessage(ChatUtility.convertToComponent("&c" + playerName + " - " + characterUUID + " (ERROR)"));
                    SneakyCharacterManager.getInstance().getLogger().severe("Unexpected error migrating " + playerName + " character " + characterUUID + ": " + e.getMessage());
                    result.failed++;
                }
            }
        }
        
        return result;
    }

    private static class MigrationResult {
        int totalProcessed = 0;
        int successful = 0;
        int failed = 0;
        int skipped = 0;
    }
}
