package by.nicojerema.smpcore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PlayerDataStore {
    private final SMPCorePlugin plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;

    public PlayerDataStore(SMPCorePlugin plugin) {
        this.plugin = plugin;

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
        }

        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                if (!dataFile.createNewFile()) {
                    plugin.getLogger().warning("Could not create data.yml");
                }
            } catch (IOException ex) {
                plugin.getLogger().severe("Failed to create data.yml: " + ex.getMessage());
            }
        }

        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public synchronized boolean ensurePlayer(UUID uuid) {
        String basePath = "players." + uuid;
        boolean firstJoin = !dataConfig.contains(basePath);

        if (!dataConfig.contains(basePath + ".coins")) {
            dataConfig.set(basePath + ".coins", 0L);
        }

        if (!dataConfig.contains(basePath + ".rank")) {
            dataConfig.set(basePath + ".rank", getDefaultRank());
        }

        if (!dataConfig.contains(basePath + ".settings.tpa-notifications")) {
            dataConfig.set(basePath + ".settings.tpa-notifications", true);
        }

        if (!dataConfig.contains(basePath + ".settings.night-vision")) {
            dataConfig.set(basePath + ".settings.night-vision", false);
        }

        if (!dataConfig.contains(basePath + ".settings.sidebar-secondary")) {
            dataConfig.set(basePath + ".settings.sidebar-secondary", false);
        }

        if (!dataConfig.contains(basePath + ".currency.secondary")) {
            dataConfig.set(basePath + ".currency.secondary", 0L);
        }

        return firstJoin;
    }

    public synchronized long getCoins(UUID uuid) {
        ensurePlayer(uuid);
        return dataConfig.getLong("players." + uuid + ".coins", 0L);
    }

    public synchronized void setCoins(UUID uuid, long amount) {
        ensurePlayer(uuid);
        dataConfig.set("players." + uuid + ".coins", Math.max(0L, amount));
    }

    public synchronized long getSecondaryCurrency(UUID uuid) {
        ensurePlayer(uuid);
        return dataConfig.getLong("players." + uuid + ".currency.secondary", 0L);
    }

    public synchronized void setSecondaryCurrency(UUID uuid, long amount) {
        ensurePlayer(uuid);
        dataConfig.set("players." + uuid + ".currency.secondary", Math.max(0L, amount));
    }

    public synchronized String getRank(UUID uuid) {
        ensurePlayer(uuid);
        String rank = dataConfig.getString("players." + uuid + ".rank", getDefaultRank());
        if (rank == null || rank.trim().isEmpty()) {
            return getDefaultRank();
        }
        return rank.toLowerCase(Locale.ROOT);
    }

    public synchronized void setRank(UUID uuid, String rank) {
        ensurePlayer(uuid);
        String normalizedRank = rank == null ? getDefaultRank() : rank.toLowerCase(Locale.ROOT);
        dataConfig.set("players." + uuid + ".rank", normalizedRank);
    }

    public synchronized void setHome(UUID uuid, int slot, Location location) {
        ensurePlayer(uuid);
        if (slot < 1 || slot > 3 || location == null || location.getWorld() == null) {
            return;
        }

        String path = "players." + uuid + ".homes." + slot;
        dataConfig.set(path + ".world", location.getWorld().getName());
        dataConfig.set(path + ".x", location.getX());
        dataConfig.set(path + ".y", location.getY());
        dataConfig.set(path + ".z", location.getZ());
        dataConfig.set(path + ".yaw", location.getYaw());
        dataConfig.set(path + ".pitch", location.getPitch());
    }

    public synchronized Location getHome(UUID uuid, int slot) {
        ensurePlayer(uuid);
        if (slot < 1 || slot > 3) {
            return null;
        }

        String path = "players." + uuid + ".homes." + slot;
        String worldName = dataConfig.getString(path + ".world");
        if (worldName == null || worldName.trim().isEmpty()) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        double x = dataConfig.getDouble(path + ".x");
        double y = dataConfig.getDouble(path + ".y");
        double z = dataConfig.getDouble(path + ".z");
        float yaw = (float) dataConfig.getDouble(path + ".yaw");
        float pitch = (float) dataConfig.getDouble(path + ".pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    public synchronized boolean hasHome(UUID uuid, int slot) {
        ensurePlayer(uuid);
        if (slot < 1 || slot > 3) {
            return false;
        }
        return dataConfig.contains("players." + uuid + ".homes." + slot + ".world");
    }

    public synchronized void removeHome(UUID uuid, int slot) {
        ensurePlayer(uuid);
        if (slot < 1 || slot > 3) {
            return;
        }
        dataConfig.set("players." + uuid + ".homes." + slot, null);
    }

    public synchronized int findFirstFreeHomeSlot(UUID uuid, int maxSlots) {
        ensurePlayer(uuid);
        int capped = Math.max(1, Math.min(3, maxSlots));
        for (int slot = 1; slot <= capped; slot++) {
            if (!hasHome(uuid, slot)) {
                return slot;
            }
        }
        return -1;
    }

    public synchronized List<Integer> getSetHomeSlots(UUID uuid, int maxSlots) {
        ensurePlayer(uuid);
        int capped = Math.max(1, Math.min(3, maxSlots));
        List<Integer> out = new ArrayList<>();
        for (int slot = 1; slot <= capped; slot++) {
            if (hasHome(uuid, slot)) {
                out.add(slot);
            }
        }
        return out;
    }

    public synchronized boolean isTpaNotificationsEnabled(UUID uuid) {
        ensurePlayer(uuid);
        return dataConfig.getBoolean("players." + uuid + ".settings.tpa-notifications", true);
    }

    public synchronized void setTpaNotificationsEnabled(UUID uuid, boolean enabled) {
        ensurePlayer(uuid);
        dataConfig.set("players." + uuid + ".settings.tpa-notifications", enabled);
    }

    public synchronized boolean isNightVisionEnabled(UUID uuid) {
        ensurePlayer(uuid);
        return dataConfig.getBoolean("players." + uuid + ".settings.night-vision", false);
    }

    public synchronized void setNightVisionEnabled(UUID uuid, boolean enabled) {
        ensurePlayer(uuid);
        dataConfig.set("players." + uuid + ".settings.night-vision", enabled);
    }

    public synchronized void save() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save data.yml: " + ex.getMessage());
        }
    }

    public synchronized boolean isSidebarSecondaryVisible(UUID uuid) {
        ensurePlayer(uuid);
        return dataConfig.getBoolean("players." + uuid + ".settings.sidebar-secondary", false);
    }

    public synchronized void setSidebarSecondaryVisible(UUID uuid, boolean visible) {
        ensurePlayer(uuid);
        dataConfig.set("players." + uuid + ".settings.sidebar-secondary", visible);
    }

    private String getDefaultRank() {
        String configured = plugin.getConfig().getString("default-rank", "member").toLowerCase(Locale.ROOT);
        if (plugin.getConfig().isConfigurationSection("ranks." + configured)) {
            return configured;
        }

        ConfigurationSection ranks = plugin.getConfig().getConfigurationSection("ranks");
        if (ranks != null && !ranks.getKeys(false).isEmpty()) {
            return ranks.getKeys(false).iterator().next().toLowerCase(Locale.ROOT);
        }

        return "member";
    }
}
