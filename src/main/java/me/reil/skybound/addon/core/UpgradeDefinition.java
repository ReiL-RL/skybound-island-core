package me.reil.skybound.addon.core;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * Определение апгрейда, загружается из секции upgrades в cores.yml.
 */
public class UpgradeDefinition {

    private final String id;
    private final String displayName;
    private final Material icon;
    private final int slot;
    private final List<String> description;
    private final int maxLevel;
    private final Map<Material, Integer> costPerLevel;
    private final double costMultiplier;
    private final String skyboundUpgradeId;

    public UpgradeDefinition(String id, String displayName, Material icon, int slot,
                             List<String> description, int maxLevel,
                             Map<Material, Integer> costPerLevel, double costMultiplier,
                             String skyboundUpgradeId) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.slot = slot;
        this.description = description;
        this.maxLevel = maxLevel;
        this.costPerLevel = costPerLevel;
        this.costMultiplier = costMultiplier;
        this.skyboundUpgradeId = skyboundUpgradeId;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public int getSlot() {
        return slot;
    }

    public List<String> getDescription() {
        return description;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public Map<Material, Integer> getCostPerLevel() {
        return costPerLevel;
    }

    public double getCostMultiplier() {
        return costMultiplier;
    }

    public String getSkyboundUpgradeId() {
        return skyboundUpgradeId;
    }
}
