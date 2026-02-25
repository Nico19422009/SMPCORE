package by.nicojerema.smpcore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ShopCommand implements CommandExecutor, TabCompleter, Listener {
    private static final int CONFIRM_SIZE = 27;
    private static final int CONFIRM_ACCEPT_SLOT = 11;
    private static final int CONFIRM_INFO_SLOT = 13;
    private static final int CONFIRM_CANCEL_SLOT = 15;

    private final SMPCorePlugin plugin;
    private final NamespacedKey shopDisplayItemKey;

    public ShopCommand(SMPCorePlugin plugin) {
        this.plugin = plugin;
        this.shopDisplayItemKey = new NamespacedKey(plugin, "shop_display_item");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "shop":
                return handleShopCommand(sender);
            case "shopedit":
                return handleShopEditCommand(sender, args);
            default:
                return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!name.equals("shopedit")) {
            return Collections.emptyList();
        }

        if (!sender.hasPermission("smpcore.shopedit")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("setprice");
            subs.add("setslot");
            subs.add("setamount");
            subs.add("remove");
            subs.add("open");
            subs.add("reload");
            return subs.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("setprice")
                || args[0].equalsIgnoreCase("setslot")
                || args[0].equalsIgnoreCase("setamount")
                || args[0].equalsIgnoreCase("remove"))) {
            return completeMaterialNames(args[1]);
        }

        return Collections.emptyList();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder rawHolder = top.getHolder();
        if (!(rawHolder instanceof ShopInventoryHolder) && !(rawHolder instanceof ConfirmInventoryHolder)) {
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
        if (rawHolder instanceof ShopInventoryHolder) {
            ShopInventoryHolder holder = (ShopInventoryHolder) rawHolder;
            ShopItem clicked = holder.getItem(event.getSlot());
            if (clicked == null) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        return;
                    }

                    Inventory currentTop = player.getOpenInventory().getTopInventory();
                    if (!(currentTop.getHolder() instanceof ShopInventoryHolder)) {
                        return;
                    }
                    openConfirmMenu(player, clicked);
                }
            });
            return;
        }

        ConfirmInventoryHolder confirmHolder = (ConfirmInventoryHolder) rawHolder;
        handleConfirmClick(player, confirmHolder, event.getSlot());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof ShopInventoryHolder || top.getHolder() instanceof ConfirmInventoryHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();
        if (!(holder instanceof ShopInventoryHolder) && !(holder instanceof ConfirmInventoryHolder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        removeLeakedShopDisplayItems(player);
    }

    private boolean handleShopCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextUtil.colorize("&cOnly players can use this command."));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("smpcore.shop")) {
            player.sendMessage(TextUtil.colorize("&cYou do not have permission."));
            return true;
        }
        if (!plugin.isEconomyEnabled()) {
            player.sendMessage(plugin.getEconomyDisabledMessage());
            return true;
        }

        openShop(player);
        return true;
    }

    private boolean handleShopEditCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smpcore.shopedit")) {
            sender.sendMessage(TextUtil.colorize("&cYou do not have permission."));
            return true;
        }

        if (args.length == 0) {
            sendShopEditUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "setprice":
                return handleSetPrice(sender, args);
            case "setslot":
                return handleSetSlot(sender, args);
            case "setamount":
                return handleSetAmount(sender, args);
            case "remove":
                return handleRemove(sender, args);
            case "reload":
                plugin.reloadPlugin();
                sender.sendMessage(TextUtil.colorize("&aSMPCORE reloaded. Shop config updated."));
                return true;
            case "open":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(TextUtil.colorize("&cOnly players can use /shopedit open."));
                    return true;
                }
                openShop((Player) sender);
                return true;
            default:
                sendShopEditUsage(sender);
                return true;
        }
    }

    private boolean handleSetPrice(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /shopedit setprice <material> <price>"));
            return true;
        }

        Material material = parseMaterial(args[1]);
        if (material == null) {
            sender.sendMessage(TextUtil.colorize("&cUnknown material: &f" + args[1]));
            return true;
        }

        long price;
        try {
            price = Long.parseLong(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(TextUtil.colorize("&cPrice must be a number."));
            return true;
        }
        if (price < 0L) {
            sender.sendMessage(TextUtil.colorize("&cPrice cannot be negative."));
            return true;
        }

        ensureShopItemDefaults(material);
        String path = "shop.items." + material.name() + ".price";
        plugin.getConfig().set(path, price);
        plugin.saveConfig();

        sender.sendMessage(TextUtil.colorize("&aSet shop price for &f" + material.name() + " &ato &e" + price + "&a."));
        return true;
    }

    private boolean handleSetSlot(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /shopedit setslot <material> <slot>"));
            return true;
        }

        Material material = parseMaterial(args[1]);
        if (material == null) {
            sender.sendMessage(TextUtil.colorize("&cUnknown material: &f" + args[1]));
            return true;
        }

        int slot;
        try {
            slot = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(TextUtil.colorize("&cSlot must be a number."));
            return true;
        }

        int size = normalizeShopSize(plugin.getConfig().getInt("shop.size", 27));
        if (slot < 0 || slot >= size) {
            sender.sendMessage(TextUtil.colorize("&cSlot must be between 0 and " + (size - 1) + "."));
            return true;
        }

        ensureShopItemDefaults(material);
        plugin.getConfig().set("shop.items." + material.name() + ".slot", slot);
        plugin.saveConfig();

        sender.sendMessage(TextUtil.colorize("&aSet shop slot for &f" + material.name() + " &ato &e" + slot + "&a."));
        return true;
    }

    private boolean handleSetAmount(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /shopedit setamount <material> <amount>"));
            return true;
        }

        Material material = parseMaterial(args[1]);
        if (material == null) {
            sender.sendMessage(TextUtil.colorize("&cUnknown material: &f" + args[1]));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(TextUtil.colorize("&cAmount must be a number."));
            return true;
        }

        if (amount < 1 || amount > material.getMaxStackSize()) {
            sender.sendMessage(TextUtil.colorize("&cAmount must be between 1 and " + material.getMaxStackSize() + "."));
            return true;
        }

        ensureShopItemDefaults(material);
        plugin.getConfig().set("shop.items." + material.name() + ".amount", amount);
        plugin.saveConfig();

        sender.sendMessage(TextUtil.colorize("&aSet shop amount for &f" + material.name() + " &ato &e" + amount + "&a."));
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /shopedit remove <material>"));
            return true;
        }

        Material material = parseMaterial(args[1]);
        if (material == null) {
            sender.sendMessage(TextUtil.colorize("&cUnknown material: &f" + args[1]));
            return true;
        }

        String path = "shop.items." + material.name();
        if (!plugin.getConfig().isConfigurationSection(path)) {
            sender.sendMessage(TextUtil.colorize("&cThat material is not in the shop."));
            return true;
        }

        plugin.getConfig().set(path, null);
        plugin.saveConfig();
        sender.sendMessage(TextUtil.colorize("&aRemoved &f" + material.name() + " &afrom the shop."));
        return true;
    }

    private void openShop(Player player) {
        int size = normalizeShopSize(plugin.getConfig().getInt("shop.size", 27));
        String titleRaw = plugin.getConfig().getString("shop.title", "&6&lShop");
        String titleParsed = plugin.getSidebarService().replacePlaceholders(player, titleRaw);
        String title = TextUtil.colorize(titleParsed);

        ShopInventoryHolder holder = new ShopInventoryHolder();
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inventory);

        List<ShopItem> items = loadShopItems();
        for (ShopItem shopItem : items) {
            if (shopItem.getSlot() < 0 || shopItem.getSlot() >= size) {
                continue;
            }

            ItemStack stack = new ItemStack(shopItem.getMaterial(), shopItem.getAmount());
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                markAsShopDisplayItem(meta);
                String itemDisplayName = shopItem.getName();
                if (itemDisplayName == null || itemDisplayName.trim().isEmpty()) {
                    itemDisplayName = "&f" + toFriendlyMaterialName(shopItem.getMaterial());
                }
                meta.setDisplayName(TextUtil.colorize(applyShopPlaceholders(player, itemDisplayName, shopItem)));

                List<String> configuredLore = shopItem.getLore();
                List<String> lore = new ArrayList<>();
                if (configuredLore != null && !configuredLore.isEmpty()) {
                    for (String loreLine : configuredLore) {
                        lore.add(TextUtil.colorize(applyShopPlaceholders(player, loreLine, shopItem)));
                    }
                } else {
                    lore.add(TextUtil.colorize("&7Buy: &fx" + shopItem.getAmount() + " " + toFriendlyMaterialName(shopItem.getMaterial())));
                    lore.add(TextUtil.colorize("&7Price: &e" + TextUtil.formatCoins(shopItem.getPrice()) + " " + plugin.getConfig().getString("coins-display-name", "Coins")));
                    lore.add(TextUtil.colorize("&eClick to buy"));
                }
                meta.setLore(lore);
                stack.setItemMeta(meta);
            }

            inventory.setItem(shopItem.getSlot(), stack);
            holder.putItem(shopItem.getSlot(), shopItem);
        }

        player.openInventory(inventory);
    }

    private void openConfirmMenu(Player player, ShopItem item) {
        String titleRaw = plugin.getConfig().getString("shop.confirm-title", "&8Confirm Purchase");
        String title = TextUtil.colorize(plugin.getSidebarService().replacePlaceholders(player, titleRaw));

        ConfirmInventoryHolder holder = new ConfirmInventoryHolder(item);
        Inventory inventory = Bukkit.createInventory(holder, CONFIRM_SIZE, title);
        holder.setInventory(inventory);

        ItemStack info = new ItemStack(item.getMaterial(), item.getAmount());
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            markAsShopDisplayItem(infoMeta);
            infoMeta.setDisplayName(TextUtil.colorize("&f" + toFriendlyMaterialName(item.getMaterial())));
            List<String> lore = new ArrayList<>();
            lore.add(TextUtil.colorize("&7Amount: &fx" + item.getAmount()));
            lore.add(TextUtil.colorize("&7Price: &e" + TextUtil.formatCoins(item.getPrice()) + " " + plugin.getConfig().getString("coins-display-name", "Coins")));
            lore.add(TextUtil.colorize("&7Your coins: &e" + TextUtil.formatCoins(plugin.getPlayerDataStore().getCoins(player.getUniqueId()))));
            infoMeta.setLore(lore);
            info.setItemMeta(infoMeta);
        }
        inventory.setItem(CONFIRM_INFO_SLOT, info);

        inventory.setItem(CONFIRM_ACCEPT_SLOT, createConfirmButton(item, true));
        inventory.setItem(CONFIRM_CANCEL_SLOT, createConfirmButton(item, false));

        player.openInventory(inventory);
    }

    private void handleConfirmClick(Player player, ConfirmInventoryHolder holder, int slot) {
        if (slot == CONFIRM_ACCEPT_SLOT) {
            buyItem(player, holder.getItem());
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    openConfirmMenu(player, holder.getItem());
                }
            });
            return;
        }

        if (slot == CONFIRM_CANCEL_SLOT) {
            player.sendMessage(TextUtil.colorize("&ePurchase cancelled."));
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    openShop(player);
                }
            });
        }
    }

    private ItemStack createConfirmButton(ShopItem item, boolean accept) {
        Material material = accept ? Material.GREEN_WOOL : Material.RED_WOOL;
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            markAsShopDisplayItem(meta);
            if (accept) {
                meta.setDisplayName(TextUtil.colorize("&aConfirm Purchase"));
                List<String> lore = new ArrayList<>();
                lore.add(TextUtil.colorize("&7Buy &fx" + item.getAmount() + " " + toFriendlyMaterialName(item.getMaterial())));
                lore.add(TextUtil.colorize("&7Cost: &e" + TextUtil.formatCoins(item.getPrice()) + " " + plugin.getConfig().getString("coins-display-name", "Coins")));
                lore.add(TextUtil.colorize("&aClick to confirm"));
                meta.setLore(lore);
            } else {
                meta.setDisplayName(TextUtil.colorize("&cCancel Purchase"));
                meta.setLore(Collections.singletonList(TextUtil.colorize("&cClick to cancel")));
            }
            button.setItemMeta(meta);
        }
        return button;
    }

    private void markAsShopDisplayItem(ItemMeta meta) {
        meta.getPersistentDataContainer().set(shopDisplayItemKey, PersistentDataType.BYTE, (byte) 1);
    }

    private boolean isShopDisplayItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }

        Byte marker = meta.getPersistentDataContainer().get(shopDisplayItemKey, PersistentDataType.BYTE);
        return marker != null && marker.byteValue() == (byte) 1;
    }

    private void removeLeakedShopDisplayItems(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        if (isShopDisplayItem(cursor)) {
            player.setItemOnCursor(new ItemStack(Material.AIR));
        }

        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (isShopDisplayItem(player.getInventory().getItem(slot))) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    private void buyItem(Player player, ShopItem item) {
        if (!plugin.isEconomyEnabled()) {
            player.sendMessage(plugin.getEconomyDisabledMessage());
            return;
        }

        long price = item.getPrice();
        long coins = plugin.getPlayerDataStore().getCoins(player.getUniqueId());

        if (coins < price) {
            player.sendMessage(TextUtil.colorize("&cYou need &e" + TextUtil.formatCoins(price) + " &ccoins to buy this."));
            return;
        }

        ItemStack purchase = new ItemStack(item.getMaterial(), item.getAmount());
        if (!hasSpaceFor(player.getInventory(), purchase)) {
            player.sendMessage(TextUtil.colorize("&cYour inventory does not have enough space."));
            return;
        }

        player.getInventory().addItem(purchase);

        long updatedCoins = coins - price;
        plugin.getPlayerDataStore().setCoins(player.getUniqueId(), updatedCoins);
        plugin.getPlayerDataStore().save();
        plugin.getSidebarService().refreshAll();

        playBuySound(player);
        player.sendMessage(TextUtil.colorize(
                "&aPurchased &fx" + item.getAmount() + " " + toFriendlyMaterialName(item.getMaterial())
                        + " &afor &e" + TextUtil.formatCoins(price) + " " + plugin.getConfig().getString("coins-display-name", "Coins") + "&a."
        ));
    }

    private void playBuySound(Player player) {
        String soundName = plugin.getConfig().getString("shop.buy-sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        if (soundName == null || soundName.trim().isEmpty()) {
            return;
        }

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private boolean hasSpaceFor(PlayerInventory inventory, ItemStack stack) {
        int remaining = stack.getAmount();
        ItemStack[] storage = inventory.getStorageContents();

        for (ItemStack current : storage) {
            if (current == null || current.getType().isAir()) {
                remaining -= stack.getMaxStackSize();
            } else if (current.isSimilar(stack)) {
                remaining -= (current.getMaxStackSize() - current.getAmount());
            }

            if (remaining <= 0) {
                return true;
            }
        }

        return false;
    }

    private List<ShopItem> loadShopItems() {
        List<ShopItem> items = new ArrayList<>();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("shop.items");
        if (section == null) {
            return items;
        }

        for (String key : section.getKeys(false)) {
            Material material = parseMaterial(key);
            if (material == null) {
                continue;
            }

            ConfigurationSection itemSection = section.getConfigurationSection(key);
            if (itemSection == null) {
                continue;
            }

            int slot = itemSection.getInt("slot", -1);
            int amount = Math.max(1, Math.min(material.getMaxStackSize(), itemSection.getInt("amount", 1)));
            long price = Math.max(0L, itemSection.getLong("price", 0L));
            String name = itemSection.getString("name", "");
            List<String> lore = itemSection.getStringList("lore");

            items.add(new ShopItem(material, slot, amount, price, name, lore));
        }

        items.sort(Comparator.comparingInt(ShopItem::getSlot));
        return items;
    }

    private String applyShopPlaceholders(Player player, String input, ShopItem item) {
        String output = input
                .replace("%price%", String.valueOf(item.getPrice()))
                .replace("%price_compact%", TextUtil.formatCoins(item.getPrice()))
                .replace("%amount%", String.valueOf(item.getAmount()))
                .replace("%material%", item.getMaterial().name())
                .replace("%coins_name%", plugin.getConfig().getString("coins-display-name", "Coins"));

        return plugin.getSidebarService().replacePlaceholders(player, output);
    }

    private void ensureShopItemDefaults(Material material) {
        String basePath = "shop.items." + material.name();
        if (!plugin.getConfig().isConfigurationSection(basePath)) {
            plugin.getConfig().set(basePath + ".slot", findNextAvailableSlot());
            plugin.getConfig().set(basePath + ".amount", 1);
            plugin.getConfig().set(basePath + ".price", 0L);
        } else {
            if (!plugin.getConfig().contains(basePath + ".slot")) {
                plugin.getConfig().set(basePath + ".slot", findNextAvailableSlot());
            }
            if (!plugin.getConfig().contains(basePath + ".amount")) {
                plugin.getConfig().set(basePath + ".amount", 1);
            }
            if (!plugin.getConfig().contains(basePath + ".price")) {
                plugin.getConfig().set(basePath + ".price", 0L);
            }
        }
    }

    private int findNextAvailableSlot() {
        int size = normalizeShopSize(plugin.getConfig().getInt("shop.size", 27));
        Set<Integer> used = new HashSet<>();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("shop.items");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                int slot = section.getInt(key + ".slot", -1);
                if (slot >= 0 && slot < size) {
                    used.add(slot);
                }
            }
        }

        for (int slot = 0; slot < size; slot++) {
            if (!used.contains(slot)) {
                return slot;
            }
        }
        return 0;
    }

    private int normalizeShopSize(int rawSize) {
        int clamped = Math.max(9, Math.min(54, rawSize));
        if (clamped % 9 == 0) {
            return clamped;
        }
        return (clamped / 9) * 9;
    }

    private Material parseMaterial(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        return Material.matchMaterial(input.trim().toUpperCase(Locale.ROOT));
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

    private void sendShopEditUsage(CommandSender sender) {
        sender.sendMessage(TextUtil.colorize("&eShop edit commands:"));
        sender.sendMessage(TextUtil.colorize("&f/shopedit setprice <material> <price>"));
        sender.sendMessage(TextUtil.colorize("&f/shopedit setslot <material> <slot>"));
        sender.sendMessage(TextUtil.colorize("&f/shopedit setamount <material> <amount>"));
        sender.sendMessage(TextUtil.colorize("&f/shopedit remove <material>"));
        sender.sendMessage(TextUtil.colorize("&f/shopedit open"));
        sender.sendMessage(TextUtil.colorize("&f/shopedit reload"));
    }

    private List<String> completeMaterialNames(String input) {
        String lowerInput = input.toLowerCase(Locale.ROOT);
        Set<String> results = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        results.addAll(getConfiguredShopMaterials(lowerInput));
        results.addAll(getGlobalMaterialMatches(lowerInput));
        return new ArrayList<>(results);
    }

    private List<String> getConfiguredShopMaterials(String input) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("shop.items");
        if (section == null) {
            return Collections.emptyList();
        }

        List<String> out = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            if (key.toLowerCase(Locale.ROOT).startsWith(input)) {
                out.add(key.toUpperCase(Locale.ROOT));
            }
        }
        return out;
    }

    private List<String> getGlobalMaterialMatches(String input) {
        List<String> out = new ArrayList<>();
        for (Material material : Material.values()) {
            if (!material.name().toLowerCase(Locale.ROOT).startsWith(input)) {
                continue;
            }
            if (material.isAir()) {
                continue;
            }
            out.add(material.name());
            if (out.size() >= 40) {
                break;
            }
        }
        return out;
    }

    private static final class ShopItem {
        private final Material material;
        private final int slot;
        private final int amount;
        private final long price;
        private final String name;
        private final List<String> lore;

        private ShopItem(Material material, int slot, int amount, long price, String name, List<String> lore) {
            this.material = material;
            this.slot = slot;
            this.amount = amount;
            this.price = price;
            this.name = name;
            this.lore = lore == null ? Collections.emptyList() : lore;
        }

        private Material getMaterial() {
            return material;
        }

        private int getSlot() {
            return slot;
        }

        private int getAmount() {
            return amount;
        }

        private long getPrice() {
            return price;
        }

        private String getName() {
            return name;
        }

        private List<String> getLore() {
            return lore;
        }
    }

    private static final class ShopInventoryHolder implements InventoryHolder {
        private final Map<Integer, ShopItem> bySlot;
        private Inventory inventory;

        private ShopInventoryHolder() {
            this.bySlot = new TreeMap<>();
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private void putItem(int slot, ShopItem item) {
            bySlot.put(slot, item);
        }

        private ShopItem getItem(int slot) {
            return bySlot.get(slot);
        }
    }

    private static final class ConfirmInventoryHolder implements InventoryHolder {
        private final ShopItem item;
        private Inventory inventory;

        private ConfirmInventoryHolder(ShopItem item) {
            this.item = item;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private ShopItem getItem() {
            return item;
        }
    }
}
