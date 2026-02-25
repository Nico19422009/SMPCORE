package by.nicojerema.smpcore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CurrencyEditorMenu implements Listener {
    private static final int MENU_SIZE = 9;
    private static final int SLOT_PRIMARY_NAME = 1;
    private static final int SLOT_SECONDARY_TOGGLE = 3;
    private static final int SLOT_SECONDARY_NAME = 5;
    private static final int SLOT_INFO = 7;

    private final SMPCorePlugin plugin;
    private final ConcurrentHashMap<UUID, CurrencyPendingEdit> pendingEdits;

    public CurrencyEditorMenu(SMPCorePlugin plugin) {
        this.plugin = plugin;
        this.pendingEdits = new ConcurrentHashMap<>();
    }

    public void openMenu(Player admin) {
        if (!admin.hasPermission("smpcore.admin")) {
            admin.sendMessage(TextUtil.colorize("&cYou do not have permission to use this menu."));
            return;
        }

        CurrencyMenuHolder holder = new CurrencyMenuHolder(admin.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, TextUtil.colorize("&8Currency Settings"));
        holder.setInventory(inventory);

        inventory.setItem(SLOT_PRIMARY_NAME, createPrimaryCurrencyItem());
        inventory.setItem(SLOT_SECONDARY_TOGGLE, createSecondaryToggleItem(plugin.isSecondaryCurrencyEnabled()));
        inventory.setItem(SLOT_SECONDARY_NAME, createSecondaryCurrencyItem());
        inventory.setItem(SLOT_INFO, createInfoItem());

        admin.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof CurrencyMenuHolder)) {
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
        CurrencyMenuHolder holder = (CurrencyMenuHolder) top.getHolder();
        if (!player.getUniqueId().equals(holder.getOwnerUuid())) {
            player.closeInventory();
            return;
        }

        switch (event.getSlot()) {
            case SLOT_PRIMARY_NAME:
                startRename(player, CurrencyEditType.PRIMARY);
                break;
            case SLOT_SECONDARY_TOGGLE:
                toggleSecondaryCurrency(player);
                openMenu(player);
                break;
            case SLOT_SECONDARY_NAME:
                if (!plugin.isSecondaryCurrencyEnabled()) {
                    player.sendMessage(TextUtil.colorize("&cEnable the secondary currency first."));
                    return;
                }
                startRename(player, CurrencyEditType.SECONDARY);
                break;
            default:
                break;
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof CurrencyMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        CurrencyPendingEdit pending = pendingEdits.remove(uuid);
        if (pending == null) {
            return;
        }

        event.setCancelled(true);
        String input = event.getMessage();
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTask(plugin, () -> applyPendingEdit(player, pending, input == null ? "" : input.trim()));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        pendingEdits.remove(event.getPlayer().getUniqueId());
    }

    private void startRename(Player player, CurrencyEditType type) {
        pendingEdits.put(player.getUniqueId(), new CurrencyPendingEdit(type));
        player.closeInventory();
        if (type == CurrencyEditType.PRIMARY) {
            player.sendMessage(TextUtil.colorize("&eEditing primary currency name (&fcoins-display-name&e)."));
        } else {
            player.sendMessage(TextUtil.colorize("&eEditing secondary currency name (&fcurrency.secondary.display-name&e)."));
        }
        player.sendMessage(TextUtil.colorize("&7Type the new name in chat or &ccancel&7."));
    }

    private void applyPendingEdit(Player player, CurrencyPendingEdit pending, String input) {
        if (!player.isOnline()) {
            return;
        }
        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage(TextUtil.colorize("&cCurrency rename cancelled."));
            openMenu(player);
            return;
        }
        if (input.isEmpty()) {
            pendingEdits.put(player.getUniqueId(), pending);
            player.sendMessage(TextUtil.colorize("&cName cannot be empty. Try again or &ccancel&7."));
            return;
        }

        if (pending.getType() == CurrencyEditType.PRIMARY) {
            plugin.getConfig().set("coins-display-name", input);
            player.sendMessage(TextUtil.colorize("&aPrimary currency renamed to &f" + input + "&a."));
        } else {
            plugin.getConfig().set("currency.secondary.display-name", input);
            player.sendMessage(TextUtil.colorize("&aSecondary currency renamed to &f" + input + "&a."));
        }

        plugin.saveConfig();
        plugin.getSidebarService().refreshAll();
        openMenu(player);
    }

    private void toggleSecondaryCurrency(Player player) {
        if (!player.hasPermission("smpcore.admin")) {
            return;
        }
        boolean enabled = plugin.isSecondaryCurrencyEnabled();
        plugin.setSecondaryCurrencyEnabled(!enabled);
        plugin.saveConfig();
        plugin.getSidebarService().refreshAll();
        player.sendMessage(TextUtil.colorize(
                (enabled ? "&cSecondary currency disabled." : "&aSecondary currency enabled.")
        ));
    }

    private ItemStack createPrimaryCurrencyItem() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize("&aPrimary Currency Name"));
            List<String> lore = new ArrayList<>();
            lore.add(TextUtil.colorize("&7Current: &f" + plugin.getConfig().getString("coins-display-name", "Coins")));
            lore.add(TextUtil.colorize("&eClick to edit via chat"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSecondaryToggleItem(boolean enabled) {
        Material material = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize(enabled ? "&aSecondary Currency Enabled" : "&cSecondary Currency Disabled"));
            List<String> lore = new ArrayList<>();
            lore.add(TextUtil.colorize("&7Current name: &f" + plugin.getSecondaryCurrencyDisplayName()));
            lore.add(TextUtil.colorize("&eClick to toggle"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSecondaryCurrencyItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize("&dSecondary Currency Name"));
            List<String> lore = new ArrayList<>();
            lore.add(TextUtil.colorize("&7Current: &f" + plugin.getSecondaryCurrencyDisplayName()));
            if (!plugin.isSecondaryCurrencyEnabled()) {
                lore.add(TextUtil.colorize("&cEnable the secondary currency first."));
            } else {
                lore.add(TextUtil.colorize("&eClick to edit via chat"));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize("&eCurrency info"));
            List<String> lore = new ArrayList<>();
            lore.add(TextUtil.colorize("&7Left click items to edit names."));
            lore.add(TextUtil.colorize("&7Toggle secondary currency availability."));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static final class CurrencyPendingEdit {
        private final CurrencyEditType type;

        private CurrencyPendingEdit(CurrencyEditType type) {
            this.type = type;
        }

        private CurrencyEditType getType() {
            return type;
        }
    }

    private enum CurrencyEditType {
        PRIMARY,
        SECONDARY
    }

    private static final class CurrencyMenuHolder implements InventoryHolder {
        private final UUID ownerUuid;
        private Inventory inventory;

        private CurrencyMenuHolder(UUID ownerUuid) {
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
