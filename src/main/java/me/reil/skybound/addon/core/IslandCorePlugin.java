package me.reil.skybound.addon.core;

import me.reil.skybound.addon.core.integration.SopCustomBlocksBridge;
import me.reil.skybound.addon.core.integration.SopItemsHook;
import me.reil.skybound.api.SkyBoundAPI;
import me.reil.skybound.api.addon.SkyBoundAddon;
import me.reil.skybound.api.addon.AddonRegistry;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * SkyBound Island Core — аддон для интерактивных блоков-ядер,
 * через которые игроки сдают ресурсы для получения XP острова.
 */
public class IslandCorePlugin extends JavaPlugin implements SkyBoundAddon {

    private static IslandCorePlugin instance;

    private CoreConfig coreConfig;
    private CoreBlockManager coreBlockManager;
    private SopCustomBlocksBridge customBlocksBridge;
    private SopItemsHook sopItemsHook;
    private CoreGui coreGui;
    private UpgradeCoreGui upgradeCoreGui;
    private BoosterCoreGui boosterCoreGui;

    @Override
    public void onEnable() {
        instance = this;

        // Check SkyBound availability
        if (getServer().getPluginManager().getPlugin("SkyBound") == null) {
            getLogger().severe("SkyBound not found! Disabling IslandCore...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Save default configs
        saveDefaultConfig();
        saveResource("cores.yml", false);

        // Load core type definitions
        coreConfig = new CoreConfig(this);
        coreConfig.load();

        // Initialize core block manager (placed cores persistence)
        coreBlockManager = new CoreBlockManager(this);
        coreBlockManager.load();

        // Initialize SopCustomBlocks bridge
        customBlocksBridge = new SopCustomBlocksBridge(this);

        // Initialize SopItemsCreator hook
        sopItemsHook = new SopItemsHook(this);

        // Initialize GUI handler
        coreGui = new CoreGui(this);

        // Initialize Upgrade Core GUI handler
        upgradeCoreGui = new UpgradeCoreGui(this);

        // Initialize Booster Core GUI handler
        boosterCoreGui = new BoosterCoreGui(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new CoreListener(this), this);

        // Register command
        getCommand("islandcore").setExecutor(new CoreCommand(this));

        // Register as SkyBound addon
        try {
            SkyBoundAPI.get().getService(AddonRegistry.class).register(this);
            getLogger().info("Registered as SkyBound addon.");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to register as SkyBound addon", e);
        }

        getLogger().info("SkyBound-IslandCore enabled! Loaded " + coreConfig.getCoreTypes().size() + " core types.");
    }

    @Override
    public void onDisable() {
        if (coreBlockManager != null) {
            coreBlockManager.save();
        }
        instance = null;
    }

    // --- SkyBoundAddon implementation ---

    @Override
    public String getAddonId() {
        return "island-core";
    }

    @Override
    public String getAddonName() {
        return "Island Core";
    }

    @Override
    public String getAddonVersion() {
        return getDescription().getVersion();
    }

    @Override
    public void onAddonEnable() {
        // Already handled in onEnable
    }

    @Override
    public void onAddonDisable() {
        // Already handled in onDisable
    }

    @Override
    public boolean supportsStandalone() {
        return false;
    }

    // --- Getters ---

    public static IslandCorePlugin getInstance() {
        return instance;
    }

    /**
     * Создаёт ItemStack ядра для выдачи игроку.
     * Использует SopCustomBlocks если доступен, иначе fallback.
     */
    public ItemStack createCoreItem(String coreTypeId) {
        CoreType type = coreConfig.getCoreType(coreTypeId);
        if (type == null) return null;

        // If SopCustomBlocks available and has custom block item
        if (customBlocksBridge.isAvailable() && type.getCustomBlockId() != null) {
            ItemStack item = customBlocksBridge.getBlockItem(type.getCustomBlockId());
            if (item != null) return item;
        }

        // Fallback: create item from fallback material with custom name/lore
        String fallbackMat = getConfig().getString("fallback-block", "BEACON");
        Material mat;
        try {
            mat = Material.valueOf(fallbackMat);
        } catch (IllegalArgumentException e) {
            mat = Material.BEACON;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', type.getDisplayName()));
            List<String> lore = new ArrayList<>();
            for (String line : type.getDescription()) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            lore.add("");
            lore.add(ChatColor.GRAY + "Поставь на остров для активации");
            lore.add(ChatColor.DARK_GRAY + "core:" + coreTypeId);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public CoreConfig getCoreConfig() {
        return coreConfig;
    }

    public CoreBlockManager getCoreBlockManager() {
        return coreBlockManager;
    }

    public SopCustomBlocksBridge getCustomBlocksBridge() {
        return customBlocksBridge;
    }

    public SopItemsHook getSopItemsHook() {
        return sopItemsHook;
    }

    public CoreGui getCoreGui() {
        return coreGui;
    }

    /**
     * Called directly by SkyBound Core when island is created.
     * Places cores at island center instead of giving items.
     */
    public void onIslandCreated(org.bukkit.entity.Player player, String islandId) {
        System.out.println("========================================");
        System.out.println("ISLAND-CORE: onIslandCreated called!");
        System.out.println("Player: " + player.getName());
        System.out.println("islandId: " + islandId);
        System.out.println("========================================");
        System.out.flush();
        
        try {
            Thread.sleep(50);
        } catch (Exception e) {}

        // Check if auto-place is enabled
        boolean placeEnabled = getConfig().getBoolean("place-on-create", true);
        System.out.println("ISLAND-CORE: place-on-create = " + placeEnabled);
        System.out.flush();
        
        if (!placeEnabled) {
            System.out.println("ISLAND-CORE: place-on-create is disabled, skipping");
            System.out.flush();
            return;
        }

        // Get location from SkyBound API
        try {
            me.reil.skybound.api.island.Island island = SkyBoundAPI.get().getIslandProvider().getIsland(islandId);
            if (island == null) {
                System.out.println("ISLAND-CORE: Island not found for ID: " + islandId);
                System.out.flush();
                return;
            }

            org.bukkit.Location center = island.getCenter();
            if (center == null) {
                System.out.println("ISLAND-CORE: Center is null for island: " + islandId);
                System.out.flush();
                return;
            }

            System.out.println("ISLAND-CORE: Center location: " + center.getX() + ", " + center.getY() + ", " + center.getZ());
            System.out.println("ISLAND-CORE: Center block coords: " + center.getBlockX() + ", " + center.getBlockY() + ", " + center.getBlockZ());
            System.out.flush();

            // Place cores at center (stacked vertically)
            java.util.List<String> autoPlaceList = getConfig().getStringList("auto-place-on-create");
            System.out.println("ISLAND-CORE: Auto-place list: " + autoPlaceList);
            System.out.flush();

            // Get integer block coordinates for center
            int centerX = center.getBlockX();
            int centerY = center.getBlockY();
            int centerZ = center.getBlockZ();
            System.out.println("ISLAND-CORE: Center block coords: " + centerX + ", " + centerY + ", " + centerZ);
            System.out.flush();

            int yOffset = 0;
            for (String coreTypeId : autoPlaceList) {
                CoreType coreType = coreConfig.getCoreType(coreTypeId);
                if (coreType == null) {
                    System.out.println("ISLAND-CORE: Core type not found: " + coreTypeId);
                    System.out.flush();
                    continue;
                }

                // Place custom block with vertical offset - use integer coordinates
                Location coreLoc = new Location(center.getWorld(), centerX, centerY + yOffset, centerZ);
                
                if (customBlocksBridge.isAvailable() && coreType.getCustomBlockId() != null) {
                    // Place the custom block using SopCustomBlocks API
                    Bukkit.getScheduler().runTaskLater(this, new Runnable() {
                    @Override
                    public void run() {
                        customBlocksBridge.placeCustomBlock(coreType.getCustomBlockId(), coreLoc);
                    }
                    }, 20);

                    
                    System.out.println("ISLAND-CORE: Placing " + coreTypeId + " at " + coreLoc.getBlockX() + "," + coreLoc.getBlockY() + "," + coreLoc.getBlockZ() + ": SUCCESS");
                    System.out.flush();
                } else {
                    System.out.println("ISLAND-CORE: No custom block for " + coreTypeId + ", skipping");
                    System.out.flush();
                }
                
                yOffset++; // Next core will be 1 block higher
            }

        } catch (Exception e) {
            System.out.println("ISLAND-CORE: Error placing cores: " + e.getMessage());
            e.printStackTrace(System.out);
            System.out.flush();
        }
    }

    public UpgradeCoreGui getUpgradeCoreGui() {
        return upgradeCoreGui;
    }

    public BoosterCoreGui getBoosterCoreGui() {
        return boosterCoreGui;
    }
}
