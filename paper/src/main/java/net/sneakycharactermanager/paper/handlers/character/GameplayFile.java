package net.sneakycharactermanager.paper.handlers.character;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.character.CharacterKey;

/**
 * A character's Paper-local gameplay state (location, inventory, attributes) lives in
 * {@code characterdata/<owner>/<characterId>.yml}. It is keyed by the owner, never by whoever wears the character.
 *
 * <p>Writes go through the plugin's async writer. Until a write lands, {@link #read} and {@link #exists} see the
 * newest queued content, so loading a character right after it was saved (the same body reloading it, or another
 * body picking it up) never reads an older file.
 */
final class GameplayFile {

    /** Newest write queued for each file and not yet on disk. */
    private static final Map<Path, Queued> unwritten = new ConcurrentHashMap<>();

    /** One queued write. Compared by identity: only the write that queued an entry may clear it, even if a later
     *  write happens to carry the same content (A, B, A). */
    private static final class Queued {
        final String content;
        Queued(String content) { this.content = content; }
    }

    private GameplayFile() {}

    static File of(CharacterKey key) {
        return new File(ownerDirectory(key), key.characterId() + ".yml");
    }

    /** True once any character of this owner has a gameplay file on this server. */
    static boolean ownerHasFiles(CharacterKey key) {
        return ownerDirectory(key).exists();
    }

    static boolean exists(File file) {
        return unwritten.containsKey(path(file)) || file.exists();
    }

    static YamlConfiguration read(File file) {
        Queued queued = unwritten.get(path(file));
        if (queued == null) return YamlConfiguration.loadConfiguration(file);
        String pending = queued.content;
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(pending);
            return config;
        } catch (InvalidConfigurationException e) {
            return YamlConfiguration.loadConfiguration(file);
        }
    }

    static void write(File file, String content) {
        file.getParentFile().mkdirs();
        Path path = path(file);
        Queued queued = new Queued(content);
        unwritten.put(path, queued);
        SneakyCharacterManager.getInstance().writeFile(path, content)
                .whenComplete((ignored, failure) -> unwritten.remove(path, queued));
    }

    /** A new character's state: world spawn and an empty inventory. */
    static YamlConfiguration fresh(int inventorySize) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("location", Bukkit.getWorlds().get(0).getSpawnLocation());
        config.set("inventory", emptyInventory(inventorySize));
        return config;
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

    private static Path path(File file) {
        return file.toPath().toAbsolutePath();
    }

    private static File ownerDirectory(CharacterKey key) {
        return new File(SneakyCharacterManager.getCharacterDataFolder(), key.owner().toString());
    }
}
