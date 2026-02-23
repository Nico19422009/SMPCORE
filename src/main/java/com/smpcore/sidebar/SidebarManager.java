package com.smpcore.sidebar;

import com.smpcore.config.ConfigManager;
import com.smpcore.config.SmpSettings;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SidebarManager {
    private static final String OBJECTIVE = "smpcore_status";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Map<UUID, Scoreboard> scoreboards = new HashMap<>();
    private BukkitTask refreshTask;

    public SidebarManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void start() {
        stop();
        SmpSettings settings = configManager.settings();
        if (!settings.sidebarEnabled()) {
            clearAll();
            return;
        }

        refreshAll();
        this.refreshTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::refreshAll,
                settings.sidebarUpdateIntervalTicks(),
                settings.sidebarUpdateIntervalTicks()
        );
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public void onJoin(Player player) {
        if (!configManager.settings().sidebarEnabled()) {
            return;
        }
        if (!player.hasPermission("smpcore.sidebar.view")) {
            return;
        }
        applySidebar(player, configManager.settings());
    }

    public void onQuit(Player player) {
        scoreboards.remove(player.getUniqueId());
    }

    public void refreshAll() {
        SmpSettings settings = configManager.settings();
        if (!settings.sidebarEnabled()) {
            clearAll();
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("smpcore.sidebar.view")) {
                continue;
            }
            applySidebar(player, settings);
        }
    }

    public void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        scoreboards.clear();
    }

    private void applySidebar(Player player, SmpSettings settings) {
        Scoreboard scoreboard = scoreboards.computeIfAbsent(
                player.getUniqueId(),
                ignored -> Bukkit.getScoreboardManager().getNewScoreboard()
        );

        Objective objective = scoreboard.getObjective(OBJECTIVE);
        if (objective == null) {
            objective = scoreboard.registerNewObjective(OBJECTIVE, "dummy", color(settings.sidebarTitle()));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            objective.setDisplayName(color(settings.sidebarTitle()));
        }

        reset(scoreboard);

        List<String> lines = settings.sidebarLines();
        int score = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String entry = ChatColor.values()[i].toString() + ChatColor.RESET;
            Team team = scoreboard.getTeam("line_" + i);
            if (team == null) {
                team = scoreboard.registerNewTeam("line_" + i);
                team.addEntry(entry);
            }
            team.prefix(color(lines.get(i)));
            objective.getScore(entry).setScore(score--);
        }

        player.setScoreboard(scoreboard);
    }

    private void reset(Scoreboard scoreboard) {
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
