package me.reil.skybound.addon.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Управляет размещёнными ядрами: хранение, загрузка, сохранение.
 */
public class CoreBlockManager {

    private final IslandCorePlugin plugin;
    private final Map<Location, CoreBlock> placedCores = new HashMap<>();
    private final File dataFile;

    public CoreBlockManager(IslandCorePlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "cores-data.yml");
    }

    /**
     * Размещает ядро в указанной локации.
     */
    public void placeCore(Location location, String coreTypeId, String islandId) {
        CoreBlock core = new CoreBlock(coreTypeId, location, islandId);
        placedCores.put(location, core);
        save();
    }

    /**
     * Удаляет ядро из указанной локации.
     */
    public void removeCore(Location location) {
        placedCores.remove(location);
        save();
    }

    /**
     * Получает ядро по локации.
     */
    public CoreBlock getCoreAt(Location location) {
        return placedCores.get(location);
    }

    /**
     * Получает все ядра для указанного острова.
     */
    public List<CoreBlock> getIslandCores(String islandId) {
        return placedCores.values().stream()
                .filter(core -> core.getIslandId().equals(islandId))
                .collect(Collectors.toList());
    }

    /**
     * Получает все размещённые ядра.
     */
    public Collection<CoreBlock> getAllCores() {
        return Collections.unmodifiableCollection(placedCores.values());
    }

    /**
     * Загружает данные из cores-data.yml.
     */
    public void load() {
        placedCores.clear();

        if (!dataFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = config.getConfigurationSection("cores");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection coreSec = section.getConfigurationSection(key);
            if (coreSec == null) continue;

            String worldName = coreSec.getString("world");
            double x = coreSec.getDouble("x");
            double y = coreSec.getDouble("y");
            double z = coreSec.getDouble("z");
            String coreTypeId = coreSec.getString("type");
            String islandId = coreSec.getString("island");

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("World '" + worldName + "' not found for core at " + key);
                continue;
            }

            Location loc = new Location(world, x, y, z);
            placedCores.put(loc, new CoreBlock(coreTypeId, loc, islandId));
        }

        plugin.getLogger().info("Loaded " + placedCores.size() + " placed cores.");
    }

    /**
     * Сохраняет данные в cores-data.yml.
     */
    public void save() {
        YamlConfiguration config = new YamlConfiguration();

        int index = 0;
        for (Map.Entry<Location, CoreBlock> entry : placedCores.entrySet()) {
            Location loc = entry.getKey();
            CoreBlock core = entry.getValue();

            String path = "cores." + index;
            config.set(path + ".world", loc.getWorld().getName());
            config.set(path + ".x", loc.getBlockX());
            config.set(path + ".y", loc.getBlockY());
            config.set(path + ".z", loc.getBlockZ());
            config.set(path + ".type", core.getCoreTypeId());
            config.set(path + ".island", core.getIslandId());
            index++;
        }

        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save cores-data.yml: " + e.getMessage());
        }
    }
}
