package com.smpcore;

import com.smpcore.command.SmpConfigCommand;
import com.smpcore.config.ConfigManager;
import com.smpcore.sidebar.SidebarListener;
import com.smpcore.sidebar.SidebarManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SMPCorePlugin extends JavaPlugin {
    private ConfigManager configManager;
    private SidebarManager sidebarManager;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.configManager.load();

        this.sidebarManager = new SidebarManager(this, configManager);
        this.sidebarManager.start();

        registerCommands();
        getServer().getPluginManager().registerEvents(new SidebarListener(sidebarManager), this);

        getLogger().info("SMPCORE enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (sidebarManager != null) {
            sidebarManager.stop();
            sidebarManager.clearAll();
        }
    }

    private void registerCommands() {
        PluginCommand command = getCommand("smpconfig");
        if (command == null) {
            throw new IllegalStateException("Command 'smpconfig' missing from plugin.yml");
        }

        SmpConfigCommand executor = new SmpConfigCommand(configManager, sidebarManager);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
