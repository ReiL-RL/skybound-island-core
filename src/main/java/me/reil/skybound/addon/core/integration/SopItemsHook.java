package me.reil.skybound.addon.core.integration;

import me.reil.skybound.addon.core.IslandCorePlugin;
import org.bukkit.inventory.ItemStack;

/**
 * Мост к SopItemsCreator для получения и проверки кастомных предметов.
 * Если SopItemsCreator недоступен, методы возвращают null/false.
 */
public class SopItemsHook {

    private final IslandCorePlugin plugin;
    private boolean available;

    public SopItemsHook(IslandCorePlugin plugin) {
        this.plugin = plugin;
        this.available = plugin.getServer().getPluginManager().getPlugin("SopItemsCreator") != null;

        if (available) {
            plugin.getLogger().info("SopItemsCreator integration enabled.");
        } else {
            plugin.getLogger().info("SopItemsCreator not found, sop: items will be skipped.");
        }
    }

    /**
     * Проверяет, доступен ли SopItemsCreator.
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Получает кастомный предмет по ID.
     * @param itemId ID предмета в SopItemsCreator
     * @return ItemStack или null если не найден
     */
    public ItemStack getCustomItem(String itemId) {
        if (!available || itemId == null) return null;

        try {
            Class<?> apiClass = Class.forName("net.enelson.sopli.items.SopItemsAPI");
            java.lang.reflect.Method getMethod = apiClass.getMethod("getItem", String.class);
            Object result = getMethod.invoke(null, itemId);
            if (result instanceof ItemStack) {
                return (ItemStack) result;
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Failed to get SopItem '" + itemId + "': " + e.getMessage());
        }
        return null;
    }

    /**
     * Проверяет, является ли предмет кастомным с указанным ID.
     * @param item предмет для проверки
     * @param itemId ожидаемый ID
     * @return true если предмет соответствует
     */
    public boolean isCustomItem(ItemStack item, String itemId) {
        if (!available || item == null || itemId == null) return false;

        try {
            Class<?> apiClass = Class.forName("net.enelson.sopli.items.SopItemsAPI");
            java.lang.reflect.Method checkMethod = apiClass.getMethod("getItemId", ItemStack.class);
            Object result = checkMethod.invoke(null, item);
            return itemId.equals(result);
        } catch (Exception e) {
            plugin.getLogger().fine("Failed to check SopItem '" + itemId + "': " + e.getMessage());
        }
        return false;
    }
}
