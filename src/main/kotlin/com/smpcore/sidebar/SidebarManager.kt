package com.smpcore.sidebar

import com.smpcore.config.ConfigManager
import com.smpcore.config.SmpSettings
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Scoreboard
import java.util.UUID

class SidebarManager(private val plugin: JavaPlugin, private val configManager: ConfigManager) {
    private val objectiveName = "smpcore_status"
    private val scoreboards = mutableMapOf<UUID, Scoreboard>()
    private var refreshTask: BukkitTask? = null

    fun start() {
        stop()
        val settings = configManager.settings()
        if (!settings.sidebarEnabled) {
            clearAll(); return
        }
        refreshAll()
        refreshTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            Runnable { refreshAll() },
            settings.sidebarUpdateIntervalTicks,
            settings.sidebarUpdateIntervalTicks
        )
    }

    fun stop() {
        refreshTask?.cancel()
        refreshTask = null
    }

    fun onJoin(player: Player) {
        if (!configManager.settings().sidebarEnabled) return
        if (!player.hasPermission("smpcore.sidebar.view")) return
        applySidebar(player, configManager.settings())
    }

    fun onQuit(player: Player) {
        scoreboards.remove(player.uniqueId)
    }

    fun refreshAll() {
        val settings = configManager.settings()
        if (!settings.sidebarEnabled) {
            clearAll(); return
        }

        Bukkit.getOnlinePlayers().forEach { p ->
            if (p.hasPermission("smpcore.sidebar.view")) applySidebar(p, settings)
        }
    }

    fun clearAll() {
        val main = Bukkit.getScoreboardManager().mainScoreboard
        Bukkit.getOnlinePlayers().forEach { it.scoreboard = main }
        scoreboards.clear()
    }

    private fun applySidebar(player: Player, settings: SmpSettings) {
        val manager = Bukkit.getScoreboardManager()
        val scoreboard = scoreboards.computeIfAbsent(player.uniqueId) { manager.newScoreboard }

        var objective = scoreboard.getObjective(objectiveName)
        if (objective == null) {
            objective = scoreboard.registerNewObjective(objectiveName, Criteria.DUMMY, color(settings.sidebarTitle))
            objective.displaySlot = DisplaySlot.SIDEBAR
        } else {
            objective.displayName = color(settings.sidebarTitle)
        }

        scoreboard.entries.forEach { scoreboard.resetScores(it) }

        var score = settings.sidebarLines().size
        settings.sidebarLines().forEachIndexed { index, line ->
            val entry = ChatColor.values()[index].toString() + ChatColor.RESET
            var team = scoreboard.getTeam("line_$index")
            if (team == null) {
                team = scoreboard.registerNewTeam("line_$index")
                team.addEntry(entry)
            }
            team.prefix(color(line))
            objective.getScore(entry).score = score--
        }

        player.scoreboard = scoreboard
    }

    private fun color(text: String): String = ChatColor.translateAlternateColorCodes('&', text)
}
