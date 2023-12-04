package net.sneakycharactermanager.paper;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class Character {

    private static Map<Player, Character> characterMap = new HashMap<Player, Character>();

    private Player player;
    private String characterUUID;
    private String name;
    private String skin;

    public Character(String playerUUID, String characterUUID, String characterName, String skin) {
        this.player = Bukkit.getPlayer(UUID.fromString(playerUUID));
        this.characterUUID = characterUUID;
        this.name = characterName;
        this.skin = skin;

        File playerDir = new File(SneakyCharacterManager.getCharacterDataFolder(), playerUUID);
        boolean firstLogin = false;

        if (!playerDir.exists()) {
            firstLogin = true;
            playerDir.mkdirs();
        }

        File characterFile = new File(playerDir, characterUUID + ".yml");

        if (!characterFile.exists()) {
            YamlConfiguration config = new YamlConfiguration();
            if (firstLogin && !SneakyCharacterManager.getInstance().getConfig().getBoolean("deleteCharacterDataOnServerStart")) {
                Location playerLocation = player.getLocation();
                config.set("location.world", playerLocation.getWorld().getName());
                config.set("location.x", playerLocation.getX());
                config.set("location.y", playerLocation.getY());
                config.set("location.z", playerLocation.getZ());
                config.set("location.yaw", playerLocation.getYaw());
                config.set("location.pitch", playerLocation.getPitch());

                ItemStack[] inventoryContents = player.getInventory().getContents();
                for (int i = 0; i < 36; i++) {
                    if (i < inventoryContents.length && inventoryContents[i] != null) {
                        config.set("inventory." + i, inventoryContents[i]);
                    } else {
                        config.set("inventory." + i, new ItemStack(Material.AIR));
                    }
                }
            } else {
                config.set("location.world", Bukkit.getWorlds().get(0).getName());
                config.set("location.x", Bukkit.getWorlds().get(0).getSpawnLocation().getX());
                config.set("location.y", Bukkit.getWorlds().get(0).getSpawnLocation().getY());
                config.set("location.z", Bukkit.getWorlds().get(0).getSpawnLocation().getZ());
                config.set("location.yaw", 0.0);
                config.set("location.pitch", 0.0);

                for (int i = 0; i < 36; i++) {
                    config.set("inventory." + i, new ItemStack(Material.AIR));
                }
            }

            try {
                config.save(characterFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void load() {
        if (this.player != null && characterMap.containsKey(this.player)) {
            characterMap.get(this.player).save();
        }

        File playerDir = new File(SneakyCharacterManager.getCharacterDataFolder(), player.getUniqueId().toString());
        File characterFile = new File(playerDir, characterUUID + ".yml");

        if (!characterFile.exists()) {
            SneakyCharacterManager.getInstance().getLogger().severe("SneakyCharacterManager attempted to load character data from a file that does not exist: " + this.characterUUID);
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(characterFile);

        Location playerLocation = new Location(
                player.getServer().getWorld(config.getString("location.world")),
                config.getDouble("location.x"),
                config.getDouble("location.y"),
                config.getDouble("location.z"),
                (float) config.getDouble("location.yaw"),
                (float) config.getDouble("location.pitch")
        );
        player.teleport(playerLocation);

        ItemStack[] inventoryContents = new ItemStack[config.getInt("inventory.size", 36)];
        for (String key : config.getConfigurationSection("inventory").getKeys(false)) {
            int slot = Integer.parseInt(key);
            inventoryContents[slot] = config.getItemStack("inventory." + key);
        }
        player.getInventory().setContents(inventoryContents);

        //TODO: Load skin, apply nickname

        characterMap.put(this.player, this);
    }

    public void save() {
        File playerDir = new File(SneakyCharacterManager.getCharacterDataFolder(), this.player.getUniqueId().toString());

        if (!playerDir.exists()) {
            playerDir.mkdirs();
        }

        File characterFile = new File(playerDir, this.characterUUID + ".yml");

        if (!characterFile.exists()) {
            SneakyCharacterManager.getInstance().getLogger().severe("SneakyCharacterManager attempted to save character data to a file that does not exist: " + this.characterUUID);
            return;
        }

        YamlConfiguration config = new YamlConfiguration();

        Location playerLocation = player.getLocation();
        config.set("location.world", playerLocation.getWorld().getName());
        config.set("location.x", playerLocation.getX());
        config.set("location.y", playerLocation.getY());
        config.set("location.z", playerLocation.getZ());
        config.set("location.yaw", playerLocation.getYaw());
        config.set("location.pitch", playerLocation.getPitch());

        ItemStack[] inventoryContents = player.getInventory().getContents();
        for (int i = 0; i < 36; i++) {
            if (i < inventoryContents.length && inventoryContents[i] != null) {
                config.set("inventory." + i, inventoryContents[i]);
            } else {
                config.set("inventory." + i, new ItemStack(Material.AIR));
            }
        }

        try {
            config.save(characterFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
