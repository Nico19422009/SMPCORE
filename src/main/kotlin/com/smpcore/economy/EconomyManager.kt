package com.smpcore.economy

import com.smpcore.config.ConfigManager
import com.smpcore.config.SmpSettings
import com.smpcore.model.CurrencyDefinition
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException
import java.util.UUID

class EconomyManager(private val plugin: JavaPlugin, private val configManager: ConfigManager) {
    private lateinit var file: File
    private lateinit var data: FileConfiguration

    fun load() {
        file = File(plugin.dataFolder, "balances.yml")
        if (!file.exists()) plugin.saveResource("balances.yml", false)
        data = YamlConfiguration.loadConfiguration(file)
    }

    fun save() {
        try {
            data.save(file)
        } catch (e: IOException) {
            plugin.logger.severe("Failed to save balances.yml: ${e.message}")
        }
    }

    fun getBalance(playerId: UUID, currencyId: String): Double {
        val key = "balances.$playerId.${currencyId.lowercase()}"
        if (!data.contains(key)) {
            val start = configManager.settings().startingBalance
            data.set(key, start)
            save()
            return start
        }
        return data.getDouble(key, 0.0)
    }

    fun setBalance(playerId: UUID, currencyId: String, amount: Double) {
        data.set("balances.$playerId.${currencyId.lowercase()}", maxOf(0.0, amount))
        save()
    }

    fun has(playerId: UUID, currencyId: String, amount: Double): Boolean = getBalance(playerId, currencyId) >= amount

    fun withdraw(playerId: UUID, currencyId: String, amount: Double): Boolean {
        if (amount < 0 || !has(playerId, currencyId, amount)) return false
        setBalance(playerId, currencyId, getBalance(playerId, currencyId) - amount)
        return true
    }

    fun deposit(playerId: UUID, currencyId: String, amount: Double) {
        if (amount < 0) return
        setBalance(playerId, currencyId, getBalance(playerId, currencyId) + amount)
    }

    fun format(amount: Double, currency: CurrencyDefinition): String {
        val unit = if (amount == 1.0) currency.singular else currency.plural
        return currency.symbol + String.format("%.2f", amount) + " " + unit
    }

    fun enabled(): Boolean = configManager.settings().economyEnabled
    fun settings(): SmpSettings = configManager.settings()
    fun defaultCurrencyId(): String = settings().primaryCurrency.id
    fun findCurrency(id: String?): CurrencyDefinition? = settings().findCurrency(id)
    fun primaryCurrency(): CurrencyDefinition = settings().primaryCurrency

    fun findOnlinePlayer(name: String): Player? = plugin.server.getPlayerExact(name)
}
