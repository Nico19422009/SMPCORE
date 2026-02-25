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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class InvSeeCommand implements CommandExecutor, TabCompleter, Listener {
    private static final int VIEW_SIZE = 54;
    private static final int ENDERCHEST_VIEW_SIZE = 27;
    private static final int SLOT_HELMET = 45;
    private static final int SLOT_CHESTPLATE = 46;
    private static final int SLOT_LEGGINGS = 47;
    private static final int SLOT_BOOTS = 48;
    private static final int SLOT_OFFHAND = 50;

    private final SMPCorePlugin plugin;

    public InvSeeCommand(SMPCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextUtil.colorize("&cOnly players can use this command."));
            return true;
        }

        String commandName = command.getName().toLowerCase(Locale.ROOT);
        Player admin = (Player) sender;
        switch (commandName) {
            case "invsee":
                return handleInvSee(admin, args);
            case "echestsee":
                return handleEChestSee(admin, args);
            default:
                return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        String permission = commandName.equals("echestsee") ? "smpcore.echestsee" : "smpcore.invsee";
        if (!sender.hasPermission(permission)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(input)) {
                    out.add(player.getName());
                }
            }
            return out;
        }

        return Collections.emptyList();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof InvSeeInventoryHolder || top.getHolder() instanceof EChestInventoryHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof InvSeeInventoryHolder || top.getHolder() instanceof EChestInventoryHolder) {
            event.setCancelled(true);
        }
    }

    private boolean handleInvSee(Player admin, String[] args) {
        if (!admin.hasPermission("smpcore.invsee")) {
            admin.sendMessage(TextUtil.colorize("&cYou do not have permission."));
            return true;
        }

        if (args.length < 1) {
            admin.sendMessage(TextUtil.colorize("&cUsage: /invsee <player>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            admin.sendMessage(TextUtil.colorize("&cPlayer not found online."));
            return true;
        }

        openInventoryView(admin, target);
        return true;
    }

    private boolean handleEChestSee(Player admin, String[] args) {
        if (!admin.hasPermission("smpcore.echestsee")) {
            admin.sendMessage(TextUtil.colorize("&cYou do not have permission."));
            return true;
        }

        if (args.length < 1) {
            admin.sendMessage(TextUtil.colorize("&cUsage: /echestsee <player>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            admin.sendMessage(TextUtil.colorize("&cPlayer not found online."));
            return true;
        }

        openEnderChestView(admin, target);
        return true;
    }

    private void openInventoryView(Player admin, Player target) {
        String title = TextUtil.colorize("&8InvSee: &f" + target.getName());
        InvSeeInventoryHolder holder = new InvSeeInventoryHolder(target.getUniqueId().toString(), target.getName());
        Inventory inventory = Bukkit.createInventory(holder, VIEW_SIZE, title);
        holder.setInventory(inventory);

        PlayerInventory targetInv = target.getInventory();
        ItemStack[] storage = targetInv.getStorageContents();
        for (int i = 0; i < storage.length && i < 36; i++) {
            ItemStack item = storage[i];
            if (item != null) {
                inventory.setItem(i, item.clone());
            }
        }

        setOrPlaceholder(inventory, SLOT_HELMET, targetInv.getHelmet(), "&eHelmet");
        setOrPlaceholder(inventory, SLOT_CHESTPLATE, targetInv.getChestplate(), "&eChestplate");
        setOrPlaceholder(inventory, SLOT_LEGGINGS, targetInv.getLeggings(), "&eLeggings");
        setOrPlaceholder(inventory, SLOT_BOOTS, targetInv.getBoots(), "&eBoots");
        setOrPlaceholder(inventory, SLOT_OFFHAND, targetInv.getItemInOffHand(), "&eOffhand");

        admin.openInventory(inventory);
    }

    private void openEnderChestView(Player admin, Player target) {
        String title = TextUtil.colorize("&8EChest: &f" + target.getName());
        EChestInventoryHolder holder = new EChestInventoryHolder(target.getUniqueId().toString(), target.getName());
        Inventory inventory = Bukkit.createInventory(holder, ENDERCHEST_VIEW_SIZE, title);
        holder.setInventory(inventory);

        Inventory targetEChest = target.getEnderChest();
        ItemStack[] contents = targetEChest.getContents();
        for (int i = 0; i < contents.length && i < ENDERCHEST_VIEW_SIZE; i++) {
            ItemStack item = contents[i];
            if (item != null) {
                inventory.setItem(i, item.clone());
            }
        }

        admin.openInventory(inventory);
    }

    private void setOrPlaceholder(Inventory inventory, int slot, ItemStack item, String placeholderName) {
        if (item != null && !item.getType().isAir()) {
            inventory.setItem(slot, item.clone());
            return;
        }

        ItemStack placeholder = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = placeholder.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize(placeholderName));
            placeholder.setItemMeta(meta);
        }
        inventory.setItem(slot, placeholder);
    }

    private static final class InvSeeInventoryHolder implements InventoryHolder {
        private final String targetUuid;
        private final String targetName;
        private Inventory inventory;

        private InvSeeInventoryHolder(String targetUuid, String targetName) {
            this.targetUuid = targetUuid;
            this.targetName = targetName;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @SuppressWarnings("unused")
        private String getTargetUuid() {
            return targetUuid;
        }

        @SuppressWarnings("unused")
        private String getTargetName() {
            return targetName;
        }
    }

    private static final class EChestInventoryHolder implements InventoryHolder {
        private final String targetUuid;
        private final String targetName;
        private Inventory inventory;

        private EChestInventoryHolder(String targetUuid, String targetName) {
            this.targetUuid = targetUuid;
            this.targetName = targetName;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @SuppressWarnings("unused")
        private String getTargetUuid() {
            return targetUuid;
        }

        @SuppressWarnings("unused")
        private String getTargetName() {
            return targetName;
        }
    }
}
