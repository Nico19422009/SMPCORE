package by.nicojerema.smpcore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

public class TeleportCommand implements CommandExecutor, TabCompleter, Listener {
    private final SMPCorePlugin plugin;
    private final Random random;

    private final Map<UUID, TpaRequest> requestsByTarget;
    private final Map<UUID, UUID> outgoingTargetByRequester;
    private final Map<UUID, Long> rtpCooldownUntilMillis;

    public TeleportCommand(SMPCorePlugin plugin) {
        this.plugin = plugin;
        this.random = new Random();
        this.requestsByTarget = new HashMap<>();
        this.outgoingTargetByRequester = new HashMap<>();
        this.rtpCooldownUntilMillis = new HashMap<>();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "tpa":
                return handleTpa(sender, args);
            case "tpahere":
                return handleTpahere(sender, args);
            case "tpaccept":
                return handleTpAccept(sender);
            case "tpdeny":
                return handleTpDeny(sender);
            case "tpcancel":
            case "tpancel":
                return handleTpCancel(sender);
            case "rtp":
                return handleRtp(sender);
            default:
                return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if ((command.getName().equalsIgnoreCase("tpa") || command.getName().equalsIgnoreCase("tpahere"))
                && args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> !name.equalsIgnoreCase(sender.getName()))
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        TpaRequest incoming;
        TpaRequest outgoing;

        synchronized (this) {
            incoming = requestsByTarget.remove(uuid);
            if (incoming != null) {
                outgoingTargetByRequester.remove(incoming.getRequesterUuid());
            }

            UUID targetUuid = outgoingTargetByRequester.remove(uuid);
            if (targetUuid != null) {
                TpaRequest request = requestsByTarget.get(targetUuid);
                if (request != null && request.getRequesterUuid().equals(uuid)) {
                    requestsByTarget.remove(targetUuid);
                    outgoing = request;
                } else {
                    outgoing = null;
                }
            } else {
                outgoing = null;
            }
        }

        if (incoming != null) {
            incoming.getTimeoutTask().cancel();
            Player requester = Bukkit.getPlayer(incoming.getRequesterUuid());
            if (requester != null && requester.isOnline()) {
                requester.sendMessage(TextUtil.colorize("&cYour teleport request expired because the player left."));
            }
        }

        if (outgoing != null) {
            outgoing.getTimeoutTask().cancel();
            Player target = Bukkit.getPlayer(outgoing.getTargetUuid());
            if (target != null && target.isOnline()) {
                target.sendMessage(TextUtil.colorize("&eTeleport request cancelled because requester left."));
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (!hasMovedBlock(event.getFrom(), event.getTo())) {
            return;
        }

        Player requester = event.getPlayer();
        cancelOutgoingRequestDueToMovement(requester);
    }

    private boolean handleTpa(CommandSender sender, String[] args) {
        return handleTeleportRequest(sender, args, RequestType.TPA);
    }

    private boolean handleTpahere(CommandSender sender, String[] args) {
        return handleTeleportRequest(sender, args, RequestType.TPA_HERE);
    }

    private boolean handleTeleportRequest(CommandSender sender, String[] args, RequestType type) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextUtil.colorize("&cOnly players can use this command."));
            return true;
        }
        Player requester = (Player) sender;
        if (!requester.hasPermission("smpcore.tpa")) {
            requester.sendMessage(TextUtil.colorize("&cYou do not have permission."));
            return true;
        }
        if (args.length < 1) {
            String usage = type == RequestType.TPA ? "/tpa <player>" : "/tpahere <player>";
            requester.sendMessage(TextUtil.colorize("&cUsage: " + usage));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            requester.sendMessage(TextUtil.colorize("&cPlayer not found online."));
            return true;
        }
        if (target.getUniqueId().equals(requester.getUniqueId())) {
            requester.sendMessage(TextUtil.colorize("&cYou cannot send a request to yourself."));
            return true;
        }
        if (!plugin.getPlayerDataStore().isTpaNotificationsEnabled(target.getUniqueId())) {
            requester.sendMessage(TextUtil.colorize("&cThat player has TPA notifications disabled."));
            return true;
        }

        TpaRequest replacedRequest = null;
        int timeoutSeconds = Math.max(5, plugin.getConfig().getInt("teleport.tpa-timeout-seconds", 60));
        UUID requesterUuid = requester.getUniqueId();
        UUID targetUuid = target.getUniqueId();

        synchronized (this) {
            if (requestsByTarget.containsKey(targetUuid)) {
                requester.sendMessage(TextUtil.colorize("&cThat player already has a pending teleport request."));
                return true;
            }

            UUID previousTarget = outgoingTargetByRequester.remove(requesterUuid);
            if (previousTarget != null) {
                TpaRequest old = requestsByTarget.get(previousTarget);
                if (old != null && old.getRequesterUuid().equals(requesterUuid)) {
                    requestsByTarget.remove(previousTarget);
                    replacedRequest = old;
                }
            }

            BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(
                    plugin,
                    () -> expireRequest(requesterUuid, targetUuid),
                    timeoutSeconds * 20L
            );

            TpaRequest request = new TpaRequest(requesterUuid, targetUuid, type, timeoutTask);
            requestsByTarget.put(targetUuid, request);
            outgoingTargetByRequester.put(requesterUuid, targetUuid);
        }

        if (replacedRequest != null) {
            replacedRequest.getTimeoutTask().cancel();
            Player oldTarget = Bukkit.getPlayer(replacedRequest.getTargetUuid());
            if (oldTarget != null && oldTarget.isOnline()) {
                oldTarget.sendMessage(TextUtil.colorize("&ePrevious teleport request was cancelled."));
            }
        }

        String sentMessage = type == RequestType.TPA
                ? "&aTeleport request sent to &f" + target.getName() + "&a."
                : "&aTeleport-here request sent to &f" + target.getName() + "&a.";
        requester.sendMessage(TextUtil.colorize(sentMessage));
        sendClickableRequestPrompt(target, requester.getName(), type);
        return true;
    }

    private boolean handleTpAccept(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextUtil.colorize("&cOnly players can use this command."));
            return true;
        }
        Player target = (Player) sender;
        if (!target.hasPermission("smpcore.tpa")) {
            target.sendMessage(TextUtil.colorize("&cYou do not have permission."));
            return true;
        }

        TpaRequest request;
        synchronized (this) {
            request = requestsByTarget.remove(target.getUniqueId());
            if (request != null) {
                outgoingTargetByRequester.remove(request.getRequesterUuid());
            }
        }

        if (request == null) {
            target.sendMessage(TextUtil.colorize("&cYou do not have a pending teleport request."));
            return true;
        }

        request.getTimeoutTask().cancel();

        Player requester = Bukkit.getPlayer(request.getRequesterUuid());
        if (requester == null || !requester.isOnline()) {
            target.sendMessage(TextUtil.colorize("&cRequester is no longer online."));
            return true;
        }

        if (request.getType() == RequestType.TPA_HERE) {
            target.teleport(requester.getLocation());
            target.sendMessage(TextUtil.colorize("&aTeleported to &f" + requester.getName() + "&a."));
            requester.sendMessage(TextUtil.colorize("&a" + target.getName() + " accepted your teleport-here request."));
        } else {
            requester.teleport(target.getLocation());
            requester.sendMessage(TextUtil.colorize("&aTeleport request accepted."));
            target.sendMessage(TextUtil.colorize("&aAccepted teleport request from &f" + requester.getName() + "&a."));
        }
        return true;
    }

    private boolean handleTpDeny(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextUtil.colorize("&cOnly players can use this command."));
            return true;
        }
        Player target = (Player) sender;
        if (!target.hasPermission("smpcore.tpa")) {
            target.sendMessage(TextUtil.colorize("&cYou do not have permission."));
            return true;
        }

        TpaRequest request;
        synchronized (this) {
            request = requestsByTarget.remove(target.getUniqueId());
            if (request != null) {
                outgoingTargetByRequester.remove(request.getRequesterUuid());
            }
        }

        if (request == null) {
            target.sendMessage(TextUtil.colorize("&cYou do not have a pending teleport request."));
            return true;
        }

        request.getTimeoutTask().cancel();

        Player requester = Bukkit.getPlayer(request.getRequesterUuid());
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(TextUtil.colorize("&cYour teleport request was denied."));
        }
        target.sendMessage(TextUtil.colorize("&eTeleport request denied."));
        return true;
    }

    private boolean handleTpCancel(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextUtil.colorize("&cOnly players can use this command."));
            return true;
        }
        Player requester = (Player) sender;
        if (!requester.hasPermission("smpcore.tpa")) {
            requester.sendMessage(TextUtil.colorize("&cYou do not have permission."));
            return true;
        }

        TpaRequest request = null;

        synchronized (this) {
            UUID targetUuid = outgoingTargetByRequester.remove(requester.getUniqueId());
            if (targetUuid != null) {
                TpaRequest existing = requestsByTarget.get(targetUuid);
                if (existing != null && existing.getRequesterUuid().equals(requester.getUniqueId())) {
                    requestsByTarget.remove(targetUuid);
                    request = existing;
                }
            }
        }

        if (request == null) {
            requester.sendMessage(TextUtil.colorize("&cYou do not have any outgoing teleport request."));
            return true;
        }

        request.getTimeoutTask().cancel();

        Player target = Bukkit.getPlayer(request.getTargetUuid());
        if (target != null && target.isOnline()) {
            target.sendMessage(TextUtil.colorize("&eTeleport request cancelled by requester."));
        }
        requester.sendMessage(TextUtil.colorize("&eTeleport request cancelled."));
        return true;
    }

    private boolean handleRtp(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextUtil.colorize("&cOnly players can use this command."));
            return true;
        }
        Player player = (Player) sender;
        performRtpForPlayer(player, true);
        return true;
    }

    public boolean performRtpForPlayer(Player player, boolean checkPermission) {
        if (player == null) {
            return false;
        }

        if (checkPermission && !player.hasPermission("smpcore.rtp")) {
            player.sendMessage(TextUtil.colorize("&cYou do not have permission."));
            return false;
        }

        int cooldownSeconds = Math.max(0, plugin.getConfig().getInt("teleport.rtp-cooldown-seconds", 10));
        long now = System.currentTimeMillis();
        if (!player.hasPermission("smpcore.rtp.bypasscooldown") && cooldownSeconds > 0) {
            long readyAt = rtpCooldownUntilMillis.getOrDefault(player.getUniqueId(), 0L);
            if (readyAt > now) {
                long remaining = (readyAt - now + 999L) / 1000L;
                player.sendMessage(TextUtil.colorize("&cYou must wait " + remaining + "s before using /rtp again."));
                return false;
            }
        }

        World world = player.getWorld();
        int radius = Math.max(100, plugin.getConfig().getInt("teleport.rtp-radius", 1500));
        int attempts = Math.max(1, plugin.getConfig().getInt("teleport.rtp-attempts", 20));
        boolean useSpawn = plugin.getConfig().getBoolean("teleport.rtp-use-world-spawn", true);

        Location center = useSpawn ? world.getSpawnLocation() : player.getLocation();
        Location found = findRandomSafeLocation(world, center, radius, attempts, player.getLocation());

        if (found == null) {
            player.sendMessage(TextUtil.colorize("&cCould not find a safe random location. Try again."));
            return false;
        }

        player.teleport(found);

        if (!player.hasPermission("smpcore.rtp.bypasscooldown") && cooldownSeconds > 0) {
            rtpCooldownUntilMillis.put(player.getUniqueId(), now + cooldownSeconds * 1000L);
        }

        player.sendMessage(TextUtil.colorize("&aRandom teleported to &f" + found.getBlockX() + ", " + found.getBlockY() + ", " + found.getBlockZ() + "&a."));
        return true;
    }

    private synchronized void expireRequest(UUID requesterUuid, UUID targetUuid) {
        TpaRequest existing = requestsByTarget.get(targetUuid);
        if (existing == null || !existing.getRequesterUuid().equals(requesterUuid)) {
            return;
        }

        requestsByTarget.remove(targetUuid);
        outgoingTargetByRequester.remove(requesterUuid);

        Player requester = Bukkit.getPlayer(requesterUuid);
        Player target = Bukkit.getPlayer(targetUuid);

        if (requester != null && requester.isOnline()) {
            requester.sendMessage(TextUtil.colorize("&cYour teleport request expired."));
        }
        if (target != null && target.isOnline()) {
            target.sendMessage(TextUtil.colorize("&eA teleport request has expired."));
        }
    }

    private void cancelOutgoingRequestDueToMovement(Player requester) {
        if (requester == null) {
            return;
        }

        TpaRequest request = null;
        synchronized (this) {
            UUID targetUuid = outgoingTargetByRequester.remove(requester.getUniqueId());
            if (targetUuid != null) {
                TpaRequest existing = requestsByTarget.get(targetUuid);
                if (existing != null && existing.getRequesterUuid().equals(requester.getUniqueId())) {
                    requestsByTarget.remove(targetUuid);
                    request = existing;
                }
            }
        }

        if (request == null) {
            return;
        }

        request.getTimeoutTask().cancel();
        requester.sendMessage(TextUtil.colorize("&cTeleport request cancelled because you moved."));

        Player target = Bukkit.getPlayer(request.getTargetUuid());
        if (target != null && target.isOnline()) {
            target.sendMessage(TextUtil.colorize("&eTeleport request from &f" + requester.getName() + " &ewas cancelled because they moved."));
        }
    }

    private boolean hasMovedBlock(Location from, Location to) {
        if (from == null || to == null) {
            return false;
        }
        if (from.getWorld() == null || to.getWorld() == null) {
            return false;
        }
        if (!from.getWorld().equals(to.getWorld())) {
            return true;
        }

        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }

    private Location findRandomSafeLocation(
            World world,
            Location center,
            int radius,
            int attempts,
            Location referenceFacing
    ) {
        for (int i = 0; i < attempts; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = random.nextDouble() * radius;

            double x = center.getX() + Math.cos(angle) * distance;
            double z = center.getZ() + Math.sin(angle) * distance;

            if (!isInsideWorldBorder(world, x, z)) {
                continue;
            }

            int blockX = (int) Math.floor(x);
            int blockZ = (int) Math.floor(z);
            int highestY = world.getHighestBlockYAt(blockX, blockZ);

            if (highestY <= world.getMinHeight()) {
                continue;
            }

            Location candidate = new Location(
                    world,
                    blockX + 0.5,
                    highestY + 1.0,
                    blockZ + 0.5,
                    referenceFacing.getYaw(),
                    referenceFacing.getPitch()
            );

            if (isSafeDestination(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isInsideWorldBorder(World world, double x, double z) {
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double half = border.getSize() / 2.0;

        return x >= center.getX() - half
                && x <= center.getX() + half
                && z >= center.getZ() - half
                && z <= center.getZ() + half;
    }

    private boolean isSafeDestination(Location location) {
        Block feet = location.getBlock();
        Block head = location.clone().add(0.0, 1.0, 0.0).getBlock();
        Block below = location.clone().add(0.0, -1.0, 0.0).getBlock();

        if (!below.getType().isSolid()) {
            return false;
        }

        if (!feet.isPassable() || !head.isPassable()) {
            return false;
        }

        return !feet.isLiquid() && !head.isLiquid() && !below.isLiquid();
    }

    private static final class TpaRequest {
        private final UUID requesterUuid;
        private final UUID targetUuid;
        private final RequestType type;
        private final BukkitTask timeoutTask;

        private TpaRequest(UUID requesterUuid, UUID targetUuid, RequestType type, BukkitTask timeoutTask) {
            this.requesterUuid = requesterUuid;
            this.targetUuid = targetUuid;
            this.type = type;
            this.timeoutTask = timeoutTask;
        }

        private UUID getRequesterUuid() {
            return requesterUuid;
        }

        private UUID getTargetUuid() {
            return targetUuid;
        }

        private RequestType getType() {
            return type;
        }

        private BukkitTask getTimeoutTask() {
            return timeoutTask;
        }
    }

    public synchronized void shutdown() {
        for (TpaRequest request : requestsByTarget.values()) {
            request.getTimeoutTask().cancel();
        }
        requestsByTarget.clear();
        outgoingTargetByRequester.clear();
        rtpCooldownUntilMillis.clear();
    }

    private void sendClickableRequestPrompt(Player target, String requesterName, RequestType type) {
        if (type == RequestType.TPA_HERE) {
            target.sendMessage(TextUtil.colorize("&e" + requesterName + " wants you to teleport to them."));
        } else {
            target.sendMessage(TextUtil.colorize("&e" + requesterName + " wants to teleport to you."));
        }

        TextComponent accept = new TextComponent(TextUtil.colorize("&a[ACCEPT] "));
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept"));
        accept.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(TextUtil.colorize("&aClick to accept the request")).create()
        ));

        TextComponent deny = new TextComponent(TextUtil.colorize("&c[DENY]"));
        deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny"));
        deny.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(TextUtil.colorize("&cClick to deny the request")).create()
        ));

        TextComponent spacer = new TextComponent(TextUtil.colorize("&7 "));
        TextComponent line = new TextComponent();
        line.addExtra(accept);
        line.addExtra(spacer);
        line.addExtra(deny);

        target.spigot().sendMessage(line);
    }

    private enum RequestType {
        TPA,
        TPA_HERE
    }
}
