package net.sneakycharactermanager.paper.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

public class InventoryUtility {

    /**
     * Convert an inventory into a B64 String.
     * (Note: This will only convert the inventory contents, not types or names)
     * @param inventory Inventory to convert
     * @return B64 Encoded inventory string
     * */
    public static String convertInventory(Inventory inventory) {
        return inventoryToBase64(inventory);
    }

    /**
     * Get the ItemStack[] from the B64 String
     * @param data B64 string to decode into an ItemStack[]
     * @return Itemstack[] containing all saved items
     * */
    public static ItemStack[] getSavedInventory(String data) {
        if (data == null) return new ItemStack[0];
        
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            
            int size = dataInput.readInt();
            ItemStack[] items = new ItemStack[size];
            
            // Read the serialized inventory
            for (int i = 0; i < size; i++) {
                try {
                    Object obj = dataInput.readObject();
                    if (obj == null) {
                        items[i] = new ItemStack(Material.AIR);
                        continue;
                    }
                    
                    if (obj instanceof ItemStack) {
                        items[i] = (ItemStack) obj;
                    } else if (obj instanceof byte[]) {
                        try {
                            items[i] = ItemStack.deserializeBytes((byte[]) obj);
                        } catch (Exception e) {
                            // If deserialization fails, try to create a basic item
                            items[i] = new ItemStack(Material.AIR);
                        }
                    } else {
                        items[i] = new ItemStack(Material.AIR);
                    }
                } catch (Exception e) {
                    items[i] = new ItemStack(Material.AIR);
                }
            }
            
            dataInput.close();
            return items;
        } catch (Exception e) {
            e.printStackTrace();
            return new ItemStack[0];
        }
    }

    /**
     * Convert an inventory into a B64 String.
     * (Note: This will only convert the inventory contents, not types or names)
     * @param inventory Inventory to convert
     * @return B64 Encoded inventory string
     * */
    public static String inventoryToBase64(Inventory inventory) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            dataOutput.writeInt(inventory.getSize());

            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack item = inventory.getItem(i);
                try {
                    if (item != null && !item.getType().isAir()) {
                        dataOutput.writeObject(item);
                    } else {
                        dataOutput.writeObject(null);
                    }
                } catch (Exception e) {
                    dataOutput.writeObject(null);
                }
            }

            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Could not convert inventory to base64.", e);
        }
    }

    /**
     * Convert an inventory into a B64 String.
     * (Note: This will only convert the inventory contents, not types or names)
     * @param size Size of the inventory
     * @param items Item Contents
     * @return B64 Encoded inventory string
     * */
    public static String inventoryToBase64(int size, ItemStack[] items) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            dataOutput.writeInt(size);

            for (int i = 0; i < size; i++) {
                try {
                    if (items[i] != null && !items[i].getType().isAir()) {
                        dataOutput.writeObject(items[i]);
                    } else {
                        dataOutput.writeObject(null);
                    }
                } catch (Exception e) {
                    dataOutput.writeObject(null);
                }
            }

            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Could not convert inventory to base64.", e);
        }
    }

    /**
     * Get the Inventory from the B64 String (Will have a generic name)
     * @param data B64 string to decode into an ItemStack[]
     * @return Inventory created from the encoded data
     * */
    public static ItemStack[] inventoryFromBase64(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            int size = dataInput.readInt();
            ItemStack[] items = new ItemStack[size];

            for (int i = 0; i < size; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }

            dataInput.close();
            return items;
        } catch (ClassNotFoundException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
