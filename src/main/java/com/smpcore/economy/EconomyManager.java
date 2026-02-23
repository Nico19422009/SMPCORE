package com.smpcore.economy;

import com.smpcore.config.ConfigManager;
import com.smpcore.config.SmpSettings;
import com.smpcore.model.CurrencyDefinition;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class EconomyManager {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private File file;
    private FileConfiguration data;

    public EconomyManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void load() {
        this.file = new File(plugin.getDataFolder(), "balances.yml");
        if (!file.exists()) {
            plugin.saveResource("balances.yml", false);
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save balances.yml: " + e.getMessage());
        }
    }

    public double getBalance(UUID playerId, String currencyId) {
        String key = "balances." + playerId + "." + currencyId.toLowerCase();
        if (!data.contains(key)) {
            double start = configManager.settings().startingBalance();
            data.set(key, start);
            save();
            return start;
        }
        return data.getDouble(key, 0.0);
    }

    public void setBalance(UUID playerId, String currencyId, double amount) {
        data.set("balances." + playerId + "." + currencyId.toLowerCase(), Math.max(0.0, amount));
        save();
    }

    public boolean has(UUID playerId, String currencyId, double amount) {
        return getBalance(playerId, currencyId) >= amount;
    }

    public boolean withdraw(UUID playerId, String currencyId, double amount) {
        if (amount < 0 || !has(playerId, currencyId, amount)) {
            return false;
        }
        setBalance(playerId, currencyId, getBalance(playerId, currencyId) - amount);
        return true;
    }

    public void deposit(UUID playerId, String currencyId, double amount) {
        if (amount < 0) {
            return;
        }
        setBalance(playerId, currencyId, getBalance(playerId, currencyId) + amount);
    }

    public String format(double amount, CurrencyDefinition currency) {
        String unit = amount == 1.0 ? currency.singular() : currency.plural();
        return currency.symbol() + String.format("%.2f", amount) + " " + unit;
    }

    public boolean enabled() {
        return configManager.settings().economyEnabled();
    }

    public SmpSettings settings() {
        return configManager.settings();
    }

    public String defaultCurrencyId() {
        return settings().primaryCurrency().id();
    }

    public CurrencyDefinition findCurrency(String id) {
        return settings().findCurrency(id);
    }

    public CurrencyDefinition primaryCurrency() {
        return settings().primaryCurrency();
    }

    public Player findOnlinePlayer(String name) {
        return plugin.getServer().getPlayerExact(name);
    }
}
