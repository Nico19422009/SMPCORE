package com.smpcore.util

import org.bukkit.ChatColor

object Text {
    fun c(text: String): String = ChatColor.translateAlternateColorCodes('&', text)
}
