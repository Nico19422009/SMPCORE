package by.nicojerema.smpcore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class SettingsCommand implements CommandExecutor, TabCompleter, Listener {
    private static final int MENU_SIZE = 27;
    private static final int SLOT_TPA_NOTIFICATIONS = 11;
    private static final int SLOT_NIGHT_VISION = 15;
    private static final int SLOT_SIDEBAR_SECONDARY = 19;

    private final SMPCorePlugin plugin;

    public SettingsCommand(SMPCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextUtil.colorize("&cOnly players can use this command."));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("smpcore.settings")) {
            player.sendMessage(TextUtil.colorize("&cYou do not have permission."));
            return true;
        }

        openSettingsMenu(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof SettingsMenuHolder)) {
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
        SettingsMenuHolder holder = (SettingsMenuHolder) top.getHolder();
        if (!player.getUniqueId().equals(holder.getOwnerUuid())) {
            player.closeInventory();
            return;
        }

        switch (event.getSlot()) {
            case SLOT_TPA_NOTIFICATIONS:
                toggleTpaNotifications(player);
                openSettingsMenu(player);
                break;
            case SLOT_NIGHT_VISION:
                toggleNightVision(player);
                openSettingsMenu(player);
                break;
            case SLOT_SIDEBAR_SECONDARY:
                toggleSidebarCurrency(player);
                openSettingsMenu(player);
                break;
            default:
                break;
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof SettingsMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        applyNightVisionSetting(player);
    }

    private void openSettingsMenu(Player player) {
        SettingsMenuHolder holder = new SettingsMenuHolder(player.getUniqueId());
        Inventory menu = Bukkit.createInventory(holder, MENU_SIZE, TextUtil.colorize("&8Settings"));
        holder.setInventory(menu);

        boolean tpaNotifications = plugin.getPlayerDataStore().isTpaNotificationsEnabled(player.getUniqueId());
        boolean nightVision = plugin.getPlayerDataStore().isNightVisionEnabled(player.getUniqueId());

        menu.setItem(SLOT_TPA_NOTIFICATIONS, createToggleItem(
                Material.ENDER_PEARL,
                "&eTPA Notifications",
                tpaNotifications,
                "&7Receive incoming /tpa requests",
                "&7When off, players cannot send you /tpa requests."
        ));

        menu.setItem(SLOT_NIGHT_VISION, createToggleItem(
                Material.POTION,
                "&bNight Vision",
                nightVision,
                "&7Keep night vision effect active",
                "&7Effect is automatically applied on join."
        ));

        boolean showingSecondary = plugin.getPlayerDataStore().isSidebarSecondaryVisible(player.getUniqueId());
        menu.setItem(SLOT_SIDEBAR_SECONDARY, createSidebarToggleItem(
                Material.NETHER_STAR,
                "&dSidebar Currency",
                showingSecondary,
                plugin.getSecondaryCurrencyDisplayName()
        ));

        player.openInventory(menu);
    }

    private ItemStack createToggleItem(Material material, String title, boolean enabled, String line1, String line2) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize(title));
            List<String> lore = new ArrayList<>();
            lore.add(TextUtil.colorize(line1));
            lore.add(TextUtil.colorize(line2));
            lore.add(TextUtil.colorize("&8"));
            lore.add(TextUtil.colorize("&7Status: " + (enabled ? "&aON" : "&cOFF")));
            lore.add(TextUtil.colorize("&eClick to toggle"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSidebarToggleItem(Material material, String title, boolean enabled, String currencyName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize(title));
            List<String> lore = new ArrayList<>();
            lore.add(TextUtil.colorize("&7Show &f" + currencyName + " &7on your sidebar"));
            lore.add(TextUtil.colorize("&8"));
            lore.add(TextUtil.colorize("&7Status: " + (enabled ? "&aON" : "&cOFF")));
            lore.add(TextUtil.colorize("&eClick to toggle"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void toggleTpaNotifications(Player player) {
        UUID uuid = player.getUniqueId();
        boolean current = plugin.getPlayerDataStore().isTpaNotificationsEnabled(uuid);
        boolean updated = !current;
        plugin.getPlayerDataStore().setTpaNotificationsEnabled(uuid, updated);
        plugin.getPlayerDataStore().save();

        if (updated) {
            player.sendMessage(TextUtil.colorize("&aTPA notifications enabled."));
        } else {
            player.sendMessage(TextUtil.colorize("&cTPA notifications disabled."));
        }
    }

    private void toggleNightVision(Player player) {
        UUID uuid = player.getUniqueId();
        boolean current = plugin.getPlayerDataStore().isNightVisionEnabled(uuid);
        boolean updated = !current;
        plugin.getPlayerDataStore().setNightVisionEnabled(uuid, updated);
        plugin.getPlayerDataStore().save();
        applyNightVisionSetting(player);

        if (updated) {
            player.sendMessage(TextUtil.colorize("&aNight vision enabled."));
        } else {
            player.sendMessage(TextUtil.colorize("&cNight vision disabled."));
        }
    }

    private void toggleSidebarCurrency(Player player) {
        UUID uuid = player.getUniqueId();
        boolean current = plugin.getPlayerDataStore().isSidebarSecondaryVisible(uuid);
        boolean updated = !current;
        plugin.getPlayerDataStore().setSidebarSecondaryVisible(uuid, updated);
        plugin.getPlayerDataStore().save();
        plugin.getSidebarService().refreshAll();

        if (updated) {
            player.sendMessage(TextUtil.colorize("&aSecondary currency now appears on your sidebar."));
        } else {
            player.sendMessage(TextUtil.colorize("&cSecondary currency hidden from your sidebar."));
        }
    }

    public void applyNightVisionSetting(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        boolean enabled = plugin.getPlayerDataStore().isNightVisionEnabled(player.getUniqueId());
        if (enabled) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.NIGHT_VISION,
                    Integer.MAX_VALUE,
                    0,
                    true,
                    false,
                    false
            ));
            return;
        }

        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    }

    private static final class SettingsMenuHolder implements InventoryHolder {
        private final UUID ownerUuid;
        private Inventory inventory;

        private SettingsMenuHolder(UUID ownerUuid) {
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
