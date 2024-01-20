package net.sneakycharactermanager.paper.handlers.character;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.consolecommands.ConsoleCommandCharDisable;
import net.sneakycharactermanager.paper.util.InventoryUtility;

public class Character {

    private static Map<Player, Character> characterMap = new HashMap<>();

    private Player player;
    private String characterUUID;
    private String name;
    private String skin;
    private boolean slim;
    private List<String> tags = new ArrayList<>();

    private boolean firstLoad = false;

    public Character(String playerUUID, String characterUUID, String characterName, String skin, boolean slim, List<String> tags) {
        this.player = Bukkit.getPlayer(UUID.fromString(playerUUID));
        this.characterUUID = characterUUID;
        this.name = characterName;
        this.skin = skin;
        this.slim = slim;
        this.tags = tags;

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

                try {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

                    dataOutput.writeInt(this.player.getInventory().getSize());

                    for (int i = 0; i < this.player.getInventory().getSize(); i++) {
                        dataOutput.writeObject(new ItemStack(Material.AIR));
                    }

                    dataOutput.close();
                    config.set("inventory", Base64Coder.encodeLines(outputStream.toByteArray()));

                    //Converts the inventory and its contents to base64, This also saves item meta-data and inventory type
                } catch (Exception e) {
                    throw new IllegalStateException("Could not convert inventory to base64.", e);
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
            Character character = get(this.player);
            if (character != null) character.save();
        }

        File playerDir = new File(SneakyCharacterManager.getCharacterDataFolder(), this.player.getUniqueId().toString());
        File characterFile = new File(playerDir, this.characterUUID + ".yml");

        if (!characterFile.exists()) {
            SneakyCharacterManager.getInstance().getLogger().severe("SneakyCharacterManager attempted to load character data from a file that does not exist: " + this.characterUUID);
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(characterFile);

        Location playerLocation = config.getLocation("location");
        if (playerLocation == null)
            playerLocation = this.player.getLocation(); //No config location? Leave where they are

        Entity vehicle = this.player.getVehicle();

        List<Entity> passengers = this.player.getPassengers();

        if (CharacterLoader.loadCharacter(this)) {
            if (vehicle != null) {
                vehicle.removePassenger(this.player);
            }
            
            if (passengers.size() > 0) {
                for (Entity passenger : passengers) {
                    if (passenger.getType() != EntityType.TEXT_DISPLAY) {
                        this.player.removePassenger(passenger);
                    }
                }
            }
    
            this.player.teleport(playerLocation.add(0, 1, 0));
    
            ItemStack[] inventoryContents = InventoryUtility.getSavedInventory(config.getString("inventory"));
            this.player.getInventory().setContents(inventoryContents);

            characterMap.put(this.player, this);
            ConsoleCommandCharDisable.playerCharEnable(this.player.getUniqueId().toString());
        }

    }

    public Player getPlayer() {
        return this.player;
    }

    public String getCharacterUUID() {
        return this.characterUUID;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) { this.name = name; }

    public String getNameUnformatted() {
        Pattern pattern = Pattern.compile("<[^>]*>|&[0-9A-FK-ORa-fk-or]");
        Matcher matcher = pattern.matcher(this.name);
        return matcher.replaceAll("");
    }

    public String getSkin() {
        return this.skin;
    }

    public void setSkin(String skin) {
        this.skin = skin;
    }
    
    public boolean isSlim() {
        return this.slim;
    }

    public List<String> getTags() {
        return this.tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getTagsJoined() {
        return String.join(",", this.tags);
    }

    public boolean hasTag(String tag) {
        return this.tags.contains(tag.toLowerCase());
    }

    public void setSlim(boolean slim) {
        this.slim = slim;
    }

    public void setFirstLoad(boolean firstLoad) {
        this.firstLoad = firstLoad;
    }
    
    public boolean isFirstLoad() {
        return this.firstLoad;
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

        String inventoryB64 = InventoryUtility.inventoryToBase64(this.player.getInventory());
        config.set("inventory", inventoryB64);

        try {
            config.save(characterFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Nullable
    public static Character get(Player player) {
        return characterMap.get(player);
    }

    public static Collection<Character> getAll() {
        return characterMap.values();
    }

    public static void remove(Player player) {
        characterMap.remove(player);
    }

    public static void saveAll() {
        for (Player player : characterMap.keySet()) {
            if (player.isOnline()) {
                Character character = get(player);
                if (character != null) character.save();
            } else {
                SneakyCharacterManager.getInstance().getLogger().severe("SneakyCharacterManager found an offline player on the characterMap. They have been removed, but this should never happen: " + player.getName());
                characterMap.remove(player);
            }
        }
    }

    public static boolean isPlayedMapped(Player player) {
        return characterMap.containsKey(player);
    }

    public static boolean canPlayerLoadCharacter(Player player, String characterUUID) {
        if (player.hasPermission(SneakyCharacterManager.IDENTIFIER + ".admin.bypass.characterlocks")) return true;

        Boolean characterPerm = null;
        for (PermissionAttachmentInfo permission : player.getEffectivePermissions()) {
            if (permission.getPermission().equals(SneakyCharacterManager.IDENTIFIER + ".character." + characterUUID)) {
                characterPerm = permission.getValue();
            }
        }

        if (
            (characterPerm == null && !player.hasPermission(SneakyCharacterManager.IDENTIFIER + ".character.*")) ||
            (characterPerm != null && !characterPerm)
        ) {
            return false;
        }

        return true;
    }

}
