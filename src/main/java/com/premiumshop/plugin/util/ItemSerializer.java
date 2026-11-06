package com.premiumshop.plugin.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High-performance, cross-version item serialization utility for Paper/Purpur 1.21.x through 26.3+.
 *
 * <p>Uses Paper's native component-aware byte serialization ({@link ItemStack#serializeAsBytes()})
 * encoded into standard Base64. Includes fallback deserializers for legacy BukkitObjectStreams
 * and YAML representations.</p>
 */
public final class ItemSerializer {

    private static final Logger LOGGER = Logger.getLogger("PremiumShop-Serializer");

    private ItemSerializer() {
        // Utility class
    }

    /**
     * Serializes a single {@link ItemStack} into a Base64 string using Paper's native byte format.
     *
     * @param item the item to serialize; may be null or air
     * @return Base64 encoded string, or null if input was null/air
     */
    public static String serialize(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }

        try {
            byte[] bytes = item.serializeAsBytes();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            // Fallback to BukkitObjectOutputStream if needed
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                 BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
                dataOutput.writeObject(item);
                dataOutput.flush();
                return Base64.getEncoder().encodeToString(outputStream.toByteArray());
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Failed to serialize ItemStack to Base64", ex);
                return null;
            }
        }
    }

    /**
     * Deserializes an {@link ItemStack} from a Base64 string.
     *
     * @param data Base64 encoded item string
     * @return restored ItemStack, or null if deserialization fails or data is empty
     */
    public static ItemStack deserialize(String data) {
        if (data == null || data.trim().isEmpty()) {
            return null;
        }

        String cleaned = data.trim();
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(cleaned);
        } catch (IllegalArgumentException e) {
            // Maybe it's raw YAML
            return deserializeYaml(cleaned);
        }

        // 1. Try modern Paper byte deserialization
        try {
            return ItemStack.deserializeBytes(bytes);
        } catch (Throwable ignored) {
            // Fall through to legacy deserializers
        }

        // 2. Try BukkitObjectInputStream
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            Object obj = dataInput.readObject();
            if (obj instanceof ItemStack stack) {
                return stack;
            }
        } catch (Exception ignored) {
            // Fall through to YAML attempt
        }

        // 3. Try YAML string decoded from bytes
        return deserializeYaml(new String(bytes, StandardCharsets.UTF_8));
    }

    private static ItemStack deserializeYaml(String yaml) {
        try {
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.loadFromString(yaml);
            return cfg.getItemStack("item");
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to deserialize item from YAML fallback", e);
            return null;
        }
    }

    /**
     * Serializes an array of {@link ItemStack}s to a Base64 string.
     *
     * @param items array of items to serialize
     * @return Base64 encoded string
     */
    public static String serializeInventory(ItemStack[] items) {
        if (items == null || items.length == 0) {
            return "";
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                if (item != null && !item.getType().isAir()) {
                    byte[] bytes = item.serializeAsBytes();
                    dataOutput.writeInt(bytes.length);
                    dataOutput.write(bytes);
                } else {
                    dataOutput.writeInt(0);
                }
            }
            dataOutput.flush();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to serialize ItemStack array to Base64", e);
            return "";
        }
    }

    /**
     * Deserializes an array of {@link ItemStack}s from a Base64 string.
     *
     * @param data Base64 encoded inventory string
     * @return restored array of ItemStacks
     */
    public static ItemStack[] deserializeInventory(String data) {
        if (data == null || data.trim().isEmpty()) {
            return new ItemStack[0];
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data.trim()));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

            int size = dataInput.readInt();
            ItemStack[] items = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                int len = dataInput.readInt();
                if (len > 0) {
                    byte[] itemBytes = new byte[len];
                    dataInput.readFully(itemBytes);
                    items[i] = ItemStack.deserializeBytes(itemBytes);
                } else {
                    items[i] = null;
                }
            }
            return items;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to deserialize ItemStack array from Base64 string", e);
            return new ItemStack[0];
        }
    }
}
