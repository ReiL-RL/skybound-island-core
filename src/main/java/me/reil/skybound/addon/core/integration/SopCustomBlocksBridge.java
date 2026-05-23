package me.reil.skybound.addon.core.integration;

import me.reil.skybound.addon.core.IslandCorePlugin;
import net.enelson.sopli.customblocks.SopCustomBlocksAPI;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

/**
 * Мост к SopCustomBlocks для размещения/проверки кастомных блоков.
 * Если SopCustomBlocks недоступен, используется fallback-блок.
 */
public class SopCustomBlocksBridge {

    private final IslandCorePlugin plugin;

    public SopCustomBlocksBridge(IslandCorePlugin plugin) {
        this.plugin = plugin;
        plugin.getLogger().info("SopCustomBlocks API integration enabled.");
    }

    /**
     * Проверяет, доступен ли SopCustomBlocks.
     */
    public boolean isAvailable() {
        return SopCustomBlocksAPI.isAvailable();
    }

    /**
     * Размещает кастомный блок по ID.
     */
    public void placeCustomBlock(String blockId, Location location) {
        if (!isAvailable()) return;
        SopCustomBlocksAPI.placeBlock(blockId, location);
    }

    /**
     * Удаляет кастомный блок.
     */
    public void removeCustomBlock(Location location) {
        if (!isAvailable()) return;
        SopCustomBlocksAPI.removeBlock(location);
    }

    /**
     * Проверяет, является ли блок кастомным с указанным ID.
     */
    public boolean isCustomBlock(Location location, String blockId) {
        if (!isAvailable()) return false;
        String actualBlockId = SopCustomBlocksAPI.getBlockId(location);
        return blockId != null && blockId.equals(actualBlockId);
    }

    /**
     * Получает ItemStack кастомного блока по ID (для выдачи игроку).
     */
    public ItemStack getBlockItem(String blockId) {
        if (!isAvailable()) return null;
        return SopCustomBlocksAPI.getBlockItem(blockId);
    }
}
