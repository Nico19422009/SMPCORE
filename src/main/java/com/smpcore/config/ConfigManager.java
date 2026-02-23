package com.smpcore.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigManager {
    private final JavaPlugin plugin;
    private SmpSettings settings;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.settings = map(plugin.getConfig());
    }

    public void reload() {
        plugin.reloadConfig();
        this.settings = map(plugin.getConfig());
    }

    public SmpSettings settings() {
        if (settings == null) {
            load();
        }
        return settings;
    }

    private SmpSettings map(FileConfiguration config) {
        int secondaryCount = config.getMapList("currencies.secondary").size();

        return new SmpSettings(
                config.getString("serverProfile.name", "My SMP"),
                config.getBoolean("sidebar.enabled", true),
                config.getString("sidebar.title", "&6SMP Setup"),
                Math.max(20L, config.getLong("sidebar.updateIntervalTicks", 40L)),
                config.getBoolean("features.economy", true),
                config.getBoolean("features.trading", true),
                config.getBoolean("features.auctionhouse", true),
                config.getString("currencies.primary.plural", "Coins"),
                secondaryCount
        );
    }
}
