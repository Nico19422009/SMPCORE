package com.smpcore.command;

import com.smpcore.config.ConfigManager;
import com.smpcore.config.SmpSettings;
import com.smpcore.sidebar.SidebarManager;
import com.smpcore.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SmpConfigCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final SidebarManager sidebarManager;

    public SmpConfigCommand(JavaPlugin plugin, ConfigManager configManager, SidebarManager sidebarManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.sidebarManager = sidebarManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smpcore.admin")) {
            sender.sendMessage(Text.c("&cYou do not have permission to use this command."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Text.c("&eUsage: /" + label + " <reload|status|set>"));
            return true;
        }

        String sub = args[0].toLowerCase();
        if ("reload".equals(sub)) {
            configManager.reload();
            sidebarManager.start();
            sender.sendMessage(Text.c("&aSMPCORE configuration reloaded."));
            return true;
        }

        if ("status".equals(sub)) {
            SmpSettings settings = configManager.settings();
            for (String line : settings.statusText()) {
                sender.sendMessage(Text.c(line));
            }
            return true;
        }

        if ("set".equals(sub)) {
            if (args.length < 3) {
                sender.sendMessage(Text.c("&eUsage: /" + label + " set <path> <value>"));
                return true;
            }
            String path = args[1];
            String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            Object parsed = parseValue(value);
            plugin.getConfig().set(path, parsed);
            plugin.saveConfig();
            configManager.reload();
            sidebarManager.start();
            sender.sendMessage(Text.c("&aUpdated config path &f" + path + "&a = &f" + parsed));
            return true;
        }

        sender.sendMessage(Text.c("&cUnknown subcommand. Use /" + label + " <reload|status|set>"));
        return true;
    }

    private Object parseValue(String input) {
        if ("true".equalsIgnoreCase(input) || "false".equalsIgnoreCase(input)) {
            return Boolean.parseBoolean(input);
        }
        try {
            if (input.contains(".")) {
                return Double.parseDouble(input);
            }
            return Integer.parseInt(input);
        } catch (NumberFormatException ignored) {
            return input;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String start = args[0].toLowerCase();
            List<String> all = Arrays.asList("reload", "status", "set");
            List<String> out = new ArrayList<String>();
            for (String option : all) {
                if (option.startsWith(start)) {
                    out.add(option);
                }
            }
            return out;
        }
        return new ArrayList<String>();
    }
}
