package by.nicojerema.smpcore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SellCommand implements CommandExecutor, TabCompleter, Listener {
    private static final int DEFAULT_SELL_GUI_SIZE = 54;
    private final SMPCorePlugin plugin;

    public SellCommand(SMPCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void ensureMaterialWorthList() {
        boolean generateWorthList = plugin.getConfig().getBoolean("sell.generate-full-worth-list", true);
        if (!generateWorthList) {
            writeWorthJsonFile(plugin.getConfig().getConfigurationSection("sell.material-overrides"));
            return;
        }

        ConfigurationSection overrides = plugin.getConfig().getConfigurationSection("sell.material-overrides");
        if (overrides == null) {
            overrides = plugin.getConfig().createSection("sell.material-overrides");
        }

        boolean overwriteExisting = plugin.getConfig().getBoolean("sell.generated-worth-overwrite-existing", true);
        int changed = 0;

        List<Material> materials = new ArrayList<>();
        for (Material material : Material.values()) {
            if (isSellMaterial(material)) {
                materials.add(material);
            }
        }
        materials.sort(Comparator.comparing(Enum::name));

        for (Material material : materials) {
            boolean forceUpdate = material == Material.ENCHANTED_GOLDEN_APPLE;
            if (!forceUpdate && !overwriteExisting && overrides.contains(material.name())) {
                continue;
            }

            double generatedWorth = computeGeneratedWorth(material);
            if (!overrides.contains(material.name())
                    || Math.abs(overrides.getDouble(material.name(), -1.0d) - generatedWorth) > 0.00001d) {
                overrides.set(material.name(), generatedWorth);
                changed++;
            }
        }

        if (changed > 0) {
            plugin.saveConfig();
            plugin.getLogger().info(
                    "Updated worth values for " + changed + " Minecraft items in sell.material-overrides."
            );
        }

        writeWorthJsonFile(overrides);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextUtil.colorize("&cOnly players can use this command."));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("smpcore.sell")) {
            player.sendMessage(TextUtil.colorize("&cYou do not have permission."));
            return true;
        }
        if (!plugin.isEconomyEnabled()) {
            player.sendMessage(plugin.getEconomyDisabledMessage());
            return true;
        }

        String mode = args.length == 0 ? "open" : args[0].toLowerCase(Locale.ROOT);
        switch (mode) {
            case "open":
            case "menu":
            case "gui":
                openSellMenu(player);
                return true;
            case "hand":
                sellHand(player);
                return true;
            case "all":
                sellAll(player);
                return true;
            default:
                player.sendMessage(TextUtil.colorize("&cUsage: /sell [open|hand|all]"));
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smpcore.sell")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            if ("open".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                out.add("open");
            }
            if ("hand".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                out.add("hand");
            }
            if ("all".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                out.add("all");
            }
            return out;
        }

        return Collections.emptyList();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof SellInventoryHolder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        processSellMenuClose(player, inventory);
    }

    private void openSellMenu(Player player) {
        int size = normalizeSellSize(plugin.getConfig().getInt("sell.gui-size", DEFAULT_SELL_GUI_SIZE));
        String titleRaw = plugin.getConfig().getString("sell.gui-title", "&8Sell Items");
        String title = TextUtil.colorize(plugin.getSidebarService().replacePlaceholders(player, titleRaw));

        SellInventoryHolder holder = new SellInventoryHolder();
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inventory);

        player.openInventory(inventory);
        player.sendMessage(TextUtil.colorize("&7Place items in the menu, then close it to sell."));
    }

    private int normalizeSellSize(int rawSize) {
        int clamped = Math.max(9, Math.min(54, rawSize));
        if (clamped % 9 == 0) {
            return clamped;
        }
        return (clamped / 9) * 9;
    }

    private void processSellMenuClose(Player player, Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        boolean hasItems = false;
        for (ItemStack stack : contents) {
            if (stack != null && !stack.getType().isAir()) {
                hasItems = true;
                break;
            }
        }

        if (!hasItems) {
            return;
        }

        if (!plugin.isEconomyEnabled()) {
            int returned = returnAllItems(player, contents);
            player.sendMessage(plugin.getEconomyDisabledMessage());
            if (returned > 0) {
                player.sendMessage(TextUtil.colorize("&eReturned &f" + returned + " item(s) &ebecause economy is disabled."));
            }
            return;
        }

        long total = 0L;
        int soldStacks = 0;
        int soldItems = 0;
        int returnedItems = 0;

        for (ItemStack stack : contents) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }

            Material material = stack.getType();
            if (!isSellable(material)) {
                returnedItems += stack.getAmount();
                returnStack(player, stack);
                continue;
            }

            long value = calculateStackSellValue(material, stack.getAmount());
            if (value <= 0L) {
                returnedItems += stack.getAmount();
                returnStack(player, stack);
                continue;
            }

            try {
                total = Math.addExact(total, value);
            } catch (ArithmeticException ex) {
                total = Long.MAX_VALUE;
            }
            soldItems += stack.getAmount();
            soldStacks++;
        }

        if (total > 0L) {
            depositCoins(player, total);
            player.sendMessage(TextUtil.colorize(
                    "&aSold &f" + soldStacks + " stacks (" + soldItems + " items) &afor &e"
                            + TextUtil.formatCoins(total) + " " + getCoinsName() + "&a."
            ));
        } else {
            player.sendMessage(TextUtil.colorize("&cNo sellable items found in the sell menu."));
        }

        if (returnedItems > 0) {
            player.sendMessage(TextUtil.colorize("&eReturned &f" + returnedItems + " item(s) &ethat cannot be sold."));
        }
    }

    private int returnAllItems(Player player, ItemStack[] contents) {
        int total = 0;
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            total += stack.getAmount();
            returnStack(player, stack);
        }
        return total;
    }

    private void returnStack(Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack.clone());
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void sellHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage(TextUtil.colorize("&cHold an item in your main hand."));
            return;
        }

        Material material = hand.getType();
        if (!isSellable(material)) {
            player.sendMessage(TextUtil.colorize("&cThis item cannot be sold."));
            return;
        }

        long total = calculateStackSellValue(material, hand.getAmount());
        if (total <= 0L) {
            player.sendMessage(TextUtil.colorize("&cThis item has no sell value."));
            return;
        }

        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        depositCoins(player, total);

        String rarity = resolveRarityName(material);
        player.sendMessage(TextUtil.colorize(
                "&aSold &fx" + hand.getAmount() + " " + toFriendlyMaterialName(material)
                        + " &a(" + rarity + ") for &e" + TextUtil.formatCoins(total) + " " + getCoinsName() + "&a."
        ));
    }

    private void sellAll(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();

        long total = 0L;
        int soldStacks = 0;
        int soldItems = 0;

        for (int i = 0; i < storage.length; i++) {
            ItemStack stack = storage[i];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }

            Material material = stack.getType();
            if (!isSellable(material)) {
                continue;
            }

            long value = calculateStackSellValue(material, stack.getAmount());
            if (value <= 0L) {
                continue;
            }

            total += value;
            soldItems += stack.getAmount();
            soldStacks++;
            storage[i] = null;
        }

        if (total <= 0L) {
            player.sendMessage(TextUtil.colorize("&cNo sellable items found in your inventory."));
            return;
        }

        inventory.setStorageContents(storage);
        depositCoins(player, total);

        player.sendMessage(TextUtil.colorize(
                "&aSold &f" + soldStacks + " stacks (" + soldItems + " items) &afor &e"
                        + TextUtil.formatCoins(total) + " " + getCoinsName() + "&a."
        ));
    }

    private void depositCoins(Player player, long amount) {
        long current = plugin.getPlayerDataStore().getCoins(player.getUniqueId());
        long updated;

        try {
            updated = Math.addExact(current, amount);
        } catch (ArithmeticException ex) {
            updated = Long.MAX_VALUE;
        }

        plugin.getPlayerDataStore().setCoins(player.getUniqueId(), updated);
        plugin.getPlayerDataStore().save();
        plugin.getSidebarService().refreshAll();
    }

    private long calculateStackSellValue(Material material, int amount) {
        double unitPrice = getUnitSellPrice(material);
        if (unitPrice <= 0.0d || amount <= 0) {
            return 0L;
        }

        double stackBonusMultiplier = getStackBonusMultiplier(amount);
        double totalPrice = unitPrice * amount * stackBonusMultiplier;

        long rounded = Math.round(totalPrice);
        if (rounded <= 0L) {
            return 1L;
        }
        return rounded;
    }

    private double getUnitSellPrice(Material material) {
        double unitPrice;
        boolean useDefaultOnly = plugin.getConfig().getBoolean("sell.use-default-price-only", true);
        if (useDefaultOnly) {
            unitPrice = Math.max(0.0d, plugin.getConfig().getDouble("sell.default-price-per-item", 4.0d));
        } else {
            ConfigurationSection overrideSection = plugin.getConfig().getConfigurationSection("sell.material-overrides");
            if (overrideSection != null && overrideSection.contains(material.name())) {
                unitPrice = Math.max(0.0d, overrideSection.getDouble(material.name(), 0.0d));
            } else {
                double base = Math.max(0.0d, plugin.getConfig().getDouble("sell.default-price-per-item", 4.0d));
                String rarity = resolveRarityName(material);
                double commonMultiplier = plugin.getConfig().getDouble("sell.rarity-multipliers.COMMON", 1.0d);
                double multiplier = plugin.getConfig().getDouble("sell.rarity-multipliers." + rarity, commonMultiplier);

                unitPrice = Math.max(0.0d, base * multiplier);
            }
        }

        unitPrice = applyShopPriceCap(material, unitPrice);
        return Math.max(0.0d, unitPrice);
    }

    private double getStackBonusMultiplier(int amount) {
        if (amount <= 1) {
            return 1.0d;
        }

        boolean enabled = plugin.getConfig().getBoolean("sell.stack-bonus.enabled", true);
        if (!enabled) {
            return 1.0d;
        }

        double fullStackMultiplier = Math.max(1.0d, plugin.getConfig().getDouble("sell.stack-bonus.full-stack-multiplier", 1.25d));
        int maxStackAmount = Math.max(2, plugin.getConfig().getInt("sell.stack-bonus.max-stack-amount", 64));
        int cappedAmount = Math.min(amount, maxStackAmount);

        double progress = (cappedAmount - 1.0d) / (maxStackAmount - 1.0d);
        return 1.0d + ((fullStackMultiplier - 1.0d) * progress);
    }

    private double applyShopPriceCap(Material material, double currentUnitSellPrice) {
        boolean enabled = plugin.getConfig().getBoolean("sell.shop-price-cap.enabled", true);
        if (!enabled) {
            return currentUnitSellPrice;
        }

        Double shopUnitPrice = getShopUnitPrice(material);
        if (shopUnitPrice == null) {
            return currentUnitSellPrice;
        }

        double multiplier = plugin.getConfig().getDouble("sell.shop-price-cap.max-sell-multiplier-of-shop-price", 0.6d);
        multiplier = Math.max(0.0d, multiplier);

        double cap = shopUnitPrice * multiplier;
        if (currentUnitSellPrice > cap) {
            return cap;
        }
        return currentUnitSellPrice;
    }

    private Double getShopUnitPrice(Material material) {
        if (material == null) {
            return null;
        }

        ConfigurationSection items = plugin.getConfig().getConfigurationSection("shop.items");
        if (items == null) {
            return null;
        }

        ConfigurationSection item = items.getConfigurationSection(material.name());
        if (item == null) {
            return null;
        }

        long totalPrice = Math.max(0L, item.getLong("price", 0L));
        int amount = Math.max(1, item.getInt("amount", 1));
        return totalPrice / (double) amount;
    }

    private double computeGeneratedWorth(Material material) {
        if (!isSellable(material)) {
            return 0.0d;
        }

        if (plugin.getConfig().getBoolean("sell.use-default-price-only", true)) {
            double base = Math.max(0.0d, plugin.getConfig().getDouble("sell.default-price-per-item", 4.0d));
            base = applyShopPriceCap(material, base);
            return Math.round(base * 100.0d) / 100.0d;
        }

        Double specialWorth = getGeneratedSpecialWorth(material);
        if (specialWorth != null) {
            return specialWorth;
        }

        String rarity = resolveRarityName(material);
        double commonWorth = Math.max(0.1d, plugin.getConfig().getDouble("sell.generated-worth-by-rarity.COMMON", 15.0d));
        double rarityWorth = plugin.getConfig().getDouble("sell.generated-worth-by-rarity." + rarity, commonWorth);

        double tierBoost = resolveTierBoost(material);
        double stackBoost = material.getMaxStackSize() == 1 ? 1.6d : 1.0d;
        double raw = Math.max(0.1d, rarityWorth * tierBoost * stackBoost);
        raw = applyShopPriceCap(material, raw);

        return Math.round(raw * 100.0d) / 100.0d;
    }

    private Double getGeneratedSpecialWorth(Material material) {
        if (material == null) {
            return null;
        }

        String path = "sell.generated-special-worth." + material.name();
        if (plugin.getConfig().contains(path)) {
            return Math.max(0.0d, plugin.getConfig().getDouble(path, 0.0d));
        }
        if (material == Material.ENCHANTED_GOLDEN_APPLE) {
            return 300000.0d;
        }
        return null;
    }

    private double resolveTierBoost(Material material) {
        String name = material.name();

        if (name.contains("ENCHANTED_GOLDEN_APPLE")) {
            return 20.0d;
        }
        if (name.contains("NETHERITE")) {
            return 18.0d;
        }
        if (name.contains("ELYTRA") || name.contains("TOTEM_OF_UNDYING")) {
            return 22.0d;
        }
        if (name.contains("NETHER_STAR") || name.contains("BEACON") || name.contains("DRAGON_EGG")) {
            return 16.0d;
        }
        if (name.contains("SHULKER_BOX") || name.contains("ANCIENT_DEBRIS")) {
            return 10.0d;
        }
        if (name.contains("DIAMOND")) {
            return 8.0d;
        }
        if (name.contains("EMERALD")) {
            return 7.0d;
        }
        if (name.contains("GOLDEN_APPLE") || name.contains("EXPERIENCE_BOTTLE")) {
            return 5.0d;
        }
        if (name.contains("SPAWN_EGG")) {
            return 4.0d;
        }
        if (name.contains("MUSIC_DISC")) {
            return 3.0d;
        }
        if (name.contains("IRON")) {
            return 3.0d;
        }
        if (name.contains("COPPER")) {
            return 2.0d;
        }

        return 1.0d;
    }

    private void writeWorthJsonFile(ConfigurationSection overrides) {
        boolean writeJson = plugin.getConfig().getBoolean("sell.write-worth-json", true);
        if (!writeJson) {
            return;
        }

        String configuredFileName = plugin.getConfig().getString("sell.worth-json-file", "item-worths.json");
        String fileName = (configuredFileName == null || configuredFileName.trim().isEmpty())
                ? "item-worths.json"
                : configuredFileName.trim();

        File jsonFile = new File(plugin.getDataFolder(), fileName);
        File parent = jsonFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create folder for item worth JSON: " + parent.getPath());
            return;
        }

        List<Material> materials = new ArrayList<>();
        for (Material material : Material.values()) {
            if (isSellMaterial(material)) {
                materials.add(material);
            }
        }
        materials.sort(Comparator.comparing(Enum::name));

        StringBuilder out = new StringBuilder(Math.max(8192, materials.size() * 80));
        out.append("{\n");
        out.append("  \"generatedAtUtc\": \"").append(escapeJson(Instant.now().toString())).append("\",\n");
        out.append("  \"totalItems\": ").append(materials.size()).append(",\n");
        double enchantedWorth = getUnitSellPrice(Material.ENCHANTED_GOLDEN_APPLE);
        out.append("  \"enchantedGoldenAppleWorth\": ").append(formatWorthNumber(enchantedWorth)).append(",\n");
        out.append("  \"items\": [\n");

        for (int i = 0; i < materials.size(); i++) {
            Material material = materials.get(i);
            String rarity = resolveRarityName(material);
            double worth = getUnitSellPrice(material);
            boolean sellable = isSellable(material);

            out.append("    {");
            out.append("\"material\": \"").append(material.name()).append("\", ");
            out.append("\"rarity\": \"").append(rarity).append("\", ");
            out.append("\"worth\": ").append(formatWorthNumber(worth)).append(", ");
            out.append("\"sellable\": ").append(sellable);
            out.append("}");

            if (i < materials.size() - 1) {
                out.append(',');
            }
            out.append('\n');
        }

        out.append("  ]\n");
        out.append("}\n");

        try {
            Files.writeString(jsonFile.toPath(), out.toString(), StandardCharsets.UTF_8);
            plugin.getLogger().info("Wrote item worth JSON to " + jsonFile.getPath());
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to write item worth JSON: " + ex.getMessage());
        }
    }

    private String formatWorthNumber(double worth) {
        long rounded = Math.round(worth);
        if (Math.abs(worth - rounded) < 0.00001d) {
            return String.valueOf(rounded);
        }
        return String.format(Locale.ROOT, "%.2f", worth);
    }

    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean isSellMaterial(Material material) {
        if (material == null || material.isAir()) {
            return false;
        }
        if (material.name().startsWith("LEGACY_")) {
            return false;
        }

        try {
            Method isItem = Material.class.getMethod("isItem");
            Object result = isItem.invoke(material);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return true;
    }

    private boolean isSellable(Material material) {
        if (material == null || material.isAir()) {
            return false;
        }

        List<String> blacklist = plugin.getConfig().getStringList("sell.blacklist");
        for (String blocked : blacklist) {
            if (material.name().equalsIgnoreCase(blocked)) {
                return false;
            }
        }

        return true;
    }

    private String resolveRarityName(Material material) {
        try {
            Method method = Material.class.getMethod("getRarity");
            Object rarity = method.invoke(material);
            if (rarity instanceof Enum) {
                return ((Enum<?>) rarity).name().toUpperCase(Locale.ROOT);
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return "COMMON";
    }

    private String toFriendlyMaterialName(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
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
        return out.toString();
    }

    private String getCoinsName() {
        return plugin.getConfig().getString("coins-display-name", "Coins");
    }

    private static final class SellInventoryHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }
}
