package me.reil.skybound.addon.core;

import me.reil.skybound.api.SkyBoundAPI;
import me.reil.skybound.api.island.Island;
import me.reil.skybound.api.upgrade.UpgradeProvider;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * GUI для ядра улучшений: выбор апгрейда → депозит ресурсов → применение.
 */
public class UpgradeCoreGui {

    private final IslandCorePlugin plugin;

    private static final String SELECTION_TITLE = "§8✦ Ядро Улучшений";
    private static final String DEPOSIT_TITLE_PREFIX = "§8✦ Улучшение: ";

    private static final int CONFIRM_SLOT = 22;
    private static final int CANCEL_SLOT = 26;
    private static final int DEPOSIT_START = 9;
    private static final int DEPOSIT_END = 17;

    // Track which players have the selection GUI open
    private final Map<UUID, CoreBlock> selectionGuis = new HashMap<>();
    // Track which players have the deposit GUI open: player → upgrade definition
    private final Map<UUID, UpgradeDefinition> depositGuis = new HashMap<>();
    // Track core blocks for deposit GUI
    private final Map<UUID, CoreBlock> depositCoreBlocks = new HashMap<>();

    public UpgradeCoreGui(IslandCorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Открывает GUI выбора апгрейда.
     */
    public void openSelection(Player player, CoreBlock coreBlock) {
        List<UpgradeDefinition> upgrades = plugin.getCoreConfig().getUpgradeDefinitions();
        if (upgrades.isEmpty()) {
            player.sendMessage(ChatColor.RED + "✦ Нет доступных улучшений!");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, SELECTION_TITLE);

        // Fill background with glass
        ItemStack filler = createFiller(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        // Get island for current levels
        Island island = getPlayerIsland(player);

        // Place upgrade icons
        for (UpgradeDefinition def : upgrades) {
            int currentLevel = 0;
            boolean maxed = false;

            if (island != null) {
                try {
                    UpgradeProvider provider = SkyBoundAPI.get().getUpgradeProvider();
                    currentLevel = provider.getLevel(island, def.getSkyboundUpgradeId());
                    maxed = currentLevel >= def.getMaxLevel();
                } catch (Exception e) {
                    plugin.getLogger().fine("Could not get upgrade level: " + e.getMessage());
                }
            }

            ItemStack icon = new ItemStack(def.getIcon());
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', def.getDisplayName()));

                List<String> lore = new ArrayList<>();
                // Description lines
                for (String line : def.getDescription()) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                lore.add("");
                lore.add(ChatColor.GRAY + "Уровень: " + ChatColor.WHITE + currentLevel + "/" + def.getMaxLevel());

                if (maxed) {
                    lore.add("");
                    lore.add(ChatColor.GREEN + "✔ Максимальный уровень!");
                } else {
                    int nextLevel = currentLevel + 1;
                    Map<Material, Integer> cost = getCostForLevel(def, nextLevel);
                    lore.add("");
                    lore.add(ChatColor.YELLOW + "Стоимость уровня " + nextLevel + ":");
                    for (Map.Entry<Material, Integer> entry : cost.entrySet()) {
                        lore.add(ChatColor.GRAY + "  • " + formatMaterial(entry.getKey()) + " x" + entry.getValue());
                    }
                    lore.add("");
                    lore.add(ChatColor.GREEN + "▶ Нажми для улучшения");
                }

                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            inv.setItem(def.getSlot(), icon);
        }

        player.openInventory(inv);
        selectionGuis.put(player.getUniqueId(), coreBlock);
        // Clean up any deposit state
        depositGuis.remove(player.getUniqueId());
        depositCoreBlocks.remove(player.getUniqueId());
    }

    /**
     * Открывает GUI депозита для конкретного апгрейда.
     */
    public void openDeposit(Player player, CoreBlock coreBlock, UpgradeDefinition def) {
        Island island = getPlayerIsland(player);
        int currentLevel = 0;
        if (island != null) {
            try {
                currentLevel = SkyBoundAPI.get().getUpgradeProvider().getLevel(island, def.getSkyboundUpgradeId());
            } catch (Exception e) {
                plugin.getLogger().fine("Could not get upgrade level: " + e.getMessage());
            }
        }

        if (currentLevel >= def.getMaxLevel()) {
            player.sendMessage(ChatColor.GREEN + "✦ Это улучшение уже на максимальном уровне!");
            return;
        }

        int targetLevel = currentLevel + 1;
        Map<Material, Integer> cost = getCostForLevel(def, targetLevel);

        String title = DEPOSIT_TITLE_PREFIX + ChatColor.translateAlternateColorCodes('&', def.getDisplayName());
        // Truncate title if too long (Bukkit limit is 32 chars)
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }

        Inventory inv = Bukkit.createInventory(null, 27, title);

        // Top row: show required items
        int slot = 0;
        for (Map.Entry<Material, Integer> entry : cost.entrySet()) {
            if (slot > 8) break;
            ItemStack reqItem = new ItemStack(entry.getKey(), entry.getValue());
            ItemMeta meta = reqItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + formatMaterial(entry.getKey()));
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Нужно: " + ChatColor.WHITE + entry.getValue() + " шт.");
                lore.add("");
                lore.add(ChatColor.DARK_GRAY + "Положи предметы в слоты ниже");
                meta.setLore(lore);
                reqItem.setItemMeta(meta);
            }
            inv.setItem(slot, reqItem);
            slot++;
        }

        // Fill remaining top slots with gray glass
        while (slot <= 8) {
            inv.setItem(slot, createFiller(Material.GRAY_STAINED_GLASS_PANE));
            slot++;
        }

        // Middle row (9-17): deposit slots — left empty

        // Bottom row decoration
        for (int i = 18; i < 27; i++) {
            if (i == CONFIRM_SLOT || i == CANCEL_SLOT) continue;
            inv.setItem(i, createFiller(Material.BLACK_STAINED_GLASS_PANE));
        }

        // Confirm button
        ItemStack confirm = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta confirmMeta = confirm.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.setDisplayName(ChatColor.GREEN + "✔ Подтвердить");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Улучшение до уровня " + targetLevel);
            confirmMeta.setLore(lore);
            confirm.setItemMeta(confirmMeta);
        }
        inv.setItem(CONFIRM_SLOT, confirm);

        // Cancel button
        ItemStack cancel = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta cancelMeta = cancel.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName(ChatColor.RED + "✘ Назад");
            cancel.setItemMeta(cancelMeta);
        }
        inv.setItem(CANCEL_SLOT, cancel);

        player.openInventory(inv);
        selectionGuis.remove(player.getUniqueId());
        depositGuis.put(player.getUniqueId(), def);
        depositCoreBlocks.put(player.getUniqueId(), coreBlock);
    }

    /**
     * Обрабатывает подтверждение депозита — проверяет предметы и применяет апгрейд.
     */
    public void handleConfirm(Player player, Inventory inventory) {
        UUID playerId = player.getUniqueId();
        UpgradeDefinition def = depositGuis.get(playerId);
        CoreBlock coreBlock = depositCoreBlocks.get(playerId);
        if (def == null || coreBlock == null) return;

        Island island = getPlayerIsland(player);
        if (island == null) {
            player.sendMessage(ChatColor.RED + "✦ Ты не на острове!");
            returnDepositItems(player, inventory);
            closeDeposit(player);
            return;
        }

        int currentLevel;
        try {
            currentLevel = SkyBoundAPI.get().getUpgradeProvider().getLevel(island, def.getSkyboundUpgradeId());
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "✦ Ошибка при проверке уровня!");
            returnDepositItems(player, inventory);
            closeDeposit(player);
            return;
        }

        if (currentLevel >= def.getMaxLevel()) {
            player.sendMessage(ChatColor.GREEN + "✦ Уже максимальный уровень!");
            returnDepositItems(player, inventory);
            closeDeposit(player);
            return;
        }

        int targetLevel = currentLevel + 1;
        Map<Material, Integer> cost = getCostForLevel(def, targetLevel);

        // Count deposited items
        Map<Material, Integer> deposited = new HashMap<>();
        for (int i = DEPOSIT_START; i <= DEPOSIT_END; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            deposited.merge(item.getType(), item.getAmount(), Integer::sum);
        }

        // Check if requirements are met
        for (Map.Entry<Material, Integer> req : cost.entrySet()) {
            int have = deposited.getOrDefault(req.getKey(), 0);
            if (have < req.getValue()) {
                player.sendMessage(ChatColor.RED + "✦ Недостаточно ресурсов! Нужно " +
                        formatMaterial(req.getKey()) + " x" + req.getValue() +
                        " (есть: " + have + ")");
                return; // Don't close — let player add more items
            }
        }

        // Requirements met — consume exactly the required amounts
        Map<Material, Integer> toConsume = new HashMap<>(cost);
        for (int i = DEPOSIT_START; i <= DEPOSIT_END; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            Integer needed = toConsume.get(item.getType());
            if (needed == null || needed <= 0) {
                // Return this item — not needed
                continue;
            }

            if (item.getAmount() <= needed) {
                toConsume.put(item.getType(), needed - item.getAmount());
                inventory.setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - needed);
                toConsume.put(item.getType(), 0);
            }
        }

        // Return excess items
        returnDepositItems(player, inventory);

        // Apply upgrade via SkyBound API (use reflection to call forceUpgrade on implementation)
        try {
            Object upgradeProvider = SkyBoundAPI.get().getUpgradeProvider();
            java.lang.reflect.Method forceMethod = upgradeProvider.getClass().getMethod("forceUpgrade", me.reil.skybound.api.island.Island.class, String.class);
            forceMethod.setAccessible(true);
            boolean success = (boolean) forceMethod.invoke(upgradeProvider, island, def.getSkyboundUpgradeId());
            if (success) {
                String name = ChatColor.translateAlternateColorCodes('&', def.getDisplayName());
                player.sendMessage(ChatColor.GREEN + "✦ " + name + ChatColor.GREEN +
                        " улучшено до уровня " + targetLevel + "!");
            } else {
                player.sendMessage(ChatColor.RED + "✦ Не удалось применить улучшение!");
                // Refund consumed items
                for (Map.Entry<Material, Integer> entry : cost.entrySet()) {
                    int consumed = entry.getValue() - toConsume.getOrDefault(entry.getKey(), 0);
                    if (consumed > 0) {
                        giveItems(player, entry.getKey(), consumed);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to apply upgrade: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "✦ Ошибка при применении улучшения!");
            // Refund consumed items
            for (Map.Entry<Material, Integer> entry : cost.entrySet()) {
                int consumed = entry.getValue() - toConsume.getOrDefault(entry.getKey(), 0);
                if (consumed > 0) {
                    giveItems(player, entry.getKey(), consumed);
                }
            }
        }

        closeDeposit(player);
    }

    /**
     * Обрабатывает отмену в deposit GUI — возвращает предметы и открывает selection.
     */
    public void handleCancel(Player player, Inventory inventory) {
        CoreBlock coreBlock = depositCoreBlocks.get(player.getUniqueId());
        returnDepositItems(player, inventory);
        closeDeposit(player);
        // Re-open selection
        if (coreBlock != null) {
            openSelection(player, coreBlock);
        }
    }

    /**
     * Возвращает предметы из слотов депозита игроку.
     */
    public void returnDepositItems(Player player, Inventory inventory) {
        for (int i = DEPOSIT_START; i <= DEPOSIT_END; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
                for (ItemStack drop : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
                inventory.setItem(i, null);
            }
        }
    }

    // --- State checks ---

    public boolean hasSelectionOpen(UUID playerId) {
        return selectionGuis.containsKey(playerId);
    }

    public boolean hasDepositOpen(UUID playerId) {
        return depositGuis.containsKey(playerId);
    }

    public CoreBlock getSelectionCoreBlock(UUID playerId) {
        return selectionGuis.get(playerId);
    }

    public UpgradeDefinition getDepositUpgrade(UUID playerId) {
        return depositGuis.get(playerId);
    }

    public CoreBlock getDepositCoreBlock(UUID playerId) {
        return depositCoreBlocks.get(playerId);
    }

    public void onClose(UUID playerId) {
        selectionGuis.remove(playerId);
        depositGuis.remove(playerId);
        depositCoreBlocks.remove(playerId);
    }

    private void closeDeposit(Player player) {
        // Just clear state - don't close inventory
        depositGuis.remove(player.getUniqueId());
        depositCoreBlocks.remove(player.getUniqueId());
    }

    // --- Utility ---

    /**
     * Рассчитывает стоимость для конкретного уровня.
     */
    public Map<Material, Integer> getCostForLevel(UpgradeDefinition def, int targetLevel) {
        Map<Material, Integer> cost = new LinkedHashMap<>();
        for (Map.Entry<Material, Integer> entry : def.getCostPerLevel().entrySet()) {
            int amount = (int) Math.ceil(entry.getValue() * Math.pow(def.getCostMultiplier(), targetLevel - 1));
            cost.put(entry.getKey(), amount);
        }
        return cost;
    }

    private Island getPlayerIsland(Player player) {
        try {
            return SkyBoundAPI.get().getIslandProvider().getPlayerIsland(player.getUniqueId());
        } catch (Exception e) {
            return null;
        }
    }

    private void giveItems(Player player, Material material, int amount) {
        ItemStack stack = new ItemStack(material, amount);
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        for (ItemStack drop : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    private ItemStack createFiller(Material material) {
        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }
        return filler;
    }

    private String formatMaterial(Material material) {
        switch (material) {
            case IRON_INGOT: return "Железо";
            case GOLD_INGOT: return "Золото";
            case DIAMOND: return "Алмаз";
            case EMERALD: return "Изумруд";
            case ENDER_PEARL: return "Эндер-жемчуг";
            case NETHERITE_INGOT: return "Незерит";
            default:
                String name = material.name().toLowerCase().replace('_', ' ');
                return name.substring(0, 1).toUpperCase() + name.substring(1);
        }
    }

    public int getConfirmSlot() {
        return CONFIRM_SLOT;
    }

    public int getCancelSlot() {
        return CANCEL_SLOT;
    }

    public int getDepositStart() {
        return DEPOSIT_START;
    }

    public int getDepositEnd() {
        return DEPOSIT_END;
    }
}
