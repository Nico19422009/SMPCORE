package com.smpcore.config

import com.smpcore.model.CurrencyDefinition
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin

class ConfigManager(private val plugin: JavaPlugin) {
    private var _settings: SmpSettings? = null

    fun load() {
        plugin.saveDefaultConfig()
        plugin.reloadConfig()
        _settings = map(plugin.config)
    }

    fun reload() {
        plugin.reloadConfig()
        _settings = map(plugin.config)
    }

    fun settings(): SmpSettings {
        if (_settings == null) load()
        return _settings!!
    }

    fun findCurrency(id: String?): CurrencyDefinition? = settings().findCurrency(id)
    fun primaryCurrency(): CurrencyDefinition = settings().primaryCurrency

    private fun map(config: FileConfiguration): SmpSettings {
        val primary = CurrencyDefinition(
            config.getString("currencies.primary.id", "coins")!!,
            config.getString("currencies.primary.singular", "Coin")!!,
            config.getString("currencies.primary.plural", "Coins")!!,
            config.getString("currencies.primary.symbol", "$")!!
        )

        val secondary = config.getMapList("currencies.secondary").map {
            CurrencyDefinition(
                (it["id"] ?: "currency").toString(),
                (it["singular"] ?: "Unit").toString(),
                (it["plural"] ?: "Units").toString(),
                (it["symbol"] ?: "*").toString()
            )
        }

        return SmpSettings(
            config.getString("serverProfile.name", "My SMP")!!,
            config.getBoolean("sidebar.enabled", true),
            config.getString("sidebar.title", "&6SMP Setup")!!,
            maxOf(20L, config.getLong("sidebar.updateIntervalTicks", 40L)),
            config.getBoolean("features.economy", true),
            config.getBoolean("features.trading", true),
            config.getBoolean("features.auctionhouse", true),
            primary,
            secondary,
            config.getDouble("economy.startingBalance", 0.0),
            config.getDouble("auctionhouse.listingFee", 5.0),
            config.getDouble("auctionhouse.taxPercent", 10.0)
        )
    }
}
