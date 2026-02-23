package com.smpcore.message;

import com.smpcore.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class MessageManager {
    private final JavaPlugin plugin;
    private FileConfiguration messages;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public String get(String path, String fallback) {
        return Text.c(messages == null ? fallback : messages.getString(path, fallback));
    }

    public void send(CommandSender sender, String path, String fallback) {
        sender.sendMessage(get(path, fallback));
    }
}
