package net.sneakycharactermanager.paper.handlers.character;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import net.sneakycharactermanager.common.io.AtomicFiles;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.character.CharacterKey;

/**
 * A character's Paper-local gameplay state (location, inventory, attributes) lives in
 * {@code characterdata/<owner>/<characterId>.yml}. It is keyed by the owner, never by whoever wears the character.
 */
final class GameplayFile {

    private GameplayFile() {}

    static File of(CharacterKey key) {
        return new File(ownerDirectory(key), key.characterId() + ".yml");
    }

    /** True once any character of this owner has a gameplay file on this server. */
    static boolean ownerHasFiles(CharacterKey key) {
        return ownerDirectory(key).exists();
    }

    /** A new character's state: world spawn and an empty inventory. */
    static YamlConfiguration fresh(int inventorySize) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("location", Bukkit.getWorlds().get(0).getSpawnLocation());
        config.set("inventory", emptyInventory(inventorySize));
        return config;
    }

    /** Writes a new file synchronously, so a quick reload can't miss it and start over. */
    static void create(File file, YamlConfiguration config) {
        file.getParentFile().mkdirs();
        try {
            AtomicFiles.writeUtf8(file.toPath(), config.saveToString());
        } catch (IOException e) {
            SneakyCharacterManager.getInstance().getLogger().severe("Could not create " + file + ": " + e.getMessage());
        }
    }

    static String emptyInventory(int size) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeInt(size);
            for (int i = 0; i < size; i++) {
                dataOutput.writeObject(new ItemStack(Material.AIR));
            }
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Could not convert inventory to base64.", e);
        }
    }

    private static File ownerDirectory(CharacterKey key) {
        return new File(SneakyCharacterManager.getCharacterDataFolder(), key.owner().toString());
    }
}
