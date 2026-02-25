package by.nicojerema.smpcore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Collectors;

public class HomeCommand implements CommandExecutor, TabCompleter, Listener {
    private static final int MAX_HOMES = 3;
    private static final int HOME_MENU_SIZE = 27;
    private static final int[] BED_SLOTS = {10, 13, 16};
    private static final int[] DYE_SLOTS = {19, 22, 25};

    private final SMPCorePlugin plugin;

    public HomeCommand(SMPCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "sethome":
                return handleSetHome(sender, args);
            case "home":
                return handleHome(sender, args);
            case "delhome":
                return handleDelHome(sender, args);
            default:
                return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smpcore.home")) {
            return Collections.emptyList();
        }

        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("1");
            options.add("2");
            options.add("3");
            if (commandName.equals("home")) {
                options.add("list");
            }

            String input = args[0].toLowerCase(Locale.ROOT);
            return options.stream()
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof HomeMenuHolder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != top) {
            return;
        }

        HomeMenuHolder holder = (HomeMenuHolder) top.getHolder();
        Integer teleportSlot = holder.getHomeSlotByBed(event.getSlot());
        Integer deleteSlot = holder.getHomeSlotByDeleteButton(event.getSlot());
        if (teleportSlot == null && deleteSlot == null) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        if (!player.getUniqueId().equals(holder.getOwnerUuid())) {
            player.sendMessage(TextUtil.colorize("&cThis is not your home menu."));
            player.closeInventory();
            return;
        }

        if (teleportSlot != null) {
            Location home = plugin.getPlayerDataStore().getHome(player.getUniqueId(), teleportSlot);
            if (home == null) {
                player.sendMessage(TextUtil.colorize("&cHome " + teleportSlot + " is not set (or world is missing)."));
                return;
            }

            player.teleport(home);
            player.closeInventory();
            player.sendMessage(TextUtil.colorize(
                    "&aTeleported to home &f" + teleportSlot + "&a at &f"
                            + home.getBlockX() + ", " + home.getBlockY() + ", " + home.getBlockZ() + "&a."
            ));
            return;
        }

        int slot = deleteSlot;
        if (!plugin.getPlayerDataStore().hasHome(player.getUniqueId(), slot)) {
            player.sendMessage(TextUtil.colorize("&cHome " + slot + " is not set."));
            return;
        }

        plugin.getPlayerDataStore().removeHome(player.getUniqueId(), slot);
        plugin.getPlayerDataStore().save();
        player.sendMessage(TextUtil.colorize("&aDeleted home &f" + slot + "&a."));
        openHomeMenu(player);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof HomeMenuHolder) {
            event.setCancelled(true);
        }
    }

    private boolean handleSetHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextUtil.colorize("&cOnly players can use this command."));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("smpcore.home")) {
            player.sendMessage(TextUtil.colorize("&cYou do not have permission."));
            return true;
        }

        int slot;
        if (args.length == 0) {
            slot = plugin.getPlayerDataStore().findFirstFreeHomeSlot(player.getUniqueId(), MAX_HOMES);
            if (slot < 1) {
                player.sendMessage(TextUtil.colorize("&cAll 3 home slots are used. Use &f/sethome <1-3> &cto replace one."));
                return true;
            }
        } else {
            slot = parseHomeSlot(args[0]);
            if (slot < 1) {
                player.sendMessage(TextUtil.colorize("&cUsage: /sethome [1-3]"));
                return true;
            }
        }

        Location location = player.getLocation();
        if (location.getWorld() == null) {
            player.sendMessage(TextUtil.colorize("&cCould not save home: invalid world."));
            return true;
        }

        plugin.getPlayerDataStore().setHome(player.getUniqueId(), slot, location);
        plugin.getPlayerDataStore().save();

        player.sendMessage(TextUtil.colorize(
                "&aSet home &f" + slot + " &aat &f"
                        + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + "&a."
        ));
        return true;
    }

    private boolean handleHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextUtil.colorize("&cOnly players can use this command."));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("smpcore.home")) {
            player.sendMessage(TextUtil.colorize("&cYou do not have permission."));
            return true;
        }

        if (args.length == 0) {
            openHomeMenu(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            List<Integer> setSlots = plugin.getPlayerDataStore().getSetHomeSlots(player.getUniqueId(), MAX_HOMES);
            if (setSlots.isEmpty()) {
                player.sendMessage(TextUtil.colorize("&cYou do not have any homes set."));
            } else {
                String joined = setSlots.stream().map(String::valueOf).collect(Collectors.joining(", "));
                player.sendMessage(TextUtil.colorize("&eYour homes: &f" + joined));
                player.sendMessage(TextUtil.colorize("&7Use &f/home <1-3> &7to teleport."));
            }
            return true;
        }

        int slot = parseHomeSlot(args[0]);
        if (slot < 1) {
            player.sendMessage(TextUtil.colorize("&cUsage: /home <1-3|list>"));
            return true;
        }

        Location home = plugin.getPlayerDataStore().getHome(player.getUniqueId(), slot);
        if (home == null) {
            player.sendMessage(TextUtil.colorize("&cHome " + slot + " is not set (or world is missing)."));
            return true;
        }

        player.teleport(home);
        player.sendMessage(TextUtil.colorize(
                "&aTeleported to home &f" + slot + "&a at &f"
                        + home.getBlockX() + ", " + home.getBlockY() + ", " + home.getBlockZ() + "&a."
        ));
        return true;
    }

    private boolean handleDelHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextUtil.colorize("&cOnly players can use this command."));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("smpcore.home")) {
            player.sendMessage(TextUtil.colorize("&cYou do not have permission."));
            return true;
        }

        if (args.length == 1) {
            int slot = parseHomeSlot(args[0]);
            if (slot < 1) {
                player.sendMessage(TextUtil.colorize("&cUsage: /delhome [1-3]"));
                return true;
            }

            if (!plugin.getPlayerDataStore().hasHome(player.getUniqueId(), slot)) {
                player.sendMessage(TextUtil.colorize("&cHome " + slot + " is not set."));
                return true;
            }

            plugin.getPlayerDataStore().removeHome(player.getUniqueId(), slot);
            plugin.getPlayerDataStore().save();
            player.sendMessage(TextUtil.colorize("&aDeleted home &f" + slot + "&a."));
            return true;
        }

        openHomeMenu(player);
        return true;
    }

    private void openHomeMenu(Player player) {
        HomeMenuHolder holder = new HomeMenuHolder(player.getUniqueId());
        Inventory menu = Bukkit.createInventory(holder, HOME_MENU_SIZE, TextUtil.colorize("&8Homes"));
        holder.setInventory(menu);

        menu.setItem(4, createInfoItem());

        for (int i = 0; i < MAX_HOMES; i++) {
            int homeSlot = i + 1;
            Location home = plugin.getPlayerDataStore().getHome(player.getUniqueId(), homeSlot);
            menu.setItem(BED_SLOTS[i], createBedItem(homeSlot, home));
            menu.setItem(DYE_SLOTS[i], createDeleteDyeItem(homeSlot, home != null));
            holder.bindBedSlot(BED_SLOTS[i], homeSlot);
            holder.bindDeleteButton(DYE_SLOTS[i], homeSlot);
        }

        player.openInventory(menu);
    }

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize("&eHome Menu"));
            List<String> lore = new ArrayList<>();
            lore.add(TextUtil.colorize("&7Click a bed to teleport."));
            lore.add(TextUtil.colorize("&7Click the gray dye under a bed to delete that home."));
            lore.add(TextUtil.colorize("&8Use /sethome [1-3] to set or replace homes."));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBedItem(int slot, Location location) {
        Material bedType;
        if (location == null || location.getWorld() == null) {
            bedType = Material.WHITE_BED;
        } else {
            switch (slot) {
                case 1:
                    bedType = Material.RED_BED;
                    break;
                case 2:
                    bedType = Material.BLUE_BED;
                    break;
                default:
                    bedType = Material.GREEN_BED;
                    break;
            }
        }

        ItemStack item = new ItemStack(bedType);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize("&fHome " + slot));
            List<String> lore = new ArrayList<>();
            if (location == null || location.getWorld() == null) {
                lore.add(TextUtil.colorize("&cNot set"));
                lore.add(TextUtil.colorize("&8Use /sethome " + slot));
            } else {
                lore.add(TextUtil.colorize("&aSet"));
                lore.add(TextUtil.colorize("&7World: &f" + location.getWorld().getName()));
                lore.add(TextUtil.colorize("&7XYZ: &f"
                        + location.getBlockX() + ", "
                        + location.getBlockY() + ", "
                        + location.getBlockZ()));
                lore.add(TextUtil.colorize("&eClick to teleport"));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createDeleteDyeItem(int slot, boolean enabled) {
        ItemStack item = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize("&7Delete Home " + slot));
            meta.setLore(Collections.singletonList(TextUtil.colorize(
                    enabled ? "&eClick to confirm deletion" : "&8No home to delete"
            )));
            item.setItemMeta(meta);
        }
        return item;
    }

    private int parseHomeSlot(String input) {
        int slot;
        try {
            slot = Integer.parseInt(input);
        } catch (NumberFormatException ex) {
            return -1;
        }
        if (slot < 1 || slot > MAX_HOMES) {
            return -1;
        }
        return slot;
    }

    private static final class HomeMenuHolder implements InventoryHolder {
        private final UUID ownerUuid;
        private final Map<Integer, Integer> bedToHomeSlot;
        private final Map<Integer, Integer> deleteButtonToHomeSlot;
        private Inventory inventory;

        private HomeMenuHolder(UUID ownerUuid) {
            this.ownerUuid = ownerUuid;
            this.bedToHomeSlot = new HashMap<>();
            this.deleteButtonToHomeSlot = new HashMap<>();
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

        private void bindBedSlot(int bedSlot, int homeSlot) {
            bedToHomeSlot.put(bedSlot, homeSlot);
        }

        private Integer getHomeSlotByBed(int bedSlot) {
            return bedToHomeSlot.get(bedSlot);
        }

        private void bindDeleteButton(int buttonSlot, int homeSlot) {
            deleteButtonToHomeSlot.put(buttonSlot, homeSlot);
        }

        private Integer getHomeSlotByDeleteButton(int buttonSlot) {
            return deleteButtonToHomeSlot.get(buttonSlot);
        }
    }
}
