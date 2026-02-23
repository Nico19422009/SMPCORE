package com.smpcore.trade

import com.smpcore.util.Text
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class TradeCommand(private val tradeManager: TradeManager) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!tradeManager.enabled()) {
            sender.sendMessage(Text.c("&cTrading is disabled in config.")); return true
        }
        if (sender !is Player) {
            sender.sendMessage(Text.c("&cOnly players can trade.")); return true
        }

        if (args.isEmpty()) {
            sender.sendMessage(Text.c("&eUsage: /trade <player|accept|pay>")); return true
        }

        if (args[0].equals("accept", true)) {
            tradeManager.accept(sender); return true
        }

        if (args[0].equals("pay", true)) {
            if (args.size < 3) {
                sender.sendMessage(Text.c("&eUsage: /trade pay <player> <amount> [currency]")); return true
            }
            val target = sender.server.getPlayerExact(args[1]) ?: run {
                sender.sendMessage(Text.c("&cTarget not found.")); return true
            }
            val amount = args[2].toDoubleOrNull() ?: run {
                sender.sendMessage(Text.c("&cInvalid amount.")); return true
            }
            val ok = tradeManager.payInTrade(sender, target, amount, if (args.size >= 4) args[3] else null)
            if (!ok) sender.sendMessage(Text.c("&cTrade payment failed."))
            return true
        }

        val target = sender.server.getPlayerExact(args[0])
        if (target == null || target.uniqueId == sender.uniqueId) {
            sender.sendMessage(Text.c("&cInvalid target.")); return true
        }
        tradeManager.request(sender, target)
        return true
    }
}
