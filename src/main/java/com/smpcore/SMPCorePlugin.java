package com.smpcore;

import com.smpcore.auction.AuctionCommand;
import com.smpcore.auction.AuctionHouseManager;
import com.smpcore.command.SmpConfigCommand;
import com.smpcore.config.ConfigManager;
import com.smpcore.economy.EconomyCommand;
import com.smpcore.economy.EconomyManager;
import com.smpcore.message.MessageManager;
import com.smpcore.sidebar.SidebarListener;
import com.smpcore.sidebar.SidebarManager;
import com.smpcore.trade.TradeCommand;
import com.smpcore.trade.TradeManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SMPCorePlugin extends JavaPlugin {
    private ConfigManager configManager;
    private SidebarManager sidebarManager;
    private EconomyManager economyManager;
    private AuctionHouseManager auctionHouseManager;
    private TradeManager tradeManager;
    private MessageManager messageManager;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.configManager.load();

        this.messageManager = new MessageManager(this);
        this.messageManager.load();

        this.economyManager = new EconomyManager(this, configManager);
        this.economyManager.load();

        this.auctionHouseManager = new AuctionHouseManager(this, configManager, economyManager);
        this.auctionHouseManager.load();

        this.tradeManager = new TradeManager(configManager, economyManager);

        this.sidebarManager = new SidebarManager(this, configManager);
        this.sidebarManager.start();

        registerCommands();
        getServer().getPluginManager().registerEvents(new SidebarListener(sidebarManager), this);

        getLogger().info("SMPCORE v1 foundation enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (sidebarManager != null) {
            sidebarManager.stop();
            sidebarManager.clearAll();
        }
        if (economyManager != null) {
            economyManager.save();
        }
        if (auctionHouseManager != null) {
            auctionHouseManager.save();
        }
    }

    private void registerCommands() {
        registerCommand("smpconfig", new SmpConfigCommand(this, configManager, sidebarManager));

        EconomyCommand economyCommand = new EconomyCommand(economyManager);
        registerCommand("balance", economyCommand);
        registerCommand("pay", economyCommand);
        registerCommand("eco", economyCommand);

        registerCommand("trade", new TradeCommand(tradeManager));
        registerCommand("ah", new AuctionCommand(auctionHouseManager, economyManager, configManager));
    }

    private void registerCommand(String name, Object executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            throw new IllegalStateException("Command '" + name + "' missing from plugin.yml");
        }
        if (executor instanceof org.bukkit.command.CommandExecutor ce) {
            cmd.setExecutor(ce);
        }
        if (executor instanceof org.bukkit.command.TabCompleter tc) {
            cmd.setTabCompleter(tc);
        }
    }
}
