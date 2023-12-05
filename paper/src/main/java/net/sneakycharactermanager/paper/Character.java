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

        File characterFile = new File(playerDir, this.characterUUID + ".yml");

        if (!characterFile.exists()) {
            if (firstLogin && !SneakyCharacterManager.getInstance().getConfig().getBoolean("deleteCharacterDataOnServerStart")) {
                this.save();
            } else {
                YamlConfiguration config = new YamlConfiguration();

                config.set("location.world", Bukkit.getWorlds().get(0).getName());
                config.set("location.x", Bukkit.getWorlds().get(0).getSpawnLocation().getX());
                config.set("location.y", Bukkit.getWorlds().get(0).getSpawnLocation().getY());
                config.set("location.z", Bukkit.getWorlds().get(0).getSpawnLocation().getZ());
                config.set("location.yaw", Bukkit.getWorlds().get(0).getSpawnLocation().getYaw());
                config.set("location.pitch", Bukkit.getWorlds().get(0).getSpawnLocation().getPitch());

                for (int i = 0; i < this.player.getInventory().getContents().length; i++) {
                    config.set("inventory." + i, new ItemStack(Material.AIR));
                }

                try {
                    config.save(characterFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void load() {
        if (this.player != null && characterMap.containsKey(this.player)) {
            get(this.player).save();
        }

        File playerDir = new File(SneakyCharacterManager.getCharacterDataFolder(), this.player.getUniqueId().toString());
        File characterFile = new File(playerDir, this.characterUUID + ".yml");

        if (!characterFile.exists()) {
            SneakyCharacterManager.getInstance().getLogger().severe("SneakyCharacterManager attempted to load character data from a file that does not exist: " + this.characterUUID);
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(characterFile);

        Location playerLocation = new Location(
                this.player.getServer().getWorld(config.getString("location.world")),
                config.getDouble("location.x"),
                config.getDouble("location.y"),
                config.getDouble("location.z"),
                (float) config.getDouble("location.yaw"),
                (float) config.getDouble("location.pitch")
        );
        this.player.teleport(playerLocation);

        ItemStack[] inventoryContents = new ItemStack[config.getInt("inventory.size", 36)];
        for (String key : config.getConfigurationSection("inventory").getKeys(false)) {
            int slot = Integer.parseInt(key);
            inventoryContents[slot] = config.getItemStack("inventory." + key);
        }
        this.player.getInventory().setContents(inventoryContents);

        //TODO: Load skin, apply nickname

        this.map();
    }

    public void map() {
        characterMap.put(this.player, this);
    }

    public void save() {
        File playerDir = new File(SneakyCharacterManager.getCharacterDataFolder(), this.player.getUniqueId().toString());

        if (!playerDir.exists()) {
            playerDir.mkdirs();
        }

        File characterFile = new File(playerDir, this.characterUUID + ".yml");

        YamlConfiguration config = new YamlConfiguration();

        Location playerLocation = this.player.getLocation();
        config.set("location.world", playerLocation.getWorld().getName());
        config.set("location.x", playerLocation.getX());
        config.set("location.y", playerLocation.getY());
        config.set("location.z", playerLocation.getZ());
        config.set("location.yaw", playerLocation.getYaw());
        config.set("location.pitch", playerLocation.getPitch());

        ItemStack[] inventoryContents = this.player.getInventory().getContents();
        for (int i = 0; i < inventoryContents.length; i++) {
            if (inventoryContents[i] != null) {
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

    public static Character get(Player player) {
        return characterMap.get(player);
    }

    public static void remove(Player player) {
        characterMap.remove(player);
    }

    public static void saveAll() {
        for (Player player : characterMap.keySet()) {
            if (player.isOnline()) {
                get(player).save();
            } else {
                SneakyCharacterManager.getInstance().getLogger().severe("SneakyCharacterManager found an offline player on the characterMap. They have been removed, but this should never happen: " + player.getName());
                characterMap.remove(player);
            }
        }
    }

    public static boolean isPlayedMapped(Player player) {
        return characterMap.containsKey(player);
    }

}
