package net.sneakycharactermanager.paper.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import net.sneakycharactermanager.paper.SneakyCharacterManager;

public class InventoryUtility {

    // SYSTEM UPDATED: All main serialization methods now use Paper's native serialization.
    // Old base64 methods (inventoryFromBase64) are kept ONLY for migration purposes.
    // New saves automatically use Paper YAML format, old saves are auto-detected and supported.

    /**
     * Convert an inventory into a YAML String using Paper's native serialization.
     * (Note: This will only convert the inventory contents, not types or names)
     * @param inventory Inventory to convert
     * @return YAML encoded inventory string using Paper serialization
     * */
    public static String convertInventory(Inventory inventory) {
        try {
            // Use Paper's native serialization for all new saves
            return inventoryToPaperYaml(inventory);
        } catch (Exception e) {
            SneakyCharacterManager.getInstance().getLogger().severe("Failed to serialize inventory using Paper format: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Get the ItemStack[] from inventory data (supports both new YAML and old base64 formats)
     * @param data Inventory data string to decode into an ItemStack[]
     * @return ItemStack[] containing all saved items
     * */
    public static ItemStack[] getSavedInventory(String data) {
        try {
             // Check if it's the new YAML format
             if (data.contains("format: paper-native") || data.contains("format: paper-native-collection") || (data.contains("size:") && data.contains("items:"))) {
                SneakyCharacterManager.getInstance().getLogger().info("Loading inventory using Paper YAML format");
                return inventoryFromPaperYaml(data);
            } else {
                // Assume it's old base64 format (for backwards compatibility during migration)
                SneakyCharacterManager.getInstance().getLogger().info("Loading inventory using legacy base64 format");
                return inventoryFromBase64(data);
            }
        } catch (Exception e) {
            SneakyCharacterManager.getInstance().getLogger().warning("Failed to load inventory data: " + e.getMessage());
            e.printStackTrace();
            return new ItemStack[0];
        }
    }

    /**
     * Convert an inventory into a YAML String using Paper's native serialization.
     * @param inventory Inventory to convert
     * @return YAML encoded inventory string using Paper serialization
     * */
    public static String inventoryToBase64(Inventory inventory) {
        try {
            // Use Paper's native serialization instead of deprecated base64 method
            return inventoryToPaperYaml(inventory);
        } catch (Exception e) {
            throw new IllegalStateException("Could not convert inventory to YAML using Paper serialization.", e);
        }
    }

    /**
     * Convert an ItemStack array into a YAML String using Paper's native serialization.
     * @param size Size of the inventory (unused, kept for compatibility)
     * @param items Item Contents
     * @return YAML encoded inventory string using Paper serialization
     * */
    public static String inventoryToBase64(int size, ItemStack[] items) {
        try {
            // Use Paper's native serialization instead of deprecated base64 method
            return inventoryToPaperYaml(items);
        } catch (Exception e) {
            throw new IllegalStateException("Could not convert inventory to YAML using Paper serialization.", e);
        }
    }

    /**
     * Get the Inventory from the B64 String (Will have a generic name)
     * Corrupted items are skipped and left as null (empty slots)
     * @param data B64 string to decode into an ItemStack[]
     * @return Inventory created from the encoded data
     * */
    public static ItemStack[] inventoryFromBase64(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            int size = dataInput.readInt();
            ItemStack[] items = new ItemStack[size];

            int successfulItems = 0;
            for (int i = 0; i < size; i++) {
                try {
                    items[i] = (ItemStack) dataInput.readObject();
                    if (items[i] != null) {
                        successfulItems++;
                    }
                } catch (Exception e) {
                    // Item failed to deserialize - leave slot empty (null)
                    items[i] = null;
                    SneakyCharacterManager.getInstance().getLogger().info("Skipped corrupted item at slot " + i + " during base64 deserialization: " + e.getMessage());
                }
            }

            dataInput.close();
            SneakyCharacterManager.getInstance().getLogger().info("Base64 deserialization completed: " + successfulItems + "/" + size + " items recovered");
            return items;
        } catch (IOException e) {
            SneakyCharacterManager.getInstance().getLogger().warning("Failed to deserialize base64 inventory data: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Convert an ItemStack array into a YAML string using Paper's native collection serialization
     * @param items ItemStack array to convert
     * @return YAML string containing the inventory data
     */
    public static String inventoryToPaperYaml(ItemStack[] items) {
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.set("size", items.length);
            config.set("format", "paper-native-collection");
            
            // Use Paper's collection serialization - much more efficient!
            byte[] serializedItems = ItemStack.serializeItemsAsBytes(java.util.Arrays.asList(items));
            config.set("items", java.util.Base64.getEncoder().encodeToString(serializedItems));
            
            return config.saveToString();
        } catch (Exception e) {
            SneakyCharacterManager.getInstance().getLogger().severe("Failed to serialize inventory using Paper collection serialization: " + e.getMessage());
            throw new IllegalStateException("Could not serialize inventory", e);
        }
    }

    /**
     * Convert an inventory into a YAML string using Paper's native serialization
     * @param inventory Inventory to convert
     * @return YAML string containing the inventory data
     */
    public static String inventoryToPaperYaml(Inventory inventory) {
        return inventoryToPaperYaml(inventory.getContents());
    }

    /**
     * Get ItemStack array from Paper YAML string
     * @param yamlString YAML string to deserialize
     * @return ItemStack array or empty array if deserialization fails
     */
    public static ItemStack[] inventoryFromPaperYaml(String yamlString) {
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString(yamlString);
            
            int size = config.getInt("size", 36);
            String format = config.getString("format", "unknown");
            
            if ("paper-native-collection".equals(format)) {
                // Use Paper's collection deserialization - much more efficient!
                String base64Data = config.getString("items");
                if (base64Data != null) {
                    byte[] serializedItems = java.util.Base64.getDecoder().decode(base64Data);
                    return ItemStack.deserializeItemsFromBytes(serializedItems);
                }
            } else {
                // Handle legacy individual item format (from older versions of our Paper serialization)
                ItemStack[] items = new ItemStack[size];
                if (config.isConfigurationSection("items")) {
                    for (String key : config.getConfigurationSection("items").getKeys(false)) {
                        try {
                            int slot = Integer.parseInt(key);
                            if (slot >= 0 && slot < size) {
                                String base64Data = config.getString("items." + key);
                                if (base64Data != null) {
                                    byte[] serializedItem = java.util.Base64.getDecoder().decode(base64Data);
                                    items[slot] = ItemStack.deserializeBytes(serializedItem);
                                }
                            }
                        } catch (Exception e) {
                            SneakyCharacterManager.getInstance().getLogger().warning("Failed to deserialize item at slot " + key + ": " + e.getMessage());
                        }
                    }
                }
                return items;
            }
            
            return new ItemStack[size]; // Return empty array if no items found
        } catch (Exception e) {
            SneakyCharacterManager.getInstance().getLogger().warning("Failed to deserialize Paper YAML inventory data: " + e.getMessage());
            return new ItemStack[0];
        }
    }

    /**
     * Migrates old base64 inventory data to Paper's native YAML serialization
     * Items that fail to convert are simply removed (clean start approach)
     * @param base64Data The old base64 encoded inventory data
     * @return YAML string using Paper serialization, or null if migration fails completely
     */
    public static String migrateBase64ToPaperYaml(String base64Data) {
        try {
            SneakyCharacterManager.getInstance().getLogger().info("Migrating base64 inventory to Paper YAML format...");
            
            // Step 1: Try to deserialize the old base64 data
            ItemStack[] oldItems = inventoryFromBase64(base64Data);
            if (oldItems == null) {
                SneakyCharacterManager.getInstance().getLogger().warning("Failed to deserialize old base64 data");
                return null;
            }
            
             // Step 2: Convert to Paper's native collection serialization format
             try {
                 // Use the new collection-based serialization for efficiency
                 return inventoryToPaperYaml(oldItems);
             } catch (Exception e) {
                 SneakyCharacterManager.getInstance().getLogger().warning("Collection serialization failed, trying item-by-item fallback: " + e.getMessage());
                 
                 // Fallback: item-by-item conversion with error handling
                 YamlConfiguration config = new YamlConfiguration();
                 config.set("size", oldItems.length);
                 config.set("format", "paper-native");
                 
                 int convertedItems = 0;
                 for (int i = 0; i < oldItems.length; i++) {
                     if (oldItems[i] != null) {
                         try {
                             // Use Paper's native serialization
                             byte[] serializedItem = oldItems[i].serializeAsBytes();
                             String base64Item = java.util.Base64.getEncoder().encodeToString(serializedItem);
                             config.set("items." + i, base64Item);
                             convertedItems++;
                         } catch (Exception itemException) {
                             // Item failed to convert - skip it (clean start approach)
                             SneakyCharacterManager.getInstance().getLogger().info("Skipped item at slot " + i + " (conversion failed): " + itemException.getMessage());
                         }
                     }
                 }
                 
                 String yamlResult = config.saveToString();
                 SneakyCharacterManager.getInstance().getLogger().info("Fallback migration successful: " + convertedItems + "/" + oldItems.length + " items converted");
                 return yamlResult;
             }
            
        } catch (Exception e) {
            SneakyCharacterManager.getInstance().getLogger().warning("Migration failed: " + e.getMessage());
            return null;
        }
    }

}
