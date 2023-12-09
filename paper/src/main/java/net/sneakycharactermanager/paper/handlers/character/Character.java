package net.sneakycharactermanager.paper.handlers.character;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.util.InventoryUtility;
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

    private boolean firstLoad = false;

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
            this.firstLoad = true;
            if (firstLogin && !SneakyCharacterManager.getInstance().getConfig().getBoolean("deleteCharacterDataOnServerStart")) {
                this.save();
            } else {
                YamlConfiguration config = new YamlConfiguration();

                config.set("location", Bukkit.getWorlds().get(0).getSpawnLocation());

//                config.set("location.world", Bukkit.getWorlds().get(0).getName());
//                config.set("location.x", Bukkit.getWorlds().get(0).getSpawnLocation().getX());
//                config.set("location.y", Bukkit.getWorlds().get(0).getSpawnLocation().getY());
//                config.set("location.z", Bukkit.getWorlds().get(0).getSpawnLocation().getZ());
//                config.set("location.yaw", Bukkit.getWorlds().get(0).getSpawnLocation().getYaw());
//                config.set("location.pitch", Bukkit.getWorlds().get(0).getSpawnLocation().getPitch());

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

        Location playerLocation = config.getLocation("location");
        if(playerLocation == null)
            playerLocation = this.player.getLocation(); //No config location? Leave where they are
        this.player.teleport(playerLocation);

//        ItemStack[] inventoryContents = new ItemStack[config.getInt("inventory.size",  this.player.getInventory().getContents().length)];
//        for (String key : config.getConfigurationSection("inventory").getKeys(false)) {
//            int slot = Integer.parseInt(key);
//            inventoryContents[slot] = config.getItemStack("inventory." + key);
//        }

        ItemStack[] inventoryContents = InventoryUtility.getSavedInventory(config.getString("inventory"));
        this.player.getInventory().setContents(inventoryContents);

        //Loading The Nickname
        CharacterLoader.loadCharacter(this);

        this.map();
    }

    public void map() {
        characterMap.put(this.player, this);
    }

    public Player getPlayer(){
        return this.player;
    }

    public String getCharacterUUID() {
        return characterUUID;
    }

    public String getCharacterName(){
        return this.name;
    }

    public String getSkin(){
        return this.skin;
    }

    public void setFirstLoad(boolean firstLoad) {
        this.firstLoad = firstLoad;
    }
    
    public boolean isFirstLoad() {
        return firstLoad;
    }

    public void save() {
        File playerDir = new File(SneakyCharacterManager.getCharacterDataFolder(), this.player.getUniqueId().toString());

        if (!playerDir.exists()) {
            playerDir.mkdirs();
        }

        File characterFile = new File(playerDir, this.characterUUID + ".yml");

        YamlConfiguration config = new YamlConfiguration();

        Location playerLocation = this.player.getLocation();
        config.set("location", playerLocation);
//        config.set("location.world", playerLocation.getWorld().getName());
//        config.set("location.x", playerLocation.getX());
//        config.set("location.y", playerLocation.getY());
//        config.set("location.z", playerLocation.getZ());
//        config.set("location.yaw", playerLocation.getYaw());
//        config.set("location.pitch", playerLocation.getPitch());

//        ItemStack[] inventoryContents = this.player.getInventory().getContents();
//        for (int i = 0; i < inventoryContents.length; i++) {
//            if (inventoryContents[i] != null) {
//                config.set("inventory." + i, inventoryContents[i]);
//            } else {
//                config.set("inventory." + i, new ItemStack(Material.AIR));
//            }
//        }

        String inventoryB64 = InventoryUtility.inventoryToBase64(this.player.getInventory());
        config.set("inventory", inventoryB64);

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
