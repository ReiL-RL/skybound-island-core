package me.reil.skybound.addon.core;

import me.reil.skybound.api.SkyBoundAPI;
import me.reil.skybound.api.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * GUI для ядра бустера: выбор бустера → депозит ресурсов → активация.
 */
public class BoosterCoreGui {

    private final IslandCorePlugin plugin;

    private static final String SELECTION_TITLE = "§8✦ Ядро Бустера";
    private static final String DEPOSIT_TITLE_PREFIX = "§8✦ Бустер: ";

    private static final int CONFIRM_SLOT = 22;
    private static final int CANCEL_SLOT = 26;
    private static final int DEPOSIT_START = 9;
    private static final int DEPOSIT_END = 17;

    // Track which players have the selection GUI open
    private final Map<UUID, CoreBlock> selectionGuis = new HashMap<>();
    // Track which players have the deposit GUI open: player → booster definition
    private final Map<UUID, BoosterDefinition> depositGuis = new HashMap<>();
    // Track core blocks for deposit GUI
    private final Map<UUID, CoreBlock> depositCoreBlocks = new HashMap<>();

    public BoosterCoreGui(IslandCorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Открывает GUI выбора бустера.
     */
    public void openSelection(Player player, CoreBlock coreBlock) {
        List<BoosterDefinition> boosters = plugin.getCoreConfig().getBoosterDefinitions();
        if (boosters.isEmpty()) {
            player.sendMessage(ChatColor.RED + "✦ Нет доступных бустеров!");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, SELECTION_TITLE);

        // Fill background with glass
        ItemStack filler = createFiller(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        // Place booster icons
        for (BoosterDefinition def : boosters) {
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
                lore.add(ChatColor.YELLOW + "Стоимость:");
                for (Map.Entry<Material, Integer> entry : def.getCost().entrySet()) {
                    lore.add(ChatColor.GRAY + "  • " + formatMaterial(entry.getKey()) + " x" + entry.getValue());
                }
                lore.add("");
                lore.add(ChatColor.GREEN + "▶ Нажми для активации");

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
     * Открывает GUI депозита для конкретного бустера.
     */
    public void openDeposit(Player player, CoreBlock coreBlock, BoosterDefinition def) {
        Map<Material, Integer> cost = def.getCost();

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
            confirmMeta.setDisplayName(ChatColor.GREEN + "✔ Активировать");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Активировать бустер");
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
     * Обрабатывает подтверждение депозита — проверяет предметы и активирует бустер.
     */
    public void handleConfirm(Player player, Inventory inventory) {
        UUID playerId = player.getUniqueId();
        BoosterDefinition def = depositGuis.get(playerId);
        CoreBlock coreBlock = depositCoreBlocks.get(playerId);
        if (def == null || coreBlock == null) return;

        Island island = getPlayerIsland(player);
        if (island == null) {
            player.sendMessage(ChatColor.RED + "✦ Ты не на острове!");
            returnDepositItems(player, inventory);
            closeDeposit(player);
            return;
        }

        Map<Material, Integer> cost = def.getCost();

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

        // Activate booster via SkyBound API
        try {
            boolean success = SkyBoundAPI.get().getBoosterProvider().purchase(player, island, def.getSkyboundBoosterId());
            if (success) {
                String name = ChatColor.translateAlternateColorCodes('&', def.getDisplayName());
                player.sendMessage(ChatColor.LIGHT_PURPLE + "✦ " + name + ChatColor.LIGHT_PURPLE + " активирован!");
            } else {
                player.sendMessage(ChatColor.RED + "✦ Не удалось активировать бустер!");
                // Refund consumed items
                for (Map.Entry<Material, Integer> entry : cost.entrySet()) {
                    int consumed = entry.getValue() - toConsume.getOrDefault(entry.getKey(), 0);
                    if (consumed > 0) {
                        giveItems(player, entry.getKey(), consumed);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to activate booster: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "✦ Ошибка при активации бустера!");
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

    public BoosterDefinition getDepositBooster(UUID playerId) {
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
        depositGuis.remove(player.getUniqueId());
        depositCoreBlocks.remove(player.getUniqueId());
        player.closeInventory();
    }

    // --- Utility ---

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
            case NETHER_STAR: return "Звезда Незера";
            case BLAZE_ROD: return "Стержень ифрита";
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
