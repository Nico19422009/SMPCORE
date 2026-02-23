package com.smpcore.sidebar

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class SidebarListener(private val sidebarManager: SidebarManager) : Listener {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        sidebarManager.onJoin(event.player)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        sidebarManager.onQuit(event.player)
    }
}
