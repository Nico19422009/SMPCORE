package com.smpcore.config;

import com.smpcore.model.CurrencyDefinition;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        CurrencyDefinition primary = new CurrencyDefinition(
                config.getString("currencies.primary.id", "coins"),
                config.getString("currencies.primary.singular", "Coin"),
                config.getString("currencies.primary.plural", "Coins"),
                config.getString("currencies.primary.symbol", "$")
        );

        List<CurrencyDefinition> secondary = new ArrayList<>();
        for (Map<?, ?> row : config.getMapList("currencies.secondary")) {
            secondary.add(new CurrencyDefinition(
                    String.valueOf(row.getOrDefault("id", "currency")),
                    String.valueOf(row.getOrDefault("singular", "Unit")),
                    String.valueOf(row.getOrDefault("plural", "Units")),
                    String.valueOf(row.getOrDefault("symbol", "*"))
            ));
        }

        return new SmpSettings(
                config.getString("serverProfile.name", "My SMP"),
                config.getBoolean("sidebar.enabled", true),
                config.getString("sidebar.title", "&6SMP Setup"),
                Math.max(20L, config.getLong("sidebar.updateIntervalTicks", 40L)),
                config.getBoolean("features.economy", true),
                config.getBoolean("features.trading", true),
                config.getBoolean("features.auctionhouse", true),
                primary,
                secondary,
                config.getDouble("economy.startingBalance", 0.0),
                config.getDouble("auctionhouse.listingFee", 5.0),
                config.getDouble("auctionhouse.taxPercent", 10.0)
        );
    }
}
