package com.smpcore.command;

import com.smpcore.config.ConfigManager;
import com.smpcore.config.SmpSettings;
import com.smpcore.sidebar.SidebarManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public final class SmpConfigCommand implements CommandExecutor, TabCompleter {
    private final ConfigManager configManager;
    private final SidebarManager sidebarManager;

    public SmpConfigCommand(ConfigManager configManager, SidebarManager sidebarManager) {
        this.configManager = configManager;
        this.sidebarManager = sidebarManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smpcore.admin")) {
            sender.sendMessage(color("&cYou do not have permission to use this command."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(color("&eUsage: /" + label + " <reload|status>"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                configManager.reload();
                sidebarManager.start();
                sender.sendMessage(color("&aSMPCORE configuration reloaded."));
                return true;
            }
            case "status" -> {
                SmpSettings settings = configManager.settings();
                for (String line : settings.statusText()) {
                    sender.sendMessage(color(line));
                }
                return true;
            }
            default -> {
                sender.sendMessage(color("&cUnknown subcommand. Use /" + label + " <reload|status>"));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "status").stream()
                    .filter(opt -> opt.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
