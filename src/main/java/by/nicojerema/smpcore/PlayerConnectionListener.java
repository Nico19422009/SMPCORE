package by.nicojerema.smpcore;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {
    private final SMPCorePlugin plugin;

    public PlayerConnectionListener(SMPCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean firstJoin = plugin.getPlayerDataStore().ensurePlayer(player.getUniqueId());
        plugin.getPlayerDataStore().save();

        if (firstJoin) {
            String joinMessage = plugin.getConfig().getString("first-join-message", "");
            if (joinMessage != null && !joinMessage.trim().isEmpty()) {
                player.sendMessage(TextUtil.colorize(plugin.getSidebarService().replacePlaceholders(player, joinMessage)));
            }
        }

        Bukkit.getScheduler().runTask(plugin, plugin.getSidebarService()::refreshAll);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPlayerDataStore().save();
        Bukkit.getScheduler().runTask(plugin, plugin.getSidebarService()::refreshAll);
    }
}
