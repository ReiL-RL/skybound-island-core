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
    private Object customBlocksService;

    public SopCustomBlocksBridge(IslandCorePlugin plugin) {
        this.plugin = plugin;
        this.available = plugin.getServer().getPluginManager().getPlugin("SopCustomBlocks") != null;

        if (available) {
            try {
                // Attempt to get the SopCustomBlocks plugin
                Plugin customBlocksPlugin = plugin.getServer().getPluginManager().getPlugin("SopCustomBlocks");
                if (customBlocksPlugin != null) {
                    // Get the API service
                    java.lang.reflect.Method getApi = customBlocksPlugin.getClass().getMethod("getApi");
                    customBlocksService = getApi.invoke(customBlocksPlugin);
                    plugin.getLogger().info("SopCustomBlocks API integration enabled.");
                } else {
                    available = false;
                }
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
        if (!available || customBlocksService == null) return;

        try {
            // Use the new API: placeBlock(id, location)
            java.lang.reflect.Method placeBlock = customBlocksService.getClass().getMethod("placeBlock", String.class, Location.class);
            placeBlock.invoke(customBlocksService, blockId, location);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to place custom block '" + blockId + "': " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Удаляет кастомный блок.
     */
    public void removeCustomBlock(Location location) {
        if (!available || customBlocksService == null) return;

        try {
            java.lang.reflect.Method removeMethod = customBlocksService.getClass().getMethod("removeBlock", Location.class);
            removeMethod.invoke(customBlocksService, location);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove custom block: " + e.getMessage());
        }
    }

    /**
     * Проверяет, является ли блок кастомным с указанным ID.
     */
    public boolean isCustomBlock(Location location, String blockId) {
        if (!available || customBlocksService == null) return false;

        try {
            java.lang.reflect.Method getMethod = customBlocksService.getClass().getMethod("getBlockId", Location.class);
            Object result = getMethod.invoke(customBlocksService, location);
            return blockId.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Получает ItemStack кастомного блока по ID (для выдачи игроку).
     */
    public ItemStack getBlockItem(String blockId) {
        if (!available || customBlocksService == null) return null;

        try {
            java.lang.reflect.Method getItemMethod = customBlocksService.getClass().getMethod("getBlockItem", String.class);
            Object result = getItemMethod.invoke(customBlocksService, blockId);
            return result instanceof ItemStack ? (ItemStack) result : null;
        } catch (Exception e) {
            plugin.getLogger().fine("Failed to get block item for '" + blockId + "': " + e.getMessage());
            return null;
        }
    }
}
