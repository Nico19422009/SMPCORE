package com.smpcore.message

import com.smpcore.util.Text
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class MessageManager(private val plugin: JavaPlugin) {
    private var messages: FileConfiguration? = null

    fun load() {
        val file = File(plugin.dataFolder, "messages.yml")
        if (!file.exists()) plugin.saveResource("messages.yml", false)
        messages = YamlConfiguration.loadConfiguration(file)
    }

    fun get(path: String, fallback: String): String = Text.c(messages?.getString(path, fallback) ?: fallback)

    fun send(sender: CommandSender, path: String, fallback: String) {
        sender.sendMessage(get(path, fallback))
    }
}
