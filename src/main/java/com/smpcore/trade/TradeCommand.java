package com.smpcore.trade;

import com.smpcore.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TradeCommand implements CommandExecutor {
    private final TradeManager tradeManager;

    public TradeCommand(TradeManager tradeManager) {
        this.tradeManager = tradeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!tradeManager.enabled()) {
            sender.sendMessage(Text.c("&cTrading is disabled in config."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.c("&cOnly players can trade."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Text.c("&eUsage: /trade <player|accept|pay>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("accept")) {
            tradeManager.accept(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("pay")) {
            if (args.length < 3) {
                sender.sendMessage(Text.c("&eUsage: /trade pay <player> <amount> [currency]"));
                return true;
            }
            Player target = player.getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Text.c("&cTarget not found."));
                return true;
            }
            double amount;
            try {
                amount = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Text.c("&cInvalid amount."));
                return true;
            }
            boolean ok = tradeManager.payInTrade(player, target, amount, args.length >= 4 ? args[3] : null);
            if (!ok) {
                sender.sendMessage(Text.c("&cTrade payment failed."));
            }
            return true;
        }

        Player target = player.getServer().getPlayerExact(args[0]);
        if (target == null || target.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage(Text.c("&cInvalid target."));
            return true;
        }
        tradeManager.request(player, target);
        return true;
    }
}
