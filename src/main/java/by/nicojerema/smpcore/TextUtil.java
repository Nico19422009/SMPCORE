package by.nicojerema.smpcore;

import org.bukkit.ChatColor;

import java.util.Locale;

public final class TextUtil {
    private TextUtil() {
    }

    public static String colorize(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    public static String formatPlaytime(long ticks) {
        long totalSeconds = ticks / 20L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (hours > 0) {
            return String.format("%dh %02dm", hours, minutes);
        }
        return String.format("%dm %02ds", minutes, seconds);
    }

    public static String safePrefix(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    public static String formatCoins(long amount) {
        long abs = Math.abs(amount);
        if (abs < 1_000L) {
            return String.valueOf(amount);
        }

        if (abs >= 1_000_000_000L) {
            return formatCompact(amount, 1_000_000_000D, "B");
        }
        if (abs >= 1_000_000L) {
            return formatCompact(amount, 1_000_000D, "M");
        }
        return formatCompact(amount, 1_000D, "K");
    }

    private static String formatCompact(long amount, double divisor, String suffix) {
        double scaled = amount / divisor;
        double absScaled = Math.abs(scaled);
        String pattern;

        if (absScaled >= 100D) {
            pattern = "%.0f";
        } else if (absScaled >= 10D) {
            pattern = "%.1f";
        } else {
            pattern = "%.2f";
        }

        String formatted = String.format(Locale.ROOT, pattern, scaled);
        if (formatted.indexOf('.') >= 0) {
            while (formatted.endsWith("0")) {
                formatted = formatted.substring(0, formatted.length() - 1);
            }
            if (formatted.endsWith(".")) {
                formatted = formatted.substring(0, formatted.length() - 1);
            }
        }

        return formatted + suffix;
    }
}
