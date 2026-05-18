package me.reil.skybound.addon.core;

import me.reil.skybound.addon.core.integration.SopItemsHook;
import me.reil.skybound.api.SkyBoundAPI;
import me.reil.skybound.api.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Constructor;
import java.util.*;

/**
 * GUI для взаимодействия с ядром: показ принимаемых предметов,
 * слоты для депозита, кнопки подтверждения/отмены.
 */
public class CoreGui {

    private final IslandCorePlugin plugin;

    // Tracking open GUIs: player UUID → core type id
    private final Map<UUID, String> openGuis = new HashMap<>();
    // Tracking which core block the player is interacting with
    private final Map<UUID, CoreBlock> openCoreBlocks = new HashMap<>();

    private static final int CONFIRM_SLOT = 22;
    private static final int CANCEL_SLOT = 26;
    private static final int DEPOSIT_START = 9;
    private static final int DEPOSIT_END = 17;

    public CoreGui(IslandCorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Открывает GUI ядра для игрока.
     */
    public void open(Player player, CoreBlock coreBlock) {
        CoreType coreType = plugin.getCoreConfig().getCoreType(coreBlock.getCoreTypeId());
        if (coreType == null) {
            player.sendMessage(ChatColor.RED + "Неизвестный тип ядра: " + coreBlock.getCoreTypeId());
            return;
        }

        String title = ChatColor.translateAlternateColorCodes('&', coreType.getGuiTitle());
        Inventory inv = Bukkit.createInventory(null, coreType.getGuiSize(), title);

        // Fill info row (slots 0-8) with items showing accepted materials
        int slot = 0;

        // Standard Material items
        for (Map.Entry<Material, CoreType.CoreItemEntry> entry : coreType.getAcceptedItems().entrySet()) {
            if (slot > 8) break;
            CoreType.CoreItemEntry itemEntry = entry.getValue();

            ItemStack infoItem = new ItemStack(entry.getKey());
            ItemMeta meta = infoItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', itemEntry.getDisplayText()));
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Положи этот предмет ниже");
                meta.setLore(lore);
                infoItem.setItemMeta(meta);
            }
            inv.setItem(slot, infoItem);
            slot++;
        }

        // SopItemsCreator items
        SopItemsHook sopHook = plugin.getSopItemsHook();
        for (Map.Entry<String, CoreType.SopItemEntry> entry : coreType.getSopItems().entrySet()) {
            if (slot > 8) break;
            CoreType.SopItemEntry sopEntry = entry.getValue();

            ItemStack infoItem = null;
            if (sopHook != null && sopHook.isAvailable()) {
                infoItem = sopHook.getCustomItem(sopEntry.getSopItemId());
            }
            // Fallback if SopItemsCreator not available or item not found
            if (infoItem == null) {
                infoItem = new ItemStack(Material.BARRIER);
            }

            ItemMeta meta = infoItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', sopEntry.getDisplayText()));
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Положи этот предмет ниже");
                meta.setLore(lore);
                infoItem.setItemMeta(meta);
            }
            inv.setItem(slot, infoItem);
            slot++;
        }

        // Fill remaining info slots with gray glass
        while (slot <= 8) {
            ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = filler.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(" ");
                filler.setItemMeta(meta);
            }
            inv.setItem(slot, filler);
            slot++;
        }

        // Deposit area (slots 9-17) left empty for player items

        // Fill bottom row decoration (slots 18-26) except confirm/cancel
        for (int i = 18; i < coreType.getGuiSize(); i++) {
            if (i == CONFIRM_SLOT || i == CANCEL_SLOT) continue;
            ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta meta = filler.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(" ");
                filler.setItemMeta(meta);
            }
            inv.setItem(i, filler);
        }

        // Confirm button
        ItemStack confirm = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta confirmMeta = confirm.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.setDisplayName(ChatColor.GREEN + "✔ Подтвердить");
            confirm.setItemMeta(confirmMeta);
        }
        inv.setItem(CONFIRM_SLOT, confirm);

        // Cancel button
        ItemStack cancel = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta cancelMeta = cancel.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName(ChatColor.RED + "✘ Отмена");
            cancel.setItemMeta(cancelMeta);
        }
        inv.setItem(CANCEL_SLOT, cancel);

        player.openInventory(inv);
        openGuis.put(player.getUniqueId(), coreType.getId());
        openCoreBlocks.put(player.getUniqueId(), coreBlock);
    }

    /**
     * Проверяет, открыт ли GUI ядра у игрока.
     */
    public boolean hasOpenGui(UUID playerId) {
        return openGuis.containsKey(playerId);
    }

    /**
     * Получает ID типа ядра для открытого GUI.
     */
    public String getOpenCoreTypeId(UUID playerId) {
        return openGuis.get(playerId);
    }

    /**
     * Получает CoreBlock для открытого GUI.
     */
    public CoreBlock getOpenCoreBlock(UUID playerId) {
        return openCoreBlocks.get(playerId);
    }

    /**
     * Обрабатывает подтверждение депозита.
     */
    public void handleConfirm(Player player, Inventory inventory) {
        UUID playerId = player.getUniqueId();
        String coreTypeId = openGuis.get(playerId);
        CoreBlock coreBlock = openCoreBlocks.get(playerId);

        if (coreTypeId == null || coreBlock == null) return;

        CoreType coreType = plugin.getCoreConfig().getCoreType(coreTypeId);
        if (coreType == null) return;

        SopItemsHook sopHook = plugin.getSopItemsHook();
        long totalXp = 0;
        List<ItemStack> returnItems = new ArrayList<>();

        // Scan deposit slots
        for (int i = DEPOSIT_START; i <= DEPOSIT_END; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            boolean matched = false;

            // Check standard Material items
            CoreType.CoreItemEntry entry = coreType.getAcceptedItems().get(item.getType());
            if (entry != null) {
                totalXp += (long) entry.getXpValue() * item.getAmount();
                matched = true;
            }

            // Check SopItemsCreator items
            if (!matched && sopHook != null && sopHook.isAvailable()) {
                for (CoreType.SopItemEntry sopEntry : coreType.getSopItems().values()) {
                    if (sopHook.isCustomItem(item, sopEntry.getSopItemId())) {
                        totalXp += (long) sopEntry.getXpValue() * item.getAmount();
                        matched = true;
                        break;
                    }
                }
            }

            if (!matched) {
                // Return non-accepted items
                returnItems.add(item.clone());
            }
            inventory.setItem(i, null);
        }

        // Return non-accepted items to player
        for (ItemStack returnItem : returnItems) {
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(returnItem);
            for (ItemStack drop : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }

        // Execute action
        if (totalXp > 0) {
            executeAction(player, coreType, coreBlock, totalXp);
        } else {
            player.sendMessage(ChatColor.YELLOW + "✦ Ты не положил подходящих предметов!");
        }

        // Close and cleanup
        closeGui(player);
    }

    /**
     * Выполняет действие ядра после депозита.
     */
    private void executeAction(Player player, CoreType coreType, CoreBlock coreBlock, long totalXp) {
        String action = coreType.getAction();

        switch (action) {
            case "ADD_ISLAND_XP":
                try {
                    Island island = SkyBoundAPI.get().getIslandProvider().getPlayerIsland(player.getUniqueId());
                    if (island != null) {
                        island.addExperience(totalXp);
                        player.sendMessage(ChatColor.GREEN + "✦ +" + totalXp + " XP острова!");
                    } else {
                        player.sendMessage(ChatColor.RED + "✦ Ты не на острове!");
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to add island XP: " + e.getMessage());
                    player.sendMessage(ChatColor.RED + "✦ Ошибка при начислении XP!");
                }
                break;

            case "ACTIVATE_BOOSTER":
                try {
                    String boosterId = coreType.getBoosterId();
                    if (boosterId != null) {
                        Island island = SkyBoundAPI.get().getIslandProvider().getPlayerIsland(player.getUniqueId());
                        if (island != null) {
                            boolean success = SkyBoundAPI.get().getBoosterProvider().purchase(player, island, boosterId);
                            if (success) {
                                player.sendMessage(ChatColor.LIGHT_PURPLE + "✦ Бустер '" + boosterId + "' активирован!");
                            } else {
                                player.sendMessage(ChatColor.RED + "✦ Не удалось активировать бустер!");
                            }
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to activate booster: " + e.getMessage());
                    player.sendMessage(ChatColor.RED + "✦ Ошибка при активации бустера!");
                }
                break;

            case "RUN_COMMAND":
                String cmd = coreType.getCommand();
                if (cmd != null) {
                    cmd = cmd.replace("{player}", player.getName())
                            .replace("{xp}", String.valueOf(totalXp));
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    player.sendMessage(ChatColor.GREEN + "✦ Действие выполнено!");
                }
                break;

            default:
                plugin.getLogger().warning("Unknown core action: " + action);
                break;
        }

        // Fire FlexAchievements event via reflection (optional dependency)
        fireAchievementEvent(player, coreType.getId(), totalXp);
    }

    /**
     * Fires FlexAchievements event via reflection if available.
     */
    private void fireAchievementEvent(Player player, String coreTypeId, long xpAmount) {
        try {
            Class<?> eventClass = Class.forName("me.reil.flexachievements.api.event.AchievementProgressEvent");
            Constructor<?> constructor = eventClass.getConstructor(Player.class, String.class, Map.class);
            Map<String, Object> context = new HashMap<>();
            context.put("core.type", coreTypeId);
            context.put("xp.amount", xpAmount);
            Event event = (Event) constructor.newInstance(player, "SKYBOUND_CORE_DEPOSIT", context);
            Bukkit.getPluginManager().callEvent(event);
        } catch (ClassNotFoundException ignored) {
            // FlexAchievements not available
        } catch (Exception e) {
            plugin.getLogger().fine("Could not fire achievement event: " + e.getMessage());
        }
    }

    /**
     * Обрабатывает отмену — возвращает предметы игроку.
     */
    public void handleCancel(Player player, Inventory inventory) {
        returnDepositItems(player, inventory);
        closeGui(player);
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

    /**
     * Закрывает GUI и очищает данные.
     */
    public void closeGui(Player player) {
        openGuis.remove(player.getUniqueId());
        openCoreBlocks.remove(player.getUniqueId());
        player.closeInventory();
    }

    /**
     * Удаляет данные при закрытии инвентаря.
     */
    public void onClose(UUID playerId) {
        openGuis.remove(playerId);
        openCoreBlocks.remove(playerId);
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
