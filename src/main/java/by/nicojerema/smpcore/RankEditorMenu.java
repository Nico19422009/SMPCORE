package by.nicojerema.smpcore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class RankEditorMenu implements Listener {
    private static final String MENU_TITLE = "&8Rank Editor";
    private static final int MIN_MENU_SIZE = 9;
    private static final int MAX_MENU_SIZE = 54;
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("(?i)(&[0-9A-FK-OR])+");

    private final SMPCorePlugin plugin;
    private final Map<UUID, PendingEdit> pendingEdits;

    public RankEditorMenu(SMPCorePlugin plugin) {
        this.plugin = plugin;
        this.pendingEdits = new ConcurrentHashMap<>();
    }

    public void openMenu(Player admin) {
        ConfigurationSection ranksSection = plugin.getConfig().getConfigurationSection("ranks");
        if (ranksSection == null || ranksSection.getKeys(false).isEmpty()) {
            admin.sendMessage(TextUtil.colorize("&cNo ranks configured in config.yml"));
            return;
        }

        List<RankEntry> ranks = loadRanks(ranksSection);
        int size = normalizeMenuSize(ranks.size());
        RankMenuHolder holder = new RankMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, size, TextUtil.colorize(MENU_TITLE));
        holder.setInventory(inventory);

        int visible = Math.min(size, ranks.size());
        for (int i = 0; i < visible; i++) {
            RankEntry rank = ranks.get(i);
            inventory.setItem(i, createRankIcon(rank));
            holder.putRank(i, rank.getConfigKey());
        }

        if (ranks.size() > size) {
            int infoSlot = size - 1;
            inventory.setItem(infoSlot, createInfoItem("&eToo many ranks", Collections.singletonList(
                    "&7Showing first " + size + " ranks only."
            )));
        }

        admin.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof RankMenuHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != top) {
            return;
        }

        RankMenuHolder holder = (RankMenuHolder) top.getHolder();
        String rankKey = holder.getRank(event.getSlot());
        if (rankKey == null) {
            return;
        }

        Player admin = (Player) event.getWhoClicked();
        ClickType click = event.getClick();
        if (click.isLeftClick()) {
            startNameEdit(admin, rankKey);
            return;
        }
        if (click.isRightClick()) {
            startColorEdit(admin, rankKey);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof RankMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PendingEdit pending = pendingEdits.remove(uuid);
        if (pending == null) {
            return;
        }

        event.setCancelled(true);
        String input = event.getMessage();
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                applyPendingEdit(player, pending, input == null ? "" : input.trim());
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingEdits.remove(event.getPlayer().getUniqueId());
    }

    private void startNameEdit(Player admin, String rankKey) {
        pendingEdits.put(admin.getUniqueId(), new PendingEdit(rankKey, EditType.NAME));
        admin.closeInventory();
        admin.sendMessage(TextUtil.colorize("&eEditing rank &f" + rankKey + "&e."));
        admin.sendMessage(TextUtil.colorize("&7Type the new rank name in chat, or type &ccancel&7."));
    }

    private void startColorEdit(Player admin, String rankKey) {
        pendingEdits.put(admin.getUniqueId(), new PendingEdit(rankKey, EditType.COLOR));
        admin.closeInventory();
        admin.sendMessage(TextUtil.colorize("&eEditing rank color for &f" + rankKey + "&e."));
        admin.sendMessage(TextUtil.colorize("&7Use color code (&6, &a, etc.) or color name (gold, green)."));
        admin.sendMessage(TextUtil.colorize("&7Type &ccancel &7to abort."));
    }

    private void applyPendingEdit(Player admin, PendingEdit pending, String input) {
        if (!admin.isOnline()) {
            return;
        }

        if (input.equalsIgnoreCase("cancel")) {
            admin.sendMessage(TextUtil.colorize("&cRank edit cancelled."));
            openMenu(admin);
            return;
        }

        String basePath = "ranks." + pending.getRankKey();
        if (!plugin.getConfig().isConfigurationSection(basePath)) {
            admin.sendMessage(TextUtil.colorize("&cRank no longer exists: &f" + pending.getRankKey()));
            openMenu(admin);
            return;
        }

        if (pending.getEditType() == EditType.NAME) {
            if (input.isEmpty()) {
                pendingEdits.put(admin.getUniqueId(), pending);
                admin.sendMessage(TextUtil.colorize("&cName cannot be empty. Type again or &ccancel&c."));
                return;
            }

            plugin.getConfig().set(basePath + ".name", input);
            plugin.saveConfig();
            plugin.getSidebarService().refreshAll();
            admin.sendMessage(TextUtil.colorize("&aUpdated rank name of &f" + pending.getRankKey() + " &ato &f" + input + "&a."));
            openMenu(admin);
            return;
        }

        String normalizedColor = normalizeColorInput(input);
        if (normalizedColor == null) {
            pendingEdits.put(admin.getUniqueId(), pending);
            admin.sendMessage(TextUtil.colorize("&cInvalid color. Use &f&6 &cor color names like &fgold&c."));
            admin.sendMessage(TextUtil.colorize("&7Type &ccancel &7to abort."));
            return;
        }

        plugin.getConfig().set(basePath + ".color", normalizedColor);
        plugin.saveConfig();
        plugin.getSidebarService().refreshAll();
        admin.sendMessage(TextUtil.colorize("&aUpdated rank color of &f" + pending.getRankKey() + " &ato " + normalizedColor + normalizedColor + "&lSample&a."));
        openMenu(admin);
    }

    private List<RankEntry> loadRanks(ConfigurationSection ranksSection) {
        List<RankEntry> entries = new ArrayList<>();
        for (String key : ranksSection.getKeys(false)) {
            String normalized = key.toLowerCase(Locale.ROOT);
            String name = ranksSection.getString(key + ".name", toDisplayName(key));
            String color = ranksSection.getString(key + ".color", "&7");
            int weight = ranksSection.getInt(key + ".weight", 100);

            entries.add(new RankEntry(key, normalized, name, color, weight));
        }

        entries.sort(Comparator.comparingInt(RankEntry::getWeight).thenComparing(RankEntry::getKey));
        return entries;
    }

    private ItemStack createRankIcon(RankEntry rank) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize(rank.getColor() + rank.getName()));

            List<String> lore = new ArrayList<>();
            lore.add(TextUtil.colorize("&7Key: &f" + rank.getConfigKey()));
            lore.add(TextUtil.colorize("&7Weight: &f" + rank.getWeight()));
            lore.add(TextUtil.colorize("&7Color: " + rank.getColor() + "&lSample"));
            lore.add(TextUtil.colorize("&7Name: &f" + rank.getName()));
            lore.add(TextUtil.colorize("&8"));
            lore.add(TextUtil.colorize("&eLeft click: &fEdit name"));
            lore.add(TextUtil.colorize("&bRight click: &fEdit color"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createInfoItem(String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize(name));
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(TextUtil.colorize(line));
            }
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private int normalizeMenuSize(int rankCount) {
        int desired = Math.max(MIN_MENU_SIZE, rankCount);
        int rows = (int) Math.ceil(desired / 9.0d);
        rows = Math.max(1, Math.min(6, rows));
        return Math.min(MAX_MENU_SIZE, rows * 9);
    }

    private String normalizeColorInput(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String candidate = trimmed.replace('\u00A7', '&').toLowerCase(Locale.ROOT);
        if (candidate.length() == 1 && isLegacyColorCode(candidate.charAt(0))) {
            return "&" + candidate;
        }
        if (candidate.length() == 2 && candidate.charAt(0) == '&' && isLegacyColorCode(candidate.charAt(1))) {
            return candidate;
        }
        if (COLOR_CODE_PATTERN.matcher(candidate).matches()) {
            return candidate;
        }

        return namedColorCode(candidate);
    }

    private boolean isLegacyColorCode(char code) {
        return "0123456789abcdefklmnor".indexOf(Character.toLowerCase(code)) >= 0;
    }

    private String namedColorCode(String input) {
        String normalized = input.toLowerCase(Locale.ROOT).replace(" ", "_").replace("-", "_");
        Map<String, String> named = new HashMap<>();
        named.put("black", "&0");
        named.put("dark_blue", "&1");
        named.put("dark_green", "&2");
        named.put("dark_aqua", "&3");
        named.put("dark_red", "&4");
        named.put("dark_purple", "&5");
        named.put("gold", "&6");
        named.put("gray", "&7");
        named.put("dark_gray", "&8");
        named.put("blue", "&9");
        named.put("green", "&a");
        named.put("aqua", "&b");
        named.put("red", "&c");
        named.put("light_purple", "&d");
        named.put("yellow", "&e");
        named.put("white", "&f");
        named.put("magic", "&k");
        named.put("bold", "&l");
        named.put("strikethrough", "&m");
        named.put("underline", "&n");
        named.put("underlined", "&n");
        named.put("italic", "&o");
        named.put("reset", "&r");
        return named.get(normalized);
    }

    private String toDisplayName(String key) {
        String[] parts = key.toLowerCase(Locale.ROOT).replace('-', ' ').replace('_', ' ').split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.trim().isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.length() == 0 ? "Member" : out.toString();
    }

    private enum EditType {
        NAME,
        COLOR
    }

    private static final class PendingEdit {
        private final String rankKey;
        private final EditType editType;

        private PendingEdit(String rankKey, EditType editType) {
            this.rankKey = rankKey;
            this.editType = editType;
        }

        private String getRankKey() {
            return rankKey;
        }

        private EditType getEditType() {
            return editType;
        }
    }

    private static final class RankEntry {
        private final String configKey;
        private final String key;
        private final String name;
        private final String color;
        private final int weight;

        private RankEntry(String configKey, String key, String name, String color, int weight) {
            this.configKey = configKey;
            this.key = key;
            this.name = name;
            this.color = color;
            this.weight = weight;
        }

        private String getConfigKey() {
            return configKey;
        }

        private String getKey() {
            return key;
        }

        private String getName() {
            return name;
        }

        private String getColor() {
            return color;
        }

        private int getWeight() {
            return weight;
        }
    }

    private static final class RankMenuHolder implements InventoryHolder {
        private final Map<Integer, String> slotToRank;
        private Inventory inventory;

        private RankMenuHolder() {
            this.slotToRank = new HashMap<>();
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private void putRank(int slot, String rankKey) {
            slotToRank.put(slot, rankKey);
        }

        private String getRank(int slot) {
            return slotToRank.get(slot);
        }
    }
}
