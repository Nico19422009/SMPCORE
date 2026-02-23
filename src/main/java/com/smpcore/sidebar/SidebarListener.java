package com.smpcore.sidebar;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class SidebarListener implements Listener {
    private final SidebarManager sidebarManager;

    public SidebarListener(SidebarManager sidebarManager) {
        this.sidebarManager = sidebarManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sidebarManager.onJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sidebarManager.onQuit(event.getPlayer());
    }
}
