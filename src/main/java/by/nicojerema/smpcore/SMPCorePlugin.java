package by.nicojerema.smpcore;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SMPCorePlugin extends JavaPlugin {
    private PlayerDataStore playerDataStore;
    private SidebarService sidebarService;
    private TeleportCommand teleportCommand;
    private RtpNpcCommand rtpNpcCommand;
    private ShopCommand shopCommand;
    private SellCommand sellCommand;
    private PayCommand payCommand;
    private InvSeeCommand invSeeCommand;
    private RankEditorMenu rankEditorMenu;
    private HomeCommand homeCommand;
    private SettingsCommand settingsCommand;
    private StatsCommand statsCommand;
    private CurrencyEditorMenu currencyEditorMenu;
    private BukkitTask updateTask;
    private UpdateService updateService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.updateService = new UpdateService(this);

        this.playerDataStore = new PlayerDataStore(this);
        this.sidebarService = new SidebarService(this, playerDataStore);
        this.teleportCommand = new TeleportCommand(this);
        this.rtpNpcCommand = new RtpNpcCommand(this, teleportCommand);
        this.shopCommand = new ShopCommand(this);
        this.sellCommand = new SellCommand(this);
        this.payCommand = new PayCommand(this);
        this.invSeeCommand = new InvSeeCommand(this);
        this.rankEditorMenu = new RankEditorMenu(this);
        this.homeCommand = new HomeCommand(this);
        this.settingsCommand = new SettingsCommand(this);
        this.statsCommand = new StatsCommand(this);
        this.currencyEditorMenu = new CurrencyEditorMenu(this);
        this.sellCommand.ensureMaterialWorthList();

        registerCommands();
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(teleportCommand, this);
        getServer().getPluginManager().registerEvents(rtpNpcCommand, this);
        getServer().getPluginManager().registerEvents(shopCommand, this);
        getServer().getPluginManager().registerEvents(sellCommand, this);
        getServer().getPluginManager().registerEvents(invSeeCommand, this);
        getServer().getPluginManager().registerEvents(rankEditorMenu, this);
        getServer().getPluginManager().registerEvents(homeCommand, this);
        getServer().getPluginManager().registerEvents(settingsCommand, this);
        getServer().getPluginManager().registerEvents(statsCommand, this);
        getServer().getPluginManager().registerEvents(currencyEditorMenu, this);

        Bukkit.getOnlinePlayers().forEach(player -> playerDataStore.ensurePlayer(player.getUniqueId()));
        restartUpdateTask();
        sidebarService.refreshAll();
    }

    @Override
    public void onDisable() {
        if (teleportCommand != null) {
            teleportCommand.shutdown();
        }
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        if (playerDataStore != null) {
            playerDataStore.save();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        sellCommand.ensureMaterialWorthList();
        if (updateService != null) {
            updateService.reloadConfig();
        }
        restartUpdateTask();
        sidebarService.reloadFromConfig();
    }

    public PlayerDataStore getPlayerDataStore() {
        return playerDataStore;
    }

    public SidebarService getSidebarService() {
        return sidebarService;
    }

    public RankEditorMenu getRankEditorMenu() {
        return rankEditorMenu;
    }

    public CurrencyEditorMenu getCurrencyEditorMenu() {
        return currencyEditorMenu;
    }

    public boolean isSecondaryCurrencyEnabled() {
        return getConfig().getBoolean("currency.secondary.enabled", false);
    }

    public void setSecondaryCurrencyEnabled(boolean enabled) {
        getConfig().set("currency.secondary.enabled", enabled);
    }

    public String getSecondaryCurrencyDisplayName() {
        return getConfig().getString("currency.secondary.display-name", "Tokens");
    }

    public boolean isEconomyEnabled() {
        boolean hasEconomyFlag = getConfig().contains("economy.enabled");
        boolean hasLegacyFlag = getConfig().contains("coins-system.enabled");

        boolean economyEnabled = hasEconomyFlag && getConfig().getBoolean("economy.enabled", true);
        boolean legacyEnabled = hasLegacyFlag && getConfig().getBoolean("coins-system.enabled", true);

        if (hasEconomyFlag && hasLegacyFlag) {
            return economyEnabled && legacyEnabled;
        }
        if (hasEconomyFlag) {
            return economyEnabled;
        }
        if (hasLegacyFlag) {
            return legacyEnabled;
        }
        return true;
    }

    public String getEconomyDisabledMessage() {
        return TextUtil.colorize(getConfig().getString("economy.disabled-message", "&cEconomy is disabled on this server."));
    }

    public void setEconomyEnabled(boolean enabled) {
        getConfig().set("economy.enabled", enabled);
        getConfig().set("coins-system.enabled", enabled);
        saveConfig();
    }

    public boolean rankExists(String rank) {
        return resolveRankKey(rank) != null;
    }

    public UpdateService getUpdateService() {
        return updateService;
    }

    public String resolveRankKey(String rankInput) {
        if (rankInput == null) {
            return null;
        }

        String requested = rankInput.trim();
        if (requested.isEmpty()) {
            return null;
        }

        ConfigurationSection ranks = getConfig().getConfigurationSection("ranks");
        if (ranks == null) {
            return null;
        }

        for (String key : ranks.getKeys(false)) {
            if (key.equalsIgnoreCase(requested)) {
                return key.toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    public List<String> getConfiguredRankKeys() {
        ConfigurationSection ranks = getConfig().getConfigurationSection("ranks");
        if (ranks == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(ranks.getKeys(false));
    }

    private void registerCommands() {
        PluginCommand smpcore = getCommand("smpcore");
        if (smpcore == null) {
            getLogger().severe("Could not register /smpcore command from plugin.yml");
        } else {
            SMPCoreCommand adminCommand = new SMPCoreCommand(this);
            smpcore.setExecutor(adminCommand);
            smpcore.setTabCompleter(adminCommand);
        }

        registerTeleportCommand("tpa");
        registerTeleportCommand("tpahere");
        registerTeleportCommand("tpaccept");
        registerTeleportCommand("tpdeny");
        registerTeleportCommand("tpcancel");
        registerTeleportCommand("rtp");
        registerRtpNpcCommand("rtpnpc");
        registerShopCommand("shop");
        registerShopCommand("shopedit");
        registerSellCommand("sell");
        registerPayCommand("pay");
        registerInvSeeCommand("invsee");
        registerInvSeeCommand("echestsee");
        registerHomeCommand("sethome");
        registerHomeCommand("home");
        registerHomeCommand("delhome");
        registerSettingsCommand("settings");
        registerStatsCommand("stats");
    }

    private void registerTeleportCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Could not register /" + name + " command from plugin.yml");
            return;
        }
        command.setExecutor(teleportCommand);
        command.setTabCompleter(teleportCommand);
    }

    private void registerShopCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Could not register /" + name + " command from plugin.yml");
            return;
        }
        command.setExecutor(shopCommand);
        command.setTabCompleter(shopCommand);
    }

    private void registerRtpNpcCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Could not register /" + name + " command from plugin.yml");
            return;
        }
        command.setExecutor(rtpNpcCommand);
        command.setTabCompleter(rtpNpcCommand);
    }

    private void registerSellCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Could not register /" + name + " command from plugin.yml");
            return;
        }
        command.setExecutor(sellCommand);
        command.setTabCompleter(sellCommand);
    }

    private void registerPayCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Could not register /" + name + " command from plugin.yml");
            return;
        }
        command.setExecutor(payCommand);
        command.setTabCompleter(payCommand);
    }

    private void registerInvSeeCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Could not register /" + name + " command from plugin.yml");
            return;
        }
        command.setExecutor(invSeeCommand);
        command.setTabCompleter(invSeeCommand);
    }

    private void registerHomeCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Could not register /" + name + " command from plugin.yml");
            return;
        }
        command.setExecutor(homeCommand);
        command.setTabCompleter(homeCommand);
    }

    private void registerSettingsCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Could not register /" + name + " command from plugin.yml");
            return;
        }
        command.setExecutor(settingsCommand);
        command.setTabCompleter(settingsCommand);
    }

    private void registerStatsCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Could not register /" + name + " command from plugin.yml");
            return;
        }
        command.setExecutor(statsCommand);
        command.setTabCompleter(statsCommand);
    }

    private void restartUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        long interval = Math.max(20L, getConfig().getLong("update-interval-ticks", 20L));
        updateTask = Bukkit.getScheduler().runTaskTimer(this, sidebarService::refreshAll, 1L, interval);
    }
}
