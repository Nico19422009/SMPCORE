package by.nicojerema.smpcore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class RtpNpcCommand implements CommandExecutor, TabCompleter, Listener {
    private final SMPCorePlugin plugin;
    private final TeleportCommand teleportCommand;
    private final NamespacedKey npcMarkerKey;
    private final NamespacedKey npcNameKey;

    public RtpNpcCommand(SMPCorePlugin plugin, TeleportCommand teleportCommand) {
        this.plugin = plugin;
        this.teleportCommand = teleportCommand;
        this.npcMarkerKey = new NamespacedKey(plugin, "rtp_npc");
        this.npcNameKey = new NamespacedKey(plugin, "rtp_npc_name");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smpcore.admin")) {
            sender.sendMessage(TextUtil.colorize("&cYou do not have permission to use this command."));
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String providedName = joinArgs(args, 1);
        if (providedName.isEmpty()) {
            sender.sendMessage(TextUtil.colorize("&cPlease provide an NPC name."));
            return true;
        }

        switch (sub) {
            case "spawn":
                handleSpawn(sender, providedName);
                break;
            case "remove":
                handleRemove(sender, providedName);
                break;
            default:
                sendUsage(sender);
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smpcore.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            subcommands.add("spawn");
            subcommands.add("remove");

            String input = args[0].toLowerCase(Locale.ROOT);
            return subcommands.stream()
                    .filter(option -> option.startsWith(input))
                    .collect(Collectors.toList());
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("remove")) {
            String input = joinArgs(args, 1).toLowerCase(Locale.ROOT);
            return getLoadedNpcNames().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Entity clicked = event.getRightClicked();
        if (!isRtpNpc(clicked)) {
            return;
        }

        event.setCancelled(true);
        teleportCommand.performRtpForPlayer(event.getPlayer(), false);
    }

    private void handleSpawn(CommandSender sender, String providedName) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextUtil.colorize("&cOnly players can spawn an RTP NPC."));
            return;
        }

        String normalized = normalizeName(providedName);
        if (normalized.isEmpty()) {
            sender.sendMessage(TextUtil.colorize("&cNPC name cannot be empty."));
            return;
        }

        if (hasLoadedNpcWithName(normalized)) {
            sender.sendMessage(TextUtil.colorize("&cAn RTP NPC with that name already exists (loaded)."));
            return;
        }

        Player player = (Player) sender;
        Location location = player.getLocation();
        Villager villager = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);

        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setCollidable(false);
        villager.setCanPickupItems(false);
        villager.setSilent(true);
        villager.setRemoveWhenFarAway(false);
        villager.setPersistent(true);
        villager.setProfession(Villager.Profession.NONE);
        villager.setCustomName(TextUtil.colorize(providedName));
        villager.setCustomNameVisible(true);

        PersistentDataContainer container = villager.getPersistentDataContainer();
        container.set(npcMarkerKey, PersistentDataType.BYTE, (byte) 1);
        container.set(npcNameKey, PersistentDataType.STRING, normalized);

        sender.sendMessage(TextUtil.colorize("&aSpawned RTP NPC &f" + providedName + "&a at your location."));
    }

    private void handleRemove(CommandSender sender, String providedName) {
        String normalized = normalizeName(providedName);
        if (normalized.isEmpty()) {
            sender.sendMessage(TextUtil.colorize("&cNPC name cannot be empty."));
            return;
        }

        int removed = removeLoadedNpcsByName(normalized);
        if (removed == 0) {
            sender.sendMessage(TextUtil.colorize("&cNo loaded RTP NPC found with name &f" + providedName + "&c."));
            return;
        }

        sender.sendMessage(TextUtil.colorize("&aRemoved &f" + removed + " &aRTP NPC(s) named &f" + providedName + "&a."));
    }

    private int removeLoadedNpcsByName(String normalizedName) {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            List<Entity> entities = new ArrayList<>(world.getEntities());
            for (Entity entity : entities) {
                if (!isRtpNpc(entity)) {
                    continue;
                }
                String stored = entity.getPersistentDataContainer().get(npcNameKey, PersistentDataType.STRING);
                if (stored == null || !stored.equalsIgnoreCase(normalizedName)) {
                    continue;
                }
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    private boolean hasLoadedNpcWithName(String normalizedName) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!isRtpNpc(entity)) {
                    continue;
                }
                String stored = entity.getPersistentDataContainer().get(npcNameKey, PersistentDataType.STRING);
                if (stored != null && stored.equalsIgnoreCase(normalizedName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isRtpNpc(Entity entity) {
        if (entity == null) {
            return false;
        }
        Byte marker = entity.getPersistentDataContainer().get(npcMarkerKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private List<String> getLoadedNpcNames() {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!isRtpNpc(entity)) {
                    continue;
                }
                String stored = entity.getPersistentDataContainer().get(npcNameKey, PersistentDataType.STRING);
                if (stored == null || stored.trim().isEmpty()) {
                    continue;
                }
                names.add(stored);
            }
        }
        return new ArrayList<>(names);
    }

    private String joinArgs(String[] args, int start) {
        if (args == null || start >= args.length) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, start, args.length)).trim();
    }

    private String normalizeName(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.stripColor(TextUtil.colorize(input)).trim().toLowerCase(Locale.ROOT);
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(TextUtil.colorize("&eRTP NPC commands:"));
        sender.sendMessage(TextUtil.colorize("&f/rtpnpc spawn <name>"));
        sender.sendMessage(TextUtil.colorize("&f/rtpnpc remove <name>"));
    }
}
