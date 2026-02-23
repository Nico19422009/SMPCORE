package com.smpcore.command

import com.smpcore.config.ConfigManager
import com.smpcore.sidebar.SidebarManager
import com.smpcore.util.Text
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.java.JavaPlugin

class SmpConfigCommand(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val sidebarManager: SidebarManager
) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("smpcore.admin")) {
            sender.sendMessage(Text.c("&cYou do not have permission to use this command.")); return true
        }

        if (args.isEmpty()) {
            sender.sendMessage(Text.c("&eUsage: /$label <reload|status|set>")); return true
        }

        when (args[0].lowercase()) {
            "reload" -> {
                configManager.reload()
                sidebarManager.start()
                sender.sendMessage(Text.c("&aSMPCORE configuration reloaded."))
            }
            "status" -> configManager.settings().statusText().forEach { sender.sendMessage(Text.c(it)) }
            "set" -> {
                if (args.size < 3) {
                    sender.sendMessage(Text.c("&eUsage: /$label set <path> <value>")); return true
                }
                val path = args[1]
                val value = args.drop(2).joinToString(" ")
                val parsed = parseValue(value)
                plugin.config.set(path, parsed)
                plugin.saveConfig()
                configManager.reload()
                sidebarManager.start()
                sender.sendMessage(Text.c("&aUpdated config path &f$path&a = &f$parsed"))
            }
            else -> sender.sendMessage(Text.c("&cUnknown subcommand. Use /$label <reload|status|set>"))
        }
        return true
    }

    private fun parseValue(input: String): Any {
        if (input.equals("true", true) || input.equals("false", true)) return input.toBoolean()
        return input.toIntOrNull() ?: input.toDoubleOrNull() ?: input
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        if (args.size == 1) {
            val options = listOf("reload", "status", "set")
            return options.filter { it.startsWith(args[0].lowercase()) }.toMutableList()
        }
        return mutableListOf()
    }
}
