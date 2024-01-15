package net.sneakycharactermanager.paper.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.util.ChatUtility;
import net.sneakycharactermanager.paper.util.InventoryUtility;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CommandCharScan extends Command {

    File characterDataFolder;
    public CommandCharScan() {
        super("charscan");
        this.description = "Scan all inventories for a item id or name";
        this.usageMessage = "/charscan <name/id> <Item Name / Item ID>";
        this.setPermission(SneakyCharacterManager.IDENTIFIER + ".commandadmin." + this.getName());

        characterDataFolder = SneakyCharacterManager.getCharacterDataFolder();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {

        if(args.length < 2) {
            sender.sendMessage(ChatUtility.convertToComponent("&cInvalid Usage: " + this.usageMessage));
            return false;
        }

        sender.sendMessage(ChatUtility.convertToComponent("&eSearching for item..."));
        String type = args[0];
        StringBuilder builder = new StringBuilder();

        for(int i = 1; i < args.length; i++){
            builder.append(args[i]).append(" ");
        }
        String itemData = builder.substring(0, builder.length()-1);

        if(type.equalsIgnoreCase("id")){
            for(String result : getById(itemData)){
                sender.sendMessage(ChatUtility.convertToComponent(result));
            }
        }
        else if(type.equalsIgnoreCase("name")){
            for(String result : getByName(itemData)){
                sender.sendMessage(ChatUtility.convertToComponent(result));
            }
        }
        else {
            sender.sendMessage(ChatUtility.convertToComponent("&cInvalid Usage: " + this.usageMessage));
            return false;
        }
        sender.sendMessage(ChatUtility.convertToComponent("&eFinished"));
        return false;
    }


    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        if(args.length == 1){
            return List.of("id", "name");
        }
        return super.tabComplete(sender, alias, args);
    }

    //Response: Item Found: Player: "Player Name" | Character: "Character UUID"
    private List<String> getById(String id){
        List<String> result = new ArrayList<>();
        if(!characterDataFolder.exists() || !characterDataFolder.isDirectory()) return result; //Shouldn't be possible but just incase

        File[] playerData = characterDataFolder.listFiles();
        if(playerData == null) return result; //Also shouldn't be possible if a player is the one executing or any player has joined

        for(File playerFile : playerData){
            if(!playerFile.isDirectory()) continue;
            File[] characterData = playerFile.listFiles();
            if(characterData == null) continue;

            for(File characterFile : characterData){
                String characterUUID = characterFile.getName().substring(0, characterFile.getName().length()-4); //Remove .yml
                YamlConfiguration config = YamlConfiguration.loadConfiguration(characterFile);
                String inventoryB64 = config.getString("inventory");
                if(inventoryB64 == null) continue;

                ItemStack[] items = InventoryUtility.getSavedInventory(inventoryB64);
                for(ItemStack item : items){
                    if(item == null) continue;
                    String key = item.getType().getKey().toString();

                    if(key.equalsIgnoreCase(id) || key.replace("minecraft:", "").equalsIgnoreCase(id)){
                        result.add("&aItem Found: Player: " + Bukkit.getOfflinePlayer(UUID.fromString(playerFile.getName())).getName()
                         + " | Character UUID: " + characterUUID);
                        break;
                    }
                }
            }
        } //Casual O(n^3) Time complexity.. Not like that's horrible or anything for anything more than 10 players? *cries to sleep*

        return result;
    }

    private List<String> getByName(String name){
        List<String> result = new ArrayList<>();
        if(!characterDataFolder.exists() || !characterDataFolder.isDirectory()) return result; //Shouldn't be possible but just incase

        File[] playerData = characterDataFolder.listFiles();
        if(playerData == null) return result; //Also shouldn't be possible if a player is the one executing or any player has joined

        for(File playerFile : playerData){
            if(!playerFile.isDirectory()) continue;
            File[] characterData = playerFile.listFiles();
            if(characterData == null) continue;

            for(File characterFile : characterData){
                String characterUUID = characterFile.getName().substring(0, characterFile.getName().length()-4); //Remove .yml
                YamlConfiguration config = YamlConfiguration.loadConfiguration(characterFile);
                String inventoryB64 = config.getString("inventory");
                if(inventoryB64 == null) continue;

                ItemStack[] items = InventoryUtility.getSavedInventory(inventoryB64);
                for(ItemStack item : items){
                    if(item == null) continue;
                    String key;
                    if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
                        Component displayName = item.getItemMeta().displayName();
                        key =  PlainTextComponentSerializer.plainText().serialize(displayName);
                    } else {
                        // Return the type name of the item if there's no custom display name
                        key = item.getType().toString().toLowerCase().replace('_', ' ');
                    }

                    if(key.equalsIgnoreCase(name) || key.replace("minecraft:", "").equalsIgnoreCase(name)){
                        result.add("&aItem Found: Player: " + Bukkit.getOfflinePlayer(UUID.fromString(playerFile.getName())).getName()
                                + " | Character UUID: " + characterUUID);
                        break;
                    }
                }
            }
        } //Casual O(n^3) Time complexity.. Not like that's horrible or anything for anything more than 10 players? *cries to sleep*

        return result;
    }
}
