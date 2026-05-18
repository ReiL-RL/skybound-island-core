package me.reil.skybound.addon.core;

import me.reil.skybound.api.SkyBoundAPI;
import me.reil.skybound.api.event.IslandCreateEvent;
import me.reil.skybound.api.event.IslandRegenEvent;
import me.reil.skybound.api.island.Island;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

/**
 * Обработчик событий: клик по ядру, GUI-взаимодействие, создание острова.
 */
public class CoreListener implements Listener {

    private final IslandCorePlugin plugin;

    public CoreListener(IslandCorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Обработка правого клика по блоку — открытие GUI ядра.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        Location loc = block.getLocation();

        // Check if this block is a registered core
        CoreBlock coreBlock = plugin.getCoreBlockManager().getCoreAt(loc);

        // Also check via SopCustomBlocks if available
        if (coreBlock == null && plugin.getCustomBlocksBridge().isAvailable()) {
            // Try to find core by custom block id
            for (CoreType coreType : plugin.getCoreConfig().getCoreTypes().values()) {
                if (coreType.getCustomBlockId() != null &&
                        plugin.getCustomBlocksBridge().isCustomBlock(loc, coreType.getCustomBlockId())) {
                    // Auto-register this core block
                    String islandId = getIslandIdAt(loc);
                    if (islandId != null) {
                        plugin.getCoreBlockManager().placeCore(loc, coreType.getId(), islandId);
                        coreBlock = plugin.getCoreBlockManager().getCoreAt(loc);
                        break;
                    }
                }
            }
        }

        if (coreBlock == null) return;

        // Check permission
        if (!player.hasPermission("islandcore.use")) {
            player.sendMessage(ChatColor.RED + "У тебя нет доступа к ядру!");
            return;
        }

        event.setCancelled(true);

        // Check if this core uses UPGRADE_SELECTION action
        CoreType coreType = plugin.getCoreConfig().getCoreType(coreBlock.getCoreTypeId());
        if (coreType != null && "UPGRADE_SELECTION".equals(coreType.getAction())) {
            plugin.getUpgradeCoreGui().openSelection(player, coreBlock);
        } else if (coreType != null && "BOOSTER_SELECTION".equals(coreType.getAction())) {
            plugin.getBoosterCoreGui().openSelection(player, coreBlock);
        } else {
            plugin.getCoreGui().open(player, coreBlock);
        }
    }

    /**
     * Обработка кликов в GUI ядра.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();

        // --- Booster Core: Selection GUI ---
        if (plugin.getBoosterCoreGui().hasSelectionOpen(playerId)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= 27) return;

            // Check if clicked slot matches a booster
            CoreBlock coreBlock = plugin.getBoosterCoreGui().getSelectionCoreBlock(playerId);
            for (BoosterDefinition def : plugin.getCoreConfig().getBoosterDefinitions()) {
                if (def.getSlot() == slot) {
                    plugin.getBoosterCoreGui().openDeposit(player, coreBlock, def);
                    return;
                }
            }
            return;
        }

        // --- Booster Core: Deposit GUI ---
        if (plugin.getBoosterCoreGui().hasDepositOpen(playerId)) {
            int slot = event.getRawSlot();
            Inventory inv = event.getInventory();

            // Info row (0-8) — block clicks
            if (slot >= 0 && slot <= 8) {
                event.setCancelled(true);
                return;
            }

            // Deposit area (9-17) — allow placing items
            if (slot >= plugin.getBoosterCoreGui().getDepositStart() && slot <= plugin.getBoosterCoreGui().getDepositEnd()) {
                return;
            }

            // Confirm button
            if (slot == plugin.getBoosterCoreGui().getConfirmSlot()) {
                event.setCancelled(true);
                plugin.getBoosterCoreGui().handleConfirm(player, inv);
                return;
            }

            // Cancel button
            if (slot == plugin.getBoosterCoreGui().getCancelSlot()) {
                event.setCancelled(true);
                plugin.getBoosterCoreGui().handleCancel(player, inv);
                return;
            }

            // Bottom row decoration — block clicks
            if (slot >= 18 && slot < inv.getSize()) {
                event.setCancelled(true);
                return;
            }
            // Player inventory clicks — allow (for moving items to deposit)
            return;
        }

        // --- Upgrade Core: Selection GUI ---
        if (plugin.getUpgradeCoreGui().hasSelectionOpen(playerId)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= 27) return;

            // Check if clicked slot matches an upgrade
            CoreBlock coreBlock = plugin.getUpgradeCoreGui().getSelectionCoreBlock(playerId);
            for (UpgradeDefinition def : plugin.getCoreConfig().getUpgradeDefinitions()) {
                if (def.getSlot() == slot) {
                    plugin.getUpgradeCoreGui().openDeposit(player, coreBlock, def);
                    return;
                }
            }
            return;
        }

        // --- Upgrade Core: Deposit GUI ---
        if (plugin.getUpgradeCoreGui().hasDepositOpen(playerId)) {
            int slot = event.getRawSlot();
            Inventory inv = event.getInventory();

            // Info row (0-8) — block clicks
            if (slot >= 0 && slot <= 8) {
                event.setCancelled(true);
                return;
            }

            // Deposit area (9-17) — allow placing items
            if (slot >= plugin.getUpgradeCoreGui().getDepositStart() && slot <= plugin.getUpgradeCoreGui().getDepositEnd()) {
                return;
            }

            // Confirm button
            if (slot == plugin.getUpgradeCoreGui().getConfirmSlot()) {
                event.setCancelled(true);
                plugin.getUpgradeCoreGui().handleConfirm(player, inv);
                return;
            }

            // Cancel button
            if (slot == plugin.getUpgradeCoreGui().getCancelSlot()) {
                event.setCancelled(true);
                plugin.getUpgradeCoreGui().handleCancel(player, inv);
                return;
            }

            // Bottom row decoration — block clicks
            if (slot >= 18 && slot < inv.getSize()) {
                event.setCancelled(true);
                return;
            }
            // Player inventory clicks — allow (for moving items to deposit)
            return;
        }

        // --- Regular Core GUI ---
        if (!plugin.getCoreGui().hasOpenGui(playerId)) return;

        int slot = event.getRawSlot();
        Inventory inv = event.getInventory();

        // Info row (0-8) — block clicks
        if (slot >= 0 && slot <= 8) {
            event.setCancelled(true);
            return;
        }

        // Deposit area (9-17) — allow placing items
        if (slot >= plugin.getCoreGui().getDepositStart() && slot <= plugin.getCoreGui().getDepositEnd()) {
            // Allow interaction in deposit slots
            return;
        }

        // Confirm button
        if (slot == plugin.getCoreGui().getConfirmSlot()) {
            event.setCancelled(true);
            plugin.getCoreGui().handleConfirm(player, inv);
            return;
        }

        // Cancel button
        if (slot == plugin.getCoreGui().getCancelSlot()) {
            event.setCancelled(true);
            plugin.getCoreGui().handleCancel(player, inv);
            return;
        }

        // Bottom row decoration — block clicks
        if (slot >= 18 && slot < inv.getSize()) {
            event.setCancelled(true);
            return;
        }

        // Player inventory clicks — allow (for moving items to deposit)
    }

    /**
     * Возврат предметов при закрытии GUI без подтверждения.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Handle booster core deposit GUI close
        if (plugin.getBoosterCoreGui().hasDepositOpen(playerId)) {
            plugin.getBoosterCoreGui().returnDepositItems(player, event.getInventory());
            plugin.getBoosterCoreGui().onClose(playerId);
            return;
        }

        // Handle booster core selection GUI close
        if (plugin.getBoosterCoreGui().hasSelectionOpen(playerId)) {
            plugin.getBoosterCoreGui().onClose(playerId);
            return;
        }

        // Handle upgrade core deposit GUI close
        if (plugin.getUpgradeCoreGui().hasDepositOpen(playerId)) {
            plugin.getUpgradeCoreGui().returnDepositItems(player, event.getInventory());
            plugin.getUpgradeCoreGui().onClose(playerId);
            return;
        }

        // Handle upgrade core selection GUI close
        if (plugin.getUpgradeCoreGui().hasSelectionOpen(playerId)) {
            plugin.getUpgradeCoreGui().onClose(playerId);
            return;
        }

        // Handle regular core GUI close
        if (!plugin.getCoreGui().hasOpenGui(playerId)) return;

        // Return items from deposit slots
        plugin.getCoreGui().returnDepositItems(player, event.getInventory());
        plugin.getCoreGui().onClose(playerId);
    }

    /**
     * Авто-выдача ядер при создании острова (как предметы в инвентарь).
     */
    @EventHandler
    public void onIslandCreate(IslandCreateEvent event) {
        Island island = event.getIsland();

        // Old behavior: auto-place block at center
        if (plugin.getConfig().getBoolean("auto-place-on-create", false)) {
            Location center = island.getCenter();
            Location coreLoc = center.clone().add(0, 1, 0);

            CoreType xpCore = plugin.getCoreConfig().getCoreType("xp_core");
            if (xpCore != null) {
                if (plugin.getCustomBlocksBridge().isAvailable() && xpCore.getCustomBlockId() != null) {
                    plugin.getCustomBlocksBridge().placeCustomBlock(xpCore.getCustomBlockId(), coreLoc);
                } else {
                    String fallbackName = plugin.getConfig().getString("fallback-block", "BEACON");
                    try {
                        Material fallback = Material.valueOf(fallbackName);
                        coreLoc.getBlock().setType(fallback);
                    } catch (IllegalArgumentException e) {
                        coreLoc.getBlock().setType(Material.BEACON);
                    }
                }
                plugin.getCoreBlockManager().placeCore(coreLoc, "xp_core", island.getId());
                plugin.getLogger().info("Auto-placed xp_core for island " + island.getId());
            }
            return;
        }

        // New behavior: give core items to player
        if (!plugin.getConfig().getBoolean("give-on-create", true)) return;

        Player player = event.getPlayer();
        List<String> autoGive = plugin.getConfig().getStringList("auto-give-on-create");

        int given = 0;
        for (String typeId : autoGive) {
            ItemStack item = plugin.createCoreItem(typeId);
            if (item != null) {
                player.getInventory().addItem(item);
                given++;
            }
        }

        if (given > 0) {
            player.sendMessage(ChatColor.GREEN + "✦ Ты получил ядра острова! Поставь их на свой остров.");
        }
    }

    /**
     * Выдача ядер при регенерации острова.
     */
    @EventHandler
    public void onIslandRegen(IslandRegenEvent event) {
        if (!plugin.getConfig().getBoolean("give-on-create", true)) return;

        Player player = event.getPlayer();
        List<String> autoGive = plugin.getConfig().getStringList("auto-give-on-create");

        int given = 0;
        for (String typeId : autoGive) {
            ItemStack item = plugin.createCoreItem(typeId);
            if (item != null) {
                player.getInventory().addItem(item);
                given++;
            }
        }

        if (given > 0) {
            player.sendMessage(ChatColor.GREEN + "✦ Ты получил ядра острова! Поставь их на свой остров.");
        }
    }

    /**
     * Обработка размещения блока — регистрация ядра при установке core-предмета.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return;

        // Check if it's a core item by looking for "core:" in lore
        String coreTypeId = null;
        for (String line : meta.getLore()) {
            String stripped = ChatColor.stripColor(line);
            if (stripped.startsWith("core:")) {
                coreTypeId = stripped.substring(5);
                break;
            }
        }
        if (coreTypeId == null) return;

        // Verify it's a valid core type
        CoreType type = plugin.getCoreConfig().getCoreType(coreTypeId);
        if (type == null) return;

        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        // Check player is on their island
        String islandId = null;
        try {
            Island island = SkyBoundAPI.get().getIslandProvider().getIslandAt(loc);
            if (island == null) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Ядро можно поставить только на своём острове!");
                return;
            }
            if (!island.getMembers().contains(player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Это не твой остров!");
                return;
            }
            islandId = island.getId();
        } catch (Exception e) {
            // If API fails, allow placement but use "unknown" island
            islandId = "unknown";
        }

        // Register core
        plugin.getCoreBlockManager().placeCore(loc, coreTypeId, islandId);

        String displayName = ChatColor.translateAlternateColorCodes('&', type.getDisplayName());
        player.sendMessage(ChatColor.GREEN + "✦ " + displayName + ChatColor.GREEN + " установлено!");
    }

    /**
     * Получает ID острова по локации.
     */
    private String getIslandIdAt(Location location) {
        try {
            Island island = SkyBoundAPI.get().getIslandProvider().getIslandAt(location);
            return island != null ? island.getId() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
