package org.autofurniture;

import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.api.events.NexoItemsLoadedEvent;
import com.nexomc.nexo.utils.drops.Drop;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Autofurniture extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {

    private boolean nexoReady = false;
    private final List<String> furnitureCache = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (getCommand("autofurniture") != null) {
            getCommand("autofurniture").setExecutor(this);
            getCommand("autofurniture").setTabCompleter(this);
        }

        Bukkit.getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!nexoReady) return;

                for (Entity entity : getServer().getWorlds().get(0).getEntities()) {
                    if (!(entity instanceof ItemDisplay furniture)) continue;
                    if (!NexoFurniture.isFurniture(furniture)) continue;

                    String id = NexoFurniture.furnitureMechanic(furniture).getItemID();

                    List<String> groundList = getConfig().getStringList("groundfurniture");
                    List<String> airList = getConfig().getStringList("airfurniture");
                    List<String> itemsList = getConfig().getStringList("items");

                    if (itemsList.contains(id)) {
                        NexoFurniture.remove(furniture, nearestPlayer(furniture), null);
                        continue;
                    }
                    if (groundList.contains(id)) {
                        if (furniture.getLocation().clone().add(0, -1, 0).getBlock().getType() == Material.AIR) {
                            NexoFurniture.remove(furniture, nearestPlayer(furniture), null);
                        }
                    }
                    if (airList.contains(id)) {
                        if (furniture.getLocation().clone().add(0, 1, 0).getBlock().getType() == Material.AIR) {
                            NexoFurniture.remove(furniture, nearestPlayer(furniture), null);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 10L); // кожні 10 тіків (~0.5 сек)
    }

    @EventHandler
    public void onNexoItemsLoaded(NexoItemsLoadedEvent event) {
        nexoReady = true;

        getLogger().info("=== Nexo Items Loaded ===");
        NexoItems.items().forEach(itemBuilder ->
                getLogger().info("Item: " + itemBuilder.build().getType() +
                        " | ID: " + NexoItems.idFromItem(itemBuilder.build()))
        );

        furnitureCache.clear();
        String[] furnitureIds = NexoFurniture.furnitureIDs();
        if (furnitureIds != null) {
            furnitureCache.addAll(Arrays.asList(furnitureIds));
        }
        getLogger().info("Cached " + furnitureCache.size() + " furniture IDs.");
    }
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!nexoReady) return;

        var block = event.getBlock();
        var blockLoc = block.getLocation();

        for (Entity entity : block.getWorld().getNearbyEntities(blockLoc.clone().add(0.5, 0, 0.5), 1.5, 2.0, 1.5)) {
            if (!(entity instanceof ItemDisplay furniture)) continue;
            if (!NexoFurniture.isFurniture(furniture)) continue;

            String id = NexoFurniture.furnitureMechanic(furniture).getItemID();

            // groundfurniture — якщо блок під меблями зламали
            if (getConfig().getStringList("groundfurniture").contains(id)) {
                var under = furniture.getLocation().clone().add(0, -1, 0).getBlock();
                if (under.equals(block)) {
                    NexoFurniture.remove(furniture, nearestPlayer(furniture), null);
                }
            }

            // airfurniture — якщо блок над меблями зламали
            if (getConfig().getStringList("airfurniture").contains(id)) {
                var above = furniture.getLocation().clone().add(0, 1, 0).getBlock();
                if (above.equals(block)) {
                    NexoFurniture.remove(furniture, nearestPlayer(furniture), null);
                }
            }
        }
    }




    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Цю команду може виконати лише гравець!");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("Використання: /autofurniture reload|add|remove");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                reloadConfig();
                player.sendMessage("§aКонфігурацію перезавантажено!");
                break;

            case "add":
                if (args.length < 3) {
                    player.sendMessage("§cВикористання: /autofurniture add <ground|air> <ID>");
                    break;
                }
                String listType = args[1].toLowerCase();
                String itemID = args[2];

                if (!nexoReady) {
                    player.sendMessage("§cМеблі Nexo ще не завантажені!");
                    break;
                }

                if (!furnitureCache.contains(itemID)) {
                    player.sendMessage("§cЦе не дійсна меблі!");
                    break;
                }

                String configKey = listType.equals("ground") ? "groundfurniture" : "airfurniture";
                List<String> airfurniture = getConfig().getStringList(configKey);
                if (!airfurniture.contains(itemID)) {
                    airfurniture.add(itemID);
                    getConfig().set(configKey, airfurniture);
                    saveConfig();
                    player.sendMessage("§aДодано: " + itemID + " до " + configKey);
                } else {
                    player.sendMessage("§e" + itemID + " вже є в " + configKey);
                }
                break;

            case "remove":
                if (args.length < 3) {
                    player.sendMessage("§cВикористання: /autofurniture remove <ground|air> <ID>");
                    break;
                }
                listType = args[1].toLowerCase();
                itemID = args[2];
                configKey = listType.equals("ground") ? "groundfurniture" : "airfurniture";
                List<String> airfurnitureList = getConfig().getStringList(configKey);
                if (airfurnitureList.remove(itemID)) {
                    getConfig().set(configKey, airfurnitureList);
                    saveConfig();
                    player.sendMessage("§cВидалено: " + itemID + " з " + configKey);
                } else {
                    player.sendMessage("§e" + itemID + " не знайдено у " + configKey);
                }
                break;

            default:
                player.sendMessage("§cНевідома підкоманда!");
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            suggestions.addAll(Arrays.asList("reload", "add", "remove"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
            suggestions.addAll(Arrays.asList("ground", "air"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("add")) {
            if (!nexoReady) {
                suggestions.add("Nexo ще не завантажив меблі!");
            } else {
                String prefix = args[2].toLowerCase();
                for (String id : furnitureCache) {
                    if (id.toLowerCase().startsWith(prefix)) {
                        suggestions.add(id);
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("remove")) {
            String configKey = args[1].equalsIgnoreCase("ground") ? "groundfurniture" : "airfurniture";
            for (String id : getConfig().getStringList(configKey)) {
                if (args[2].isEmpty() || id.toLowerCase().startsWith(args[2].toLowerCase())) {
                    suggestions.add(id);
                }
            }
        }

        return suggestions;
    }

    private Player nearestPlayer(Entity entity) {
        double minDist = Double.MAX_VALUE;
        Player nearest = null;
        for (Player p : entity.getWorld().getPlayers()) {
            double dist = p.getLocation().distanceSquared(entity.getLocation());
            if (dist < minDist) {
                minDist = dist;
                nearest = p;
            }
        }
        return nearest;
    }
}
