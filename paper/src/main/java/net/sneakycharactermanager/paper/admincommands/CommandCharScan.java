package net.sneakycharactermanager.paper.admincommands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.directory.CharacterDirectory;
import net.sneakycharactermanager.paper.api.directory.CharacterInfo;
import com.sneakyutils.util.TextUtility;
import com.sneakyutils.util.InventoryUtility;
import com.sneakyitems.api.SneakyItemsAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class CommandCharScan extends CommandBaseAdmin {

    /** Upper bound on one name lookup, so a slow proxy round trip cannot stall the result list. */
    private static final long NAME_LOOKUP_TIMEOUT_SECONDS = 10;

    File characterDataFolder;
    public CommandCharScan() {
        super("charscan");
        this.description = "Scan all inventories for an item id, name, or magic item name";
        this.usageMessage = "/charscan [name/id/magicitem] [Item Name / Item ID / Magic Item Name]";

        characterDataFolder = SneakyCharacterManager.getCharacterDataFolder();
    }

    /** One character whose saved inventory matched. */
    record Hit(UUID owner, String characterId) {}

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!SneakyCharacterManager.getInstance().getConfig().getBoolean("manageInventories", true)) {
            sender.sendMessage(TextUtility.INSTANCE.convertToComponent("&cmanageInventories is currently set to false in the config, so you probably don't want this command."));
            return false;
        }

        if(args.length < 2) {
            sender.sendMessage(TextUtility.INSTANCE.convertToComponent("&cInvalid Usage: " + this.usageMessage));
            return false;
        }

        String type = args[0];
        StringBuilder builder = new StringBuilder();

        for(int i = 1; i < args.length; i++){
            builder.append(args[i]).append(" ");
        }
        String itemData = builder.substring(0, builder.length()-1);

        Predicate<ItemStack> matcher;
        if(type.equalsIgnoreCase("id")) matcher = byId(itemData);
        else if(type.equalsIgnoreCase("name")) matcher = byName(itemData);
        else if(type.equalsIgnoreCase("magicitem")) matcher = byMagicItem(itemData);
        else {
            sender.sendMessage(TextUtility.INSTANCE.convertToComponent("&cInvalid Usage: " + this.usageMessage));
            return false;
        }

        sender.sendMessage(TextUtility.INSTANCE.convertToComponent("&eSearching for item..."));
        SneakyCharacterManager plugin = SneakyCharacterManager.getInstance();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Hit> hits = scan(matcher);
            List<CompletableFuture<Component>> lines = new ArrayList<>(hits.size());
            for(Hit hit : hits) lines.add(describe(hit));
            CompletableFuture.allOf(lines.toArray(new CompletableFuture[0])).whenComplete((ignored, error) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for(CompletableFuture<Component> line : lines) sender.sendMessage(line.join());
                        sender.sendMessage(TextUtility.INSTANCE.convertToComponent("&eFinished"));
                    }));
        });
        return false;
    }


    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        if(args.length == 1){
            return List.of("id", "name", "magicitem");
        }
        return super.tabComplete(sender, alias, args);
    }

    private static Predicate<ItemStack> byId(String id){
        return item -> {
            String key = item.getType().getKey().toString();
            return key.equalsIgnoreCase(id) || key.replace("minecraft:", "").equalsIgnoreCase(id);
        };
    }

    private static Predicate<ItemStack> byName(String name){
        return item -> {
            String key;
            if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
                Component displayName = item.getItemMeta().displayName();
                key = PlainTextComponentSerializer.plainText().serialize(displayName);
            } else {
                // Return the type name of the item if there's no custom display name
                key = item.getType().toString().toLowerCase().replace('_', ' ');
            }
            return key.equalsIgnoreCase(name) || key.replace("minecraft:", "").equalsIgnoreCase(name);
        };
    }

    /** Matches the SneakyItems magic-item PDC key (e.g. {@code item-characterCrystal}). */
    private static Predicate<ItemStack> byMagicItem(String magicItem){
        return item -> magicItem.equalsIgnoreCase(SneakyItemsAPI.keyOf(item));
    }

    /**
     * "Item Found: Player: X | Character: Y" with the character UUID on hover (click copies it). Falls back to
     * printing the UUID when the name cannot be looked up (SCM directory unavailable, no player online for a proxy
     * round trip, timeout, unknown id).
     */
    private static CompletableFuture<Component> describe(Hit hit){
        String playerName = Optional.ofNullable(Bukkit.getOfflinePlayer(hit.owner()).getName()).orElse(hit.owner().toString());
        UUID characterUuid;
        try {
            characterUuid = UUID.fromString(hit.characterId());
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(line(playerName, null, hit.characterId()));
        }

        Optional<CharacterDirectory> directory = CharacterDirectory.get();
        if(directory.isEmpty()) return CompletableFuture.completedFuture(line(playerName, null, hit.characterId()));

        CompletableFuture<Optional<CharacterInfo>> lookup;
        try {
            lookup = directory.get().character(characterUuid);
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(line(playerName, null, hit.characterId()));
        }
        return lookup
                .completeOnTimeout(Optional.empty(), NAME_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .handle((info, error) -> line(playerName,
                        error == null && info != null ? info.map(CharacterInfo::name).orElse(null) : null,
                        hit.characterId()));
    }

    static Component line(String playerName, String characterName, String characterId){
        Component character = characterName == null || characterName.isBlank()
                ? Component.text(characterId, NamedTextColor.GREEN)
                : Component.text(characterName, NamedTextColor.GREEN)
                        .hoverEvent(HoverEvent.showText(Component.text("Character UUID: " + characterId + "\nClick to copy", NamedTextColor.GRAY)))
                        .clickEvent(ClickEvent.copyToClipboard(characterId));
        return Component.text("Item Found: Player: " + playerName + " | Character: ", NamedTextColor.GREEN).append(character);
    }

    private List<Hit> scan(Predicate<ItemStack> matches){
        List<Hit> result = new ArrayList<>();
        if(!characterDataFolder.exists() || !characterDataFolder.isDirectory()) return result; //Shouldn't be possible but just incase

        File[] playerData = characterDataFolder.listFiles();
        if(playerData == null) return result; //Also shouldn't be possible if a player is the one executing or any player has joined

        for(File playerFile : playerData){
            if(!playerFile.isDirectory()) continue;
            UUID owner;
            try {
                owner = UUID.fromString(playerFile.getName());
            } catch (IllegalArgumentException e) {
                continue;
            }
            File[] characterData = playerFile.listFiles();
            if(characterData == null) continue;

            for(File characterFile : characterData){
                if(!characterFile.getName().endsWith(".yml")) continue;
                String characterUUID = characterFile.getName().substring(0, characterFile.getName().length()-4); //Remove .yml
                YamlConfiguration config = YamlConfiguration.loadConfiguration(characterFile);
                String inventoryB64 = config.getString("inventory");
                if(inventoryB64 == null) continue;

                ItemStack[] items;
                try {
                    items = InventoryUtility.getSavedInventory(inventoryB64);
                } catch (IOException e) {
                    continue;
                }
                for(ItemStack item : items){
                    if(item == null) continue;
                    if(matches.test(item)){
                        result.add(new Hit(owner, characterUUID));
                        break;
                    }
                }
            }
        }

        return result;
    }
}
