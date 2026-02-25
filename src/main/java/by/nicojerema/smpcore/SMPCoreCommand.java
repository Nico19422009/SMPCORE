package by.nicojerema.smpcore;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class SMPCoreCommand implements CommandExecutor, TabCompleter {
    private final SMPCorePlugin plugin;

    public SMPCoreCommand(SMPCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smpcore.admin")) {
            sender.sendMessage(TextUtil.colorize("&cYou do not have permission to use this command."));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload":
                plugin.reloadPlugin();
                sender.sendMessage(TextUtil.colorize("&aSMPCORE config reloaded."));
                sender.sendMessage(TextUtil.colorize("&7Loaded ranks: &f" + plugin.getConfiguredRankKeys().size()));
                break;
            case "update":
                UpdateService updateService = plugin.getUpdateService();
                if (updateService == null) {
                    sender.sendMessage(TextUtil.colorize("&cUpdate service is not available right now."));
                    break;
                }
                updateService.downloadLatestRelease(sender);
                break;
            case "economy":
                handleEconomy(sender, args);
                break;
            case "rankmenu":
            case "rankeditor":
            case "ranks":
                handleRankMenu(sender);
                break;
            case "currency":
                handleCurrencyMenu(sender);
                break;
            case "worlds":
                handleWorlds(sender);
                break;
            case "setrank":
                handleSetRank(sender, args);
                break;
            case "setcoins":
                handleSetCoins(sender, args);
                break;
            case "setsecondary":
                handleSetSecondary(sender, args);
                break;
            case "addcoins":
                handleAddCoins(sender, args);
                break;
            case "addsecondary":
                handleAddSecondary(sender, args);
                break;
            default:
                sendUsage(sender);
                break;
        }
        return true;
    }

    private void handleSetRank(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /smpcore setrank <player> <rank>"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(TextUtil.colorize("&cPlayer not found online."));
            return;
        }

        String rank = plugin.resolveRankKey(args[2]);
        if (rank == null) {
            sender.sendMessage(TextUtil.colorize("&cUnknown rank in config: &f" + args[2]));
            return;
        }

        plugin.getPlayerDataStore().setRank(target.getUniqueId(), rank);
        plugin.getPlayerDataStore().save();
        plugin.getSidebarService().refreshAll();

        sender.sendMessage(TextUtil.colorize("&aSet rank of &f" + target.getName() + " &ato &f" + rank + "&a."));
    }

    private void handleSetCoins(CommandSender sender, String[] args) {
        if (!plugin.isEconomyEnabled()) {
            sender.sendMessage(plugin.getEconomyDisabledMessage());
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /smpcore setcoins <player> <amount>"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(TextUtil.colorize("&cPlayer not found online."));
            return;
        }

        long value;
        try {
            value = Long.parseLong(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(TextUtil.colorize("&cAmount must be a number."));
            return;
        }

        plugin.getPlayerDataStore().setCoins(target.getUniqueId(), value);
        plugin.getPlayerDataStore().save();
        plugin.getSidebarService().refreshAll();

        sender.sendMessage(TextUtil.colorize(
                "&aSet coins of &f" + target.getName() + " &ato &f" + TextUtil.formatCoins(Math.max(0L, value)) + "&a."
        ));
    }

    private void handleAddCoins(CommandSender sender, String[] args) {
        if (!plugin.isEconomyEnabled()) {
            sender.sendMessage(plugin.getEconomyDisabledMessage());
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /smpcore addcoins <player> <amount>"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(TextUtil.colorize("&cPlayer not found online."));
            return;
        }

        long value;
        try {
            value = Long.parseLong(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(TextUtil.colorize("&cAmount must be a number."));
            return;
        }

        long current = plugin.getPlayerDataStore().getCoins(target.getUniqueId());
        long updated = Math.max(0L, current + value);

        plugin.getPlayerDataStore().setCoins(target.getUniqueId(), updated);
        plugin.getPlayerDataStore().save();
        plugin.getSidebarService().refreshAll();

        sender.sendMessage(TextUtil.colorize(
                "&aUpdated coins of &f" + target.getName() + " &ato &f" + TextUtil.formatCoins(updated) + "&a."
        ));
    }

    private void handleSetSecondary(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /smpcore setsecondary <player> <amount>"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(TextUtil.colorize("&cPlayer not found online."));
            return;
        }

        long value;
        try {
            value = Long.parseLong(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(TextUtil.colorize("&cAmount must be a number."));
            return;
        }

        plugin.getPlayerDataStore().setSecondaryCurrency(target.getUniqueId(), value);
        plugin.getPlayerDataStore().save();
        plugin.getSidebarService().refreshAll();

        sender.sendMessage(TextUtil.colorize(
                "&aSet " + plugin.getSecondaryCurrencyDisplayName() + " of &f" + target.getName()
                        + " &ato &f" + TextUtil.formatCoins(Math.max(0L, value)) + "&a."
        ));
    }

    private void handleAddSecondary(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /smpcore addsecondary <player> <amount>"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(TextUtil.colorize("&cPlayer not found online."));
            return;
        }

        long value;
        try {
            value = Long.parseLong(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(TextUtil.colorize("&cAmount must be a number."));
            return;
        }

        long current = plugin.getPlayerDataStore().getSecondaryCurrency(target.getUniqueId());
        long updated = Math.max(0L, current + value);

        plugin.getPlayerDataStore().setSecondaryCurrency(target.getUniqueId(), updated);
        plugin.getPlayerDataStore().save();
        plugin.getSidebarService().refreshAll();

        sender.sendMessage(TextUtil.colorize(
                "&aUpdated " + plugin.getSecondaryCurrencyDisplayName() + " of &f" + target.getName()
                        + " &ato &f" + TextUtil.formatCoins(updated) + "&a."
        ));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(TextUtil.colorize("&eSMPCORE commands:"));
        sender.sendMessage(TextUtil.colorize("&f/smpcore reload"));
        sender.sendMessage(TextUtil.colorize("&f/smpcore economy <on|off|status>"));
        sender.sendMessage(TextUtil.colorize("&f/smpcore rankmenu"));
        sender.sendMessage(TextUtil.colorize("&f/smpcore currency"));
        sender.sendMessage(TextUtil.colorize("&f/smpcore worlds"));
        sender.sendMessage(TextUtil.colorize("&f/smpcore setrank <player> <rank>"));
        sender.sendMessage(TextUtil.colorize("&f/smpcore setcoins <player> <amount>"));
        sender.sendMessage(TextUtil.colorize("&f/smpcore addcoins <player> <amount>"));
        sender.sendMessage(TextUtil.colorize("&f/smpcore setsecondary <player> <amount>"));
        sender.sendMessage(TextUtil.colorize("&f/smpcore addsecondary <player> <amount>"));
        sender.sendMessage(TextUtil.colorize("&f/smpcore update"));
    }

    private void handleRankMenu(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextUtil.colorize("&cOnly players can use /smpcore rankmenu."));
            return;
        }

        Player player = (Player) sender;
        plugin.getRankEditorMenu().openMenu(player);
    }

    private void handleCurrencyMenu(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextUtil.colorize("&cOnly players can use /smpcore currency."));
            return;
        }

        Player player = (Player) sender;
        plugin.getCurrencyEditorMenu().openMenu(player);
    }

    private void handleWorlds(CommandSender sender) {
        List<String> loaded = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            loaded.add(world.getName());
        }

        sender.sendMessage(TextUtil.colorize("&eLoaded worlds (" + loaded.size() + "):"));
        if (loaded.isEmpty()) {
            sender.sendMessage(TextUtil.colorize("&cNo worlds are currently loaded."));
        } else {
            sender.sendMessage(TextUtil.colorize("&f" + String.join("&7, &f", loaded)));
        }

        List<String> multiverse = scanMultiverseWorlds();
        if (!multiverse.isEmpty()) {
            Set<String> extra = new LinkedHashSet<>(multiverse);
            extra.removeAll(loaded);
            if (!extra.isEmpty()) {
                sender.sendMessage(TextUtil.colorize("&eMultiverse worlds (defined but not loaded?):"));
                sender.sendMessage(TextUtil.colorize("&f" + String.join("&7, &f", extra)));
            }
        }
    }

    private List<String> scanMultiverseWorlds() {
        Plugin mvPlugin = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (mvPlugin == null) {
            return Collections.emptyList();
        }

        try {
            Method getManager = mvPlugin.getClass().getMethod("getMVWorldManager");
            Object manager = getManager.invoke(mvPlugin);
            if (manager == null) {
                return Collections.emptyList();
            }

            Method getWorlds = manager.getClass().getMethod("getMVWorlds");
            Object rawWorlds = getWorlds.invoke(manager);
            if (!(rawWorlds instanceof Collection)) {
                return Collections.emptyList();
            }

            List<String> names = new ArrayList<>();
            Method nameGetter = null;
            for (Object mvWorld : (Collection<?>) rawWorlds) {
                if (mvWorld == null) {
                    continue;
                }
                if (nameGetter == null) {
                    nameGetter = mvWorld.getClass().getMethod("getName");
                }
                Object result = nameGetter.invoke(mvWorld);
                if (result instanceof String) {
                    names.add((String) result);
                }
            }
            return names;
        } catch (ReflectiveOperationException ex) {
            return Collections.emptyList();
        }
    }

    private void handleEconomy(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /smpcore economy <on|off|status>"));
            return;
        }

        String mode = args[1].toLowerCase(Locale.ROOT);
        switch (mode) {
            case "status":
                sender.sendMessage(TextUtil.colorize("&eEconomy is currently: "
                        + (plugin.isEconomyEnabled() ? "&aENABLED" : "&cDISABLED")));
                break;
            case "on":
            case "enable":
            case "enabled":
                plugin.setEconomyEnabled(true);
                sender.sendMessage(TextUtil.colorize("&aEconomy enabled. /pay, /sell and /shop are active."));
                sender.sendMessage(TextUtil.colorize("&7Applies immediately and also after server restart."));
                break;
            case "off":
            case "disable":
            case "disabled":
                plugin.setEconomyEnabled(false);
                sender.sendMessage(TextUtil.colorize("&cEconomy disabled. /pay, /sell and /shop are blocked."));
                sender.sendMessage(TextUtil.colorize("&7Saved in config and will stay disabled after server restart."));
                break;
            default:
                sender.sendMessage(TextUtil.colorize("&cUsage: /smpcore economy <on|off|status>"));
                break;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smpcore.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            subcommands.add("reload");
            subcommands.add("economy");
            subcommands.add("rankmenu");
            subcommands.add("currency");
            subcommands.add("worlds");
            subcommands.add("setrank");
            subcommands.add("setcoins");
            subcommands.add("addcoins");
            subcommands.add("setsecondary");
            subcommands.add("addsecondary");
            subcommands.add("update");
            return subcommands.stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("setrank")
                || args[0].equalsIgnoreCase("setcoins")
                || args[0].equalsIgnoreCase("addcoins")
                || args[0].equalsIgnoreCase("setsecondary")
                || args[0].equalsIgnoreCase("addsecondary"))) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("setrank")) {
            String input = args[2].toLowerCase(Locale.ROOT);
            if (!plugin.getConfig().isConfigurationSection("ranks")) {
                return Collections.emptyList();
            }

            List<String> ranks = new ArrayList<>(plugin.getConfig().getConfigurationSection("ranks").getKeys(false));
            return ranks.stream()
                    .filter(rank -> rank.toLowerCase(Locale.ROOT).startsWith(input))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("economy")) {
            List<String> options = new ArrayList<>();
            options.add("on");
            options.add("off");
            options.add("status");
            String input = args[1].toLowerCase(Locale.ROOT);
            return options.stream()
                    .filter(option -> option.startsWith(input))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
