package by.nicojerema.smpcore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public class StatsCommand implements CommandExecutor, TabCompleter, Listener {
    private static final int MENU_SIZE = 27;
    private static final int SLOT_TARGET_HEAD = 4;
    private static final int SLOT_DEATHS = 10;
    private static final int SLOT_KILLS = 12;
    private static final int SLOT_COINS = 14;
    private static final int SLOT_RANK = 16;

    private final SMPCorePlugin plugin;

    public StatsCommand(SMPCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smpcore.stats")) {
            sender.sendMessage(TextUtil.colorize("&cYou do not have permission."));
            return true;
        }

        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(TextUtil.colorize("&cUsage: /stats <player>"));
                return true;
            }
            target = (Player) sender;
        } else {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(TextUtil.colorize("&cPlayer not found online."));
                return true;
            }
        }

        if (sender instanceof Player) {
            openStatsMenu((Player) sender, target);
        } else {
            sendStatsText(sender, target);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smpcore.stats")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof StatsMenuHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != top) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        StatsMenuHolder holder = (StatsMenuHolder) top.getHolder();
        if (!player.getUniqueId().equals(holder.getOwnerUuid())) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof StatsMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void openStatsMenu(Player viewer, Player target) {
        StatsMenuHolder holder = new StatsMenuHolder(viewer.getUniqueId());
        Inventory menu = Bukkit.createInventory(
                holder,
                MENU_SIZE,
                TextUtil.colorize("&8Stats: &f" + target.getName())
        );
        holder.setInventory(menu);

        int deaths = target.getStatistic(Statistic.DEATHS);
        int kills = target.getStatistic(Statistic.PLAYER_KILLS);
        String coinsValue = resolveCoinsValue(target);
        String rankDisplay = resolveRankDisplay(target.getUniqueId());

        menu.setItem(SLOT_TARGET_HEAD, createTargetHead(target));
        menu.setItem(SLOT_DEATHS, createValueItem(Material.SKELETON_SKULL, "&cDeaths", String.valueOf(deaths)));
        menu.setItem(SLOT_KILLS, createValueItem(Material.DIAMOND_SWORD, "&bKills", String.valueOf(kills)));
        menu.setItem(SLOT_COINS, createValueItem(Material.EMERALD, "&aCoins", coinsValue));
        menu.setItem(SLOT_RANK, createValueItem(Material.NAME_TAG, "&eRank", TextUtil.colorize(rankDisplay)));

        viewer.openInventory(menu);
    }

    private ItemStack createTargetHead(Player target) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta rawMeta = item.getItemMeta();
        if (!(rawMeta instanceof SkullMeta)) {
            return item;
        }

        SkullMeta meta = (SkullMeta) rawMeta;
        meta.setOwningPlayer(target);
        meta.setDisplayName(TextUtil.colorize("&f" + target.getName()));
        meta.setLore(Collections.singletonList(TextUtil.colorize("&7Live player stats")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createValueItem(Material material, String title, String value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize(title));
            meta.setLore(Collections.singletonList(TextUtil.colorize("&f" + value)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void sendStatsText(CommandSender viewer, Player target) {
        int deaths = target.getStatistic(Statistic.DEATHS);
        int kills = target.getStatistic(Statistic.PLAYER_KILLS);
        String coinsValue = resolveCoinsValue(target);
        String rankDisplay = resolveRankDisplay(target.getUniqueId());

        viewer.sendMessage(TextUtil.colorize("&eStats of &f" + target.getName() + "&e:"));
        viewer.sendMessage(TextUtil.colorize("&7Deaths: &f" + deaths));
        viewer.sendMessage(TextUtil.colorize("&7Kills: &f" + kills));
        viewer.sendMessage(TextUtil.colorize("&7Coins: &f" + coinsValue));
        viewer.sendMessage(TextUtil.colorize("&7Rank: " + rankDisplay));
    }

    private String resolveCoinsValue(Player target) {
        if (plugin.isEconomyEnabled()) {
            long coins = plugin.getPlayerDataStore().getCoins(target.getUniqueId());
            return TextUtil.formatCoins(coins);
        }
        return plugin.getConfig().getString("economy.disabled-placeholder", "Disabled");
    }

    private String resolveRankDisplay(UUID playerUuid) {
        String rankKey = plugin.getPlayerDataStore().getRank(playerUuid);
        String resolved = plugin.resolveRankKey(rankKey);
        String lookup = resolved == null ? rankKey : resolved;

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("ranks." + lookup);
        if (section == null) {
            return "&f" + toDisplayName(lookup);
        }

        String color = section.getString("color", "&7");
        String name = section.getString("name", toDisplayName(lookup));
        return color + name;
    }

    private String toDisplayName(String key) {
        if (key == null || key.trim().isEmpty()) {
            return "Member";
        }

        String lower = key.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        String[] parts = lower.split("\\s+");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            if (part.trim().isEmpty()) {
                continue;
            }
            out.add(Character.toUpperCase(part.charAt(0)) + (part.length() > 1 ? part.substring(1) : ""));
        }
        if (out.isEmpty()) {
            return "Member";
        }
        return String.join(" ", out);
    }

    private static final class StatsMenuHolder implements InventoryHolder {
        private final UUID ownerUuid;
        private Inventory inventory;

        private StatsMenuHolder(UUID ownerUuid) {
            this.ownerUuid = ownerUuid;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private UUID getOwnerUuid() {
            return ownerUuid;
        }
    }
}
