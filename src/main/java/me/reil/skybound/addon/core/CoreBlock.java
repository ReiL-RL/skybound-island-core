package me.reil.skybound.addon.core;

import org.bukkit.Location;

/**
 * Модель данных размещённого ядра.
 */
public class CoreBlock {

    private final String coreTypeId;
    private final Location location;
    private final String islandId;

    public CoreBlock(String coreTypeId, Location location, String islandId) {
        this.coreTypeId = coreTypeId;
        this.location = location;
        this.islandId = islandId;
    }

    public String getCoreTypeId() {
        return coreTypeId;
    }

    public Location getLocation() {
        return location;
    }

    public String getIslandId() {
        return islandId;
    }
}
