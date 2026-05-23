package me.reil.skybound.addon.core;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

/**
 * Загружает определения типов ядер из cores.yml.
 */
public class CoreConfig {

    private final IslandCorePlugin plugin;
    private final Map<String, CoreType> coreTypes = new LinkedHashMap<>();
    private final List<UpgradeDefinition> upgradeDefinitions = new ArrayList<>();
    private final List<BoosterDefinition> boosterDefinitions = new ArrayList<>();

    public CoreConfig(IslandCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        coreTypes.clear();
        upgradeDefinitions.clear();
        boosterDefinitions.clear();

        File file = new File(plugin.getDataFolder(), "cores.yml");
        if (!file.exists()) {
            plugin.getLogger().warning("cores.yml not found!");
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection coresSection = config.getConfigurationSection("cores");
        if (coresSection == null) {
            plugin.getLogger().warning("No 'cores' section in cores.yml!");
            return;
        }

        for (String key : coresSection.getKeys(false)) {
            ConfigurationSection section = coresSection.getConfigurationSection(key);
            if (section == null) continue;

            String displayName = section.getString("display-name", key);
            List<String> description = section.getStringList("description");
            String customBlockId = section.getString("custom-block", null);
            String guiTitle = section.getString("gui-title", "&8Core");
            int guiSize = section.getInt("gui-size", 27);
            String action = section.getString("action", "ADD_ISLAND_XP");
            String boosterId = section.getString("booster-id", null);
            int boosterDuration = section.getInt("booster-duration", 0);
            String command = section.getString("command", null);

            // Load accepted items (Material-based and SopItems)
            Map<Material, CoreType.CoreItemEntry> acceptedItems = new LinkedHashMap<>();
            Map<String, CoreType.SopItemEntry> sopItems = new LinkedHashMap<>();

            ConfigurationSection itemsSection = section.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String itemKey : itemsSection.getKeys(false)) {
                    ConfigurationSection itemSec = itemsSection.getConfigurationSection(itemKey);
                    if (itemSec == null) continue;

                    int xp = itemSec.getInt("xp", 0);
                    String display = itemSec.getString("display", itemKey + " → " + xp);

                    if (itemKey.startsWith("sop:")) {
                        // SopItemsCreator item
                        String sopItemId = itemKey.substring(4); // Remove "sop:" prefix
                        sopItems.put(sopItemId, new CoreType.SopItemEntry(sopItemId, xp, display));
                    } else {
                        // Standard Material
                        try {
                            Material material = Material.valueOf(itemKey.toUpperCase());
                            acceptedItems.put(material, new CoreType.CoreItemEntry(material, xp, display));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Unknown material '" + itemKey + "' in core type '" + key + "'");
                        }
                    }
                }
            }

            // Load upgrade definitions for UPGRADE_SELECTION cores
            if ("UPGRADE_SELECTION".equals(action)) {
                loadUpgradeDefinitions(section);
            }

            // Load booster definitions for BOOSTER_SELECTION cores
            if ("BOOSTER_SELECTION".equals(action)) {
                loadBoosterDefinitions(section);
            }

            CoreType coreType = new CoreType(key, displayName, description, customBlockId,
                    guiTitle, guiSize, acceptedItems, sopItems, action, boosterId, boosterDuration, command);
            coreTypes.put(key, coreType);
            plugin.getLogger().info("Loaded core type '" + key + "' with custom-block: " + customBlockId);
        }

        plugin.getLogger().info("Loaded " + coreTypes.size() + " core types from cores.yml");
        if (!upgradeDefinitions.isEmpty()) {
            plugin.getLogger().info("Loaded " + upgradeDefinitions.size() + " upgrade definitions.");
        }
        if (!boosterDefinitions.isEmpty()) {
            plugin.getLogger().info("Loaded " + boosterDefinitions.size() + " booster definitions.");
        }
    }

    /**
     * Загружает определения апгрейдов из секции upgrades.
     */
    private void loadUpgradeDefinitions(ConfigurationSection coreSection) {
        ConfigurationSection upgradesSection = coreSection.getConfigurationSection("upgrades");
        if (upgradesSection == null) return;

        for (String upgradeKey : upgradesSection.getKeys(false)) {
            ConfigurationSection sec = upgradesSection.getConfigurationSection(upgradeKey);
            if (sec == null) continue;

            try {
                String displayName = sec.getString("display-name", upgradeKey);
                Material icon;
                try {
                    icon = Material.valueOf(sec.getString("icon", "STONE").toUpperCase());
                } catch (IllegalArgumentException e) {
                    icon = Material.STONE;
                }
                int slot = sec.getInt("slot", 0);
                List<String> description = sec.getStringList("description");
                int maxLevel = sec.getInt("max-level", 5);
                double costMultiplier = sec.getDouble("cost-multiplier", 1.5);
                String skyboundUpgradeId = sec.getString("skybound-upgrade-id", upgradeKey);

                // Load cost-per-level
                Map<Material, Integer> costPerLevel = new LinkedHashMap<>();
                ConfigurationSection costSection = sec.getConfigurationSection("cost-per-level");
                if (costSection != null) {
                    for (String matKey : costSection.getKeys(false)) {
                        try {
                            Material mat = Material.valueOf(matKey.toUpperCase());
                            int amount = costSection.getInt(matKey);
                            costPerLevel.put(mat, amount);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Unknown material '" + matKey + "' in upgrade '" + upgradeKey + "' cost");
                        }
                    }
                }

                UpgradeDefinition def = new UpgradeDefinition(upgradeKey, displayName, icon, slot,
                        description, maxLevel, costPerLevel, costMultiplier, skyboundUpgradeId);
                upgradeDefinitions.add(def);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load upgrade definition: " + upgradeKey, e);
            }
        }
    }

    /**
     * Загружает определения бустеров из секции boosters.
     */
    private void loadBoosterDefinitions(ConfigurationSection coreSection) {
        ConfigurationSection boostersSection = coreSection.getConfigurationSection("boosters");
        if (boostersSection == null) return;

        for (String boosterKey : boostersSection.getKeys(false)) {
            ConfigurationSection sec = boostersSection.getConfigurationSection(boosterKey);
            if (sec == null) continue;

            try {
                String displayName = sec.getString("display-name", boosterKey);
                Material icon;
                try {
                    icon = Material.valueOf(sec.getString("icon", "NETHER_STAR").toUpperCase());
                } catch (IllegalArgumentException e) {
                    icon = Material.NETHER_STAR;
                }
                int slot = sec.getInt("slot", 0);
                List<String> description = sec.getStringList("description");
                String skyboundBoosterId = sec.getString("skybound-booster-id", boosterKey);

                // Load cost
                Map<Material, Integer> cost = new LinkedHashMap<>();
                ConfigurationSection costSection = sec.getConfigurationSection("cost");
                if (costSection != null) {
                    for (String matKey : costSection.getKeys(false)) {
                        try {
                            Material mat = Material.valueOf(matKey.toUpperCase());
                            int amount = costSection.getInt(matKey);
                            cost.put(mat, amount);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Unknown material '" + matKey + "' in booster '" + boosterKey + "' cost");
                        }
                    }
                }

                BoosterDefinition def = new BoosterDefinition(boosterKey, displayName, icon, slot,
                        description, skyboundBoosterId, cost);
                boosterDefinitions.add(def);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load booster definition: " + boosterKey, e);
            }
        }
    }

    public Map<String, CoreType> getCoreTypes() {
        return Collections.unmodifiableMap(coreTypes);
    }

    public CoreType getCoreType(String id) {
        return coreTypes.get(id);
    }

    public List<UpgradeDefinition> getUpgradeDefinitions() {
        return Collections.unmodifiableList(upgradeDefinitions);
    }

    public UpgradeDefinition getUpgradeDefinition(String id) {
        for (UpgradeDefinition def : upgradeDefinitions) {
            if (def.getId().equals(id)) return def;
        }
        return null;
    }

    public List<BoosterDefinition> getBoosterDefinitions() {
        return Collections.unmodifiableList(boosterDefinitions);
    }

    public BoosterDefinition getBoosterDefinition(String id) {
        for (BoosterDefinition def : boosterDefinitions) {
            if (def.getId().equals(id)) return def;
        }
        return null;
    }
}
