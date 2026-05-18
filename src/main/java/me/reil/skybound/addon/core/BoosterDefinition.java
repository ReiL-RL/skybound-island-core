package me.reil.skybound.addon.core;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * Определение бустера, загружается из секции boosters в cores.yml.
 */
public class BoosterDefinition {

    private final String id;
    private final String displayName;
    private final Material icon;
    private final int slot;
    private final List<String> description;
    private final String skyboundBoosterId;
    private final Map<Material, Integer> cost;

    public BoosterDefinition(String id, String displayName, Material icon, int slot,
                             List<String> description, String skyboundBoosterId,
                             Map<Material, Integer> cost) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.slot = slot;
        this.description = description;
        this.skyboundBoosterId = skyboundBoosterId;
        this.cost = cost;
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

    public String getSkyboundBoosterId() {
        return skyboundBoosterId;
    }

    public Map<Material, Integer> getCost() {
        return cost;
    }
}
