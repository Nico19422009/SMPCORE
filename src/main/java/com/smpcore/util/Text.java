package com.smpcore.util;

import org.bukkit.ChatColor;

public final class Text {
    private Text() {
    }

    public static String c(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
