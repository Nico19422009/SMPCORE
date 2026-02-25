package by.nicojerema.smpcore;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PayCommand implements CommandExecutor, TabCompleter {
    private final SMPCorePlugin plugin;

    public PayCommand(SMPCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextUtil.colorize("&cOnly players can use this command."));
            return true;
        }

        Player payer = (Player) sender;
        if (!payer.hasPermission("smpcore.pay")) {
            payer.sendMessage(TextUtil.colorize("&cYou do not have permission."));
            return true;
        }
        if (!plugin.isEconomyEnabled()) {
            payer.sendMessage(plugin.getEconomyDisabledMessage());
            return true;
        }

        if (args.length < 2) {
            payer.sendMessage(TextUtil.colorize("&cUsage: /pay <player> <amount>"));
            payer.sendMessage(TextUtil.colorize("&7Example: &f/pay Steve 10M &7or &f/pay Alex 10K"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            payer.sendMessage(TextUtil.colorize("&cPlayer not found online."));
            return true;
        }
        if (target.getUniqueId().equals(payer.getUniqueId())) {
            payer.sendMessage(TextUtil.colorize("&cYou cannot pay yourself."));
            return true;
        }

        Long amount = parseAmount(args[1]);
        if (amount == null || amount <= 0L) {
            payer.sendMessage(TextUtil.colorize("&cInvalid amount. Use numbers like &f1000&c, &f10K&c, &f10M&c, &f1B&c."));
            return true;
        }

        long payerCoins = plugin.getPlayerDataStore().getCoins(payer.getUniqueId());
        if (payerCoins < amount) {
            payer.sendMessage(TextUtil.colorize(
                    "&cNot enough " + getCoinsName() + ". You have &f" + formatAmount(payerCoins)
                            + "&c, need &f" + formatAmount(amount) + "&c."
            ));
            return true;
        }

        long targetCoins = plugin.getPlayerDataStore().getCoins(target.getUniqueId());
        long updatedTarget;
        try {
            updatedTarget = Math.addExact(targetCoins, amount);
        } catch (ArithmeticException ex) {
            payer.sendMessage(TextUtil.colorize("&cThis payment is too large."));
            return true;
        }

        plugin.getPlayerDataStore().setCoins(payer.getUniqueId(), payerCoins - amount);
        plugin.getPlayerDataStore().setCoins(target.getUniqueId(), updatedTarget);
        plugin.getPlayerDataStore().save();
        plugin.getSidebarService().refreshAll();

        String formatted = formatAmount(amount);
        payer.sendMessage(TextUtil.colorize("&aYou paid &f" + target.getName() + " &e" + formatted + " " + getCoinsName() + "&a."));
        target.sendMessage(TextUtil.colorize("&aYou received &e" + formatted + " " + getCoinsName() + " &afrom &f" + payer.getName() + "&a."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smpcore.pay")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                String name = player.getName();
                if (name.equalsIgnoreCase(sender.getName())) {
                    continue;
                }
                if (name.toLowerCase(Locale.ROOT).startsWith(input)) {
                    out.add(name);
                }
            }
            return out;
        }

        if (args.length == 2) {
            String input = args[1].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            suggestions.add("1K");
            suggestions.add("10K");
            suggestions.add("100K");
            suggestions.add("1M");
            suggestions.add("10M");
            suggestions.add("100M");
            suggestions.add("1B");

            List<String> out = new ArrayList<>();
            for (String suggestion : suggestions) {
                if (suggestion.toLowerCase(Locale.ROOT).startsWith(input)) {
                    out.add(suggestion);
                }
            }
            return out;
        }

        return Collections.emptyList();
    }

    private Long parseAmount(String rawInput) {
        if (rawInput == null) {
            return null;
        }

        String input = rawInput.trim().replace(",", "").replace("_", "");
        if (input.isEmpty()) {
            return null;
        }

        long multiplier = 1L;
        String numberPart = input;

        char last = input.charAt(input.length() - 1);
        if (Character.isLetter(last)) {
            switch (Character.toLowerCase(last)) {
                case 'k':
                    multiplier = 1_000L;
                    break;
                case 'm':
                    multiplier = 1_000_000L;
                    break;
                case 'b':
                    multiplier = 1_000_000_000L;
                    break;
                case 't':
                    multiplier = 1_000_000_000_000L;
                    break;
                default:
                    return null;
            }
            numberPart = input.substring(0, input.length() - 1);
        }

        if (numberPart.trim().isEmpty()) {
            return null;
        }

        BigDecimal base;
        try {
            base = new BigDecimal(numberPart);
        } catch (NumberFormatException ex) {
            return null;
        }

        if (base.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal amount = base.multiply(BigDecimal.valueOf(multiplier));
        BigDecimal rounded = amount.setScale(0, RoundingMode.HALF_UP);

        if (rounded.compareTo(BigDecimal.ONE) < 0) {
            return null;
        }

        if (rounded.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            return null;
        }

        try {
            return rounded.longValueExact();
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private String formatAmount(long amount) {
        return TextUtil.formatCoins(amount);
    }

    private String getCoinsName() {
        return plugin.getConfig().getString("coins-display-name", "Coins");
    }
}
