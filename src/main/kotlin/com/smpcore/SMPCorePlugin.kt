package com.smpcore

import com.smpcore.auction.AuctionCommand
import com.smpcore.auction.AuctionHouseManager
import com.smpcore.command.SmpConfigCommand
import com.smpcore.config.ConfigManager
import com.smpcore.economy.EconomyCommand
import com.smpcore.economy.EconomyManager
import com.smpcore.message.MessageManager
import com.smpcore.sidebar.SidebarListener
import com.smpcore.sidebar.SidebarManager
import com.smpcore.trade.TradeCommand
import com.smpcore.trade.TradeManager
import org.bukkit.command.CommandExecutor
import org.bukkit.command.PluginCommand
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.java.JavaPlugin

class SMPCorePlugin : JavaPlugin() {
    private lateinit var configManager: ConfigManager
    private lateinit var sidebarManager: SidebarManager
    private lateinit var economyManager: EconomyManager
    private lateinit var auctionHouseManager: AuctionHouseManager
    private lateinit var tradeManager: TradeManager
    private lateinit var messageManager: MessageManager

    override fun onEnable() {
        configManager = ConfigManager(this)
        configManager.load()

        messageManager = MessageManager(this)
        messageManager.load()

        economyManager = EconomyManager(this, configManager)
        economyManager.load()

        auctionHouseManager = AuctionHouseManager(this, configManager, economyManager)
        auctionHouseManager.load()

        tradeManager = TradeManager(configManager, economyManager)

        sidebarManager = SidebarManager(this, configManager)
        sidebarManager.start()

        registerCommands()
        server.pluginManager.registerEvents(SidebarListener(sidebarManager), this)
        logger.info("SMPCORE Kotlin Paper plugin enabled.")
    }

    override fun onDisable() {
        if (::sidebarManager.isInitialized) {
            sidebarManager.stop()
            sidebarManager.clearAll()
        }
        if (::economyManager.isInitialized) economyManager.save()
        if (::auctionHouseManager.isInitialized) auctionHouseManager.save()
    }

    private fun registerCommands() {
        registerCommand("smpconfig", SmpConfigCommand(this, configManager, sidebarManager))

        val economyCommand = EconomyCommand(economyManager)
        registerCommand("balance", economyCommand)
        registerCommand("pay", economyCommand)
        registerCommand("eco", economyCommand)

        registerCommand("trade", TradeCommand(tradeManager))
        registerCommand("ah", AuctionCommand(auctionHouseManager, economyManager, configManager))
    }

    private fun registerCommand(name: String, executor: Any) {
        val cmd: PluginCommand = getCommand(name)
            ?: throw IllegalStateException("Command '$name' missing from plugin.yml")
        if (executor is CommandExecutor) cmd.setExecutor(executor)
        if (executor is TabCompleter) cmd.tabCompleter = executor
    }
}
