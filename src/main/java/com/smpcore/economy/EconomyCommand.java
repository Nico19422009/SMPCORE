package com.smpcore.economy;

import com.smpcore.model.CurrencyDefinition;
import com.smpcore.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class EconomyCommand implements CommandExecutor, TabCompleter {
    private final EconomyManager economy;

    public EconomyCommand(EconomyManager economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("balance")) {
            return handleBalance(sender, args);
        }
        if (label.equalsIgnoreCase("pay")) {
            return handlePay(sender, args);
        }
        if (label.equalsIgnoreCase("eco")) {
            return handleEco(sender, args);
        }
        return false;
    }

    private boolean handleBalance(CommandSender sender, String[] args) {
        if (!economy.enabled()) {
            sender.sendMessage(Text.c("&cEconomy is disabled."));
            return true;
        }
        Player target;
        String currencyId = null;
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Text.c("&cConsole must specify player."));
                return true;
            }
            target = (Player) sender;
        } else {
            target = economy.findOnlinePlayer(args[0]);
            if (target == null) {
                sender.sendMessage(Text.c("&cPlayer not found."));
                return true;
            }
            if (args.length > 1) {
                currencyId = args[1];
            }
        }

        CurrencyDefinition currency = economy.findCurrency(currencyId);
        if (currency == null) {
            sender.sendMessage(Text.c("&cUnknown currency."));
            return true;
        }
        double balance = economy.getBalance(target.getUniqueId(), currency.id());
        sender.sendMessage(Text.c("&a" + target.getName() + " balance: &f" + economy.format(balance, currency)));
        return true;
    }

    private boolean handlePay(CommandSender sender, String[] args) {
        if (!economy.enabled()) {
            sender.sendMessage(Text.c("&cEconomy is disabled."));
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(Text.c("&cOnly players can pay."));
            return true;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            sender.sendMessage(Text.c("&eUsage: /pay <player> <amount> [currency]"));
            return true;
        }

        Player target = economy.findOnlinePlayer(args[0]);
        if (target == null || target.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage(Text.c("&cInvalid target player."));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Text.c("&cAmount must be numeric."));
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage(Text.c("&cAmount must be positive."));
            return true;
        }

        CurrencyDefinition currency = economy.findCurrency(args.length >= 3 ? args[2] : null);
        if (currency == null) {
            sender.sendMessage(Text.c("&cUnknown currency."));
            return true;
        }

        if (!economy.withdraw(player.getUniqueId(), currency.id(), amount)) {
            sender.sendMessage(Text.c("&cInsufficient funds."));
            return true;
        }
        economy.deposit(target.getUniqueId(), currency.id(), amount);
        sender.sendMessage(Text.c("&aPaid " + economy.format(amount, currency) + " to " + target.getName()));
        target.sendMessage(Text.c("&aReceived " + economy.format(amount, currency) + " from " + player.getName()));
        return true;
    }

    private boolean handleEco(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smpcore.admin")) {
            sender.sendMessage(Text.c("&cNo permission."));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.c("&eUsage: /eco <set|add|remove> <player> <amount> [currency]"));
            return true;
        }
        String mode = args[0].toLowerCase(Locale.ROOT);
        Player target = economy.findOnlinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Text.c("&cPlayer not found."));
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Text.c("&cAmount must be numeric."));
            return true;
        }
        CurrencyDefinition currency = economy.findCurrency(args.length >= 4 ? args[3] : null);
        if (currency == null) {
            sender.sendMessage(Text.c("&cUnknown currency."));
            return true;
        }
        double current = economy.getBalance(target.getUniqueId(), currency.id());
        if ("set".equals(mode)) {
            economy.setBalance(target.getUniqueId(), currency.id(), amount);
        } else if ("add".equals(mode)) {
            economy.setBalance(target.getUniqueId(), currency.id(), current + amount);
        } else if ("remove".equals(mode)) {
            economy.setBalance(target.getUniqueId(), currency.id(), Math.max(0, current - amount));
        } else {
            sender.sendMessage(Text.c("&cUnknown action."));
            return true;
        }

        sender.sendMessage(Text.c("&aUpdated " + target.getName() + " -> " + economy.format(economy.getBalance(target.getUniqueId(), currency.id()), currency)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (alias.equalsIgnoreCase("eco") && args.length == 1) {
            return Arrays.asList("set", "add", "remove");
        }
        return new ArrayList<String>();
    }
}
