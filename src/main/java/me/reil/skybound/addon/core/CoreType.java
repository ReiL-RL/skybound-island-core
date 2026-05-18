package me.reil.skybound.addon.core;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * Определение типа ядра, загружается из cores.yml.
 */
public class CoreType {

    private final String id;
    private final String displayName;
    private final List<String> description;
    private final String customBlockId;
    private final String guiTitle;
    private final int guiSize;
    private final Map<Material, CoreItemEntry> acceptedItems;
    private final Map<String, SopItemEntry> sopItems;
    private final String action;
    private final String boosterId;
    private final int boosterDuration;
    private final String command;

    public CoreType(String id, String displayName, List<String> description,
                    String customBlockId, String guiTitle, int guiSize,
                    Map<Material, CoreItemEntry> acceptedItems, Map<String, SopItemEntry> sopItems,
                    String action, String boosterId, int boosterDuration, String command) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.customBlockId = customBlockId;
        this.guiTitle = guiTitle;
        this.guiSize = guiSize;
        this.acceptedItems = acceptedItems;
        this.sopItems = sopItems;
        this.action = action;
        this.boosterId = boosterId;
        this.boosterDuration = boosterDuration;
        this.command = command;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getDescription() {
        return description;
    }

    public String getCustomBlockId() {
        return customBlockId;
    }

    public String getGuiTitle() {
        return guiTitle;
    }

    public int getGuiSize() {
        return guiSize;
    }

    public Map<Material, CoreItemEntry> getAcceptedItems() {
        return acceptedItems;
    }

    /**
     * Получает SopItemsCreator предметы (ключ = sop item id).
     */
    public Map<String, SopItemEntry> getSopItems() {
        return sopItems;
    }

    public String getAction() {
        return action;
    }

    public String getBoosterId() {
        return boosterId;
    }

    public int getBoosterDuration() {
        return boosterDuration;
    }

    public String getCommand() {
        return command;
    }

    /**
     * Запись принимаемого предмета: материал, кол-во XP, текст для GUI.
     */
    public static class CoreItemEntry {
        private final Material material;
        private final int xpValue;
        private final String displayText;

        public CoreItemEntry(Material material, int xpValue, String displayText) {
            this.material = material;
            this.xpValue = xpValue;
            this.displayText = displayText;
        }

        public Material getMaterial() {
            return material;
        }

        public int getXpValue() {
            return xpValue;
        }

        public String getDisplayText() {
            return displayText;
        }
    }

    /**
     * Запись SopItemsCreator предмета: item id, кол-во XP, текст для GUI.
     */
    public static class SopItemEntry {
        private final String sopItemId;
        private final int xpValue;
        private final String displayText;

        public SopItemEntry(String sopItemId, int xpValue, String displayText) {
            this.sopItemId = sopItemId;
            this.xpValue = xpValue;
            this.displayText = displayText;
        }

        public String getSopItemId() {
            return sopItemId;
        }

        public int getXpValue() {
            return xpValue;
        }

        public String getDisplayText() {
            return displayText;
        }
    }
}
