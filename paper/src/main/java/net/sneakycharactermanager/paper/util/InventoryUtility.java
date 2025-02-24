package net.sneakycharactermanager.paper.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

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

        try {
            //Save this string to file.
            return inventoryToBase64(inventory);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Get the ItemStack[] from the B64 String
     * @param data B64 string to decode into an ItemStack[]
     * @return Itemstack[] containing all saved items
     * */
    public static ItemStack[] getSavedInventory(String data) {

        try {
            return inventoryFromBase64(data);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new ItemStack[0];
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
                dataOutput.writeObject(inventory.getItem(i));
            }

            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());

            //Converts the inventory and its contents to base64, This also saves item meta-data and inventory type
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
                dataOutput.writeObject(items[i]);
            }

            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());

            //Converts the inventory and its contents to base64, This also saves item meta-data and inventory type
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
