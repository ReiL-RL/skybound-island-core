package me.reil.skybound.addon.core.integration;

import me.reil.skybound.addon.core.IslandCorePlugin;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.ItemStack;

/**
 * Мост к SopCustomBlocks для размещения/проверки кастомных блоков.
 * Если SopCustomBlocks недоступен, используется fallback-блок.
 */
public class SopCustomBlocksBridge {

    private final IslandCorePlugin plugin;
    private boolean available;
    private Object customBlocksApi;

    public SopCustomBlocksBridge(IslandCorePlugin plugin) {
        this.plugin = plugin;
        this.available = plugin.getServer().getPluginManager().getPlugin("SopCustomBlocks") != null;

        if (available) {
            try {
                // Attempt to get the SopCustomBlocks API
                customBlocksApi = plugin.getServer().getPluginManager().getPlugin("SopCustomBlocks");
                plugin.getLogger().info("SopCustomBlocks integration enabled.");
            } catch (Exception e) {
                available = false;
                plugin.getLogger().warning("SopCustomBlocks found but failed to hook: " + e.getMessage());
            }
        } else {
            plugin.getLogger().info("SopCustomBlocks not found, using fallback blocks.");
        }
    }

    /**
     * Проверяет, доступен ли SopCustomBlocks.
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Размещает кастомный блок по ID.
     */
    public void placeCustomBlock(String blockId, Location location) {
        if (!available) return;

        try {
            // Get SopCustomBlocks plugin instance
            Plugin customBlocksPlugin = plugin.getServer().getPluginManager().getPlugin("SopCustomBlocks");
            if (customBlocksPlugin == null) return;
            
            // Get blockManager
            java.lang.reflect.Method getBlockManager = customBlocksPlugin.getClass().getMethod("getBlockManager");
            Object blockManager = getBlockManager.invoke(customBlocksPlugin);
            
            // Call addBlock(id, location, yaw, pitch) - no player needed
            java.lang.reflect.Method addBlock = blockManager.getClass().getMethod("addBlock", String.class, Location.class, float.class, float.class);
            addBlock.invoke(blockManager, blockId, location, 0.0f, 0.0f);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to place custom block '" + blockId + "': " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Удаляет кастомный блок.
     */
    public void removeCustomBlock(Location location) {
        if (!available) return;

        try {
            Class<?> apiClass = Class.forName("net.enelson.sopli.customblocks.SopCustomBlocksAPI");
            java.lang.reflect.Method removeMethod = apiClass.getMethod("removeBlock", Location.class);
            removeMethod.invoke(null, location);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove custom block: " + e.getMessage());
        }
    }

    /**
     * Проверяет, является ли блок кастомным с указанным ID.
     */
    public boolean isCustomBlock(Location location, String blockId) {
        if (!available) return false;

        try {
            Class<?> apiClass = Class.forName("net.enelson.sopli.customblocks.SopCustomBlocksAPI");
            java.lang.reflect.Method getMethod = apiClass.getMethod("getBlockId", Location.class);
            Object result = getMethod.invoke(null, location);
            return blockId.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Получает ItemStack кастомного блока по ID (для выдачи игроку).
     */
    public ItemStack getBlockItem(String blockId) {
        if (!available) return null;

        try {
            Class<?> apiClass = Class.forName("net.enelson.sopli.customblocks.SopCustomBlocksAPI");
            java.lang.reflect.Method getItemMethod = apiClass.getMethod("getBlockItem", String.class);
            Object result = getItemMethod.invoke(null, blockId);
            return result instanceof ItemStack ? (ItemStack) result : null;
        } catch (Exception e) {
            plugin.getLogger().fine("Failed to get block item for '" + blockId + "': " + e.getMessage());
            return null;
        }
    }
}
