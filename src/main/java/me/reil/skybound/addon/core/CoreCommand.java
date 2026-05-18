package me.reil.skybound.addon.core;

import me.reil.skybound.api.SkyBoundAPI;
import me.reil.skybound.api.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Команды администратора для управления ядрами.
 *
 * /islandcore place <type> — разместить ядро
 * /islandcore remove — удалить ядро (на которое смотришь)
 * /islandcore list — список типов ядер
 * /islandcore reload — перезагрузить конфиг
 */
public class CoreCommand implements CommandExecutor {

    private final IslandCorePlugin plugin;

    public CoreCommand(IslandCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("islandcore.admin")) {
            sender.sendMessage(ChatColor.RED + "Нет доступа!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "place":
                return handlePlace(sender, args);
            case "give":
                return handleGive(sender, args);
            case "remove":
                return handleRemove(sender);
            case "list":
                return handleList(sender);
            case "reload":
                return handleReload(sender);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handlePlace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Только для игроков!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Использование: /islandcore place <type>");
            sender.sendMessage(ChatColor.GRAY + "Типы: " + String.join(", ", plugin.getCoreConfig().getCoreTypes().keySet()));
            return true;
        }

        Player player = (Player) sender;
        String typeId = args[1].toLowerCase();

        CoreType coreType = plugin.getCoreConfig().getCoreType(typeId);
        if (coreType == null) {
            sender.sendMessage(ChatColor.RED + "Неизвестный тип ядра: " + typeId);
            sender.sendMessage(ChatColor.GRAY + "Доступные: " + String.join(", ", plugin.getCoreConfig().getCoreTypes().keySet()));
            return true;
        }

        Location loc = player.getLocation().getBlock().getLocation();

        // Determine island id
        String islandId = "admin";
        try {
            Island island = SkyBoundAPI.get().getIslandProvider().getIslandAt(loc);
            if (island != null) {
                islandId = island.getId();
            }
        } catch (Exception ignored) {}

        // Place the block
        if (plugin.getCustomBlocksBridge().isAvailable() && coreType.getCustomBlockId() != null) {
            plugin.getCustomBlocksBridge().placeCustomBlock(coreType.getCustomBlockId(), loc);
        } else {
            String fallbackName = plugin.getConfig().getString("fallback-block", "BEACON");
            try {
                Material fallback = Material.valueOf(fallbackName);
                loc.getBlock().setType(fallback);
            } catch (IllegalArgumentException e) {
                loc.getBlock().setType(Material.BEACON);
            }
        }

        // Register in manager
        plugin.getCoreBlockManager().placeCore(loc, typeId, islandId);

        sender.sendMessage(ChatColor.GREEN + "✦ Ядро '" + coreType.getDisplayName() + ChatColor.GREEN + "' размещено!");
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Использование: /islandcore give <type> [player]");
            sender.sendMessage(ChatColor.GRAY + "Типы: " + String.join(", ", plugin.getCoreConfig().getCoreTypes().keySet()));
            return true;
        }

        String typeId = args[1].toLowerCase();
        CoreType coreType = plugin.getCoreConfig().getCoreType(typeId);
        if (coreType == null) {
            sender.sendMessage(ChatColor.RED + "Неизвестный тип ядра: " + typeId);
            sender.sendMessage(ChatColor.GRAY + "Доступные: " + String.join(", ", plugin.getCoreConfig().getCoreTypes().keySet()));
            return true;
        }

        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Игрок '" + args[2] + "' не найден!");
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "Укажи игрока: /islandcore give <type> <player>");
            return true;
        }

        ItemStack item = plugin.createCoreItem(typeId);
        if (item == null) {
            sender.sendMessage(ChatColor.RED + "Не удалось создать предмет ядра!");
            return true;
        }

        HashMap<Integer, ItemStack> overflow = target.getInventory().addItem(item);
        for (ItemStack drop : overflow.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), drop);
        }

        String displayName = ChatColor.translateAlternateColorCodes('&', coreType.getDisplayName());
        sender.sendMessage(ChatColor.GREEN + "✦ Ядро '" + displayName + ChatColor.GREEN + "' выдано игроку " + target.getName() + "!");
        if (!target.equals(sender)) {
            target.sendMessage(ChatColor.GREEN + "✦ Ты получил " + displayName + ChatColor.GREEN + "! Поставь на остров.");
        }
        return true;
    }

    private boolean handleRemove(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Только для игроков!");
            return true;
        }

        Player player = (Player) sender;
        // Get block player is looking at
        org.bukkit.block.Block target = player.getTargetBlockExact(5);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Посмотри на блок ядра!");
            return true;
        }

        Location loc = target.getLocation();
        CoreBlock coreBlock = plugin.getCoreBlockManager().getCoreAt(loc);

        if (coreBlock == null) {
            sender.sendMessage(ChatColor.RED + "Этот блок не является ядром!");
            return true;
        }

        // Remove custom block if applicable
        if (plugin.getCustomBlocksBridge().isAvailable()) {
            plugin.getCustomBlocksBridge().removeCustomBlock(loc);
        }

        target.setType(Material.AIR);
        plugin.getCoreBlockManager().removeCore(loc);

        sender.sendMessage(ChatColor.GREEN + "✦ Ядро удалено!");
        return true;
    }

    private boolean handleList(CommandSender sender) {
        Map<String, CoreType> types = plugin.getCoreConfig().getCoreTypes();
        sender.sendMessage(ChatColor.GOLD + "=== Типы ядер (" + types.size() + ") ===");

        for (Map.Entry<String, CoreType> entry : types.entrySet()) {
            CoreType type = entry.getValue();
            String name = ChatColor.translateAlternateColorCodes('&', type.getDisplayName());
            sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + entry.getKey()
                    + ChatColor.GRAY + " → " + name
                    + ChatColor.GRAY + " [" + type.getAcceptedItems().size() + " предметов, действие: " + type.getAction() + "]");
        }

        sender.sendMessage(ChatColor.GOLD + "Размещено ядер: " + plugin.getCoreBlockManager().getAllCores().size());
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getCoreConfig().load();
        sender.sendMessage(ChatColor.GREEN + "✦ Конфигурация перезагружена!");
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== SkyBound Island Core ===");
        sender.sendMessage(ChatColor.YELLOW + "/islandcore place <type>" + ChatColor.GRAY + " — разместить ядро");
        sender.sendMessage(ChatColor.YELLOW + "/islandcore give <type> [player]" + ChatColor.GRAY + " — выдать ядро");
        sender.sendMessage(ChatColor.YELLOW + "/islandcore remove" + ChatColor.GRAY + " — удалить ядро");
        sender.sendMessage(ChatColor.YELLOW + "/islandcore list" + ChatColor.GRAY + " — список типов");
        sender.sendMessage(ChatColor.YELLOW + "/islandcore reload" + ChatColor.GRAY + " — перезагрузить конфиг");
    }
}
