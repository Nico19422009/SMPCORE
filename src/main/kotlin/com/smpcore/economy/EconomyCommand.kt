package com.smpcore.economy

import com.smpcore.util.Text
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class EconomyCommand(private val economy: EconomyManager) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        return when (label.lowercase()) {
            "balance" -> handleBalance(sender, args)
            "pay" -> handlePay(sender, args)
            "eco" -> handleEco(sender, args)
            else -> false
        }
    }

    private fun handleBalance(sender: CommandSender, args: Array<out String>): Boolean {
        if (!economy.enabled()) {
            sender.sendMessage(Text.c("&cEconomy is disabled.")); return true
        }
        val target: Player
        var currencyId: String? = null
        if (args.isEmpty()) {
            if (sender !is Player) {
                sender.sendMessage(Text.c("&cConsole must specify player.")); return true
            }
            target = sender
        } else {
            target = economy.findOnlinePlayer(args[0]) ?: run {
                sender.sendMessage(Text.c("&cPlayer not found.")); return true
            }
            if (args.size > 1) currencyId = args[1]
        }

        val currency = economy.findCurrency(currencyId) ?: run {
            sender.sendMessage(Text.c("&cUnknown currency.")); return true
        }
        val balance = economy.getBalance(target.uniqueId, currency.id)
        sender.sendMessage(Text.c("&a${target.name} balance: &f${economy.format(balance, currency)}"))
        return true
    }

    private fun handlePay(sender: CommandSender, args: Array<out String>): Boolean {
        if (!economy.enabled()) {
            sender.sendMessage(Text.c("&cEconomy is disabled.")); return true
        }
        if (sender !is Player) {
            sender.sendMessage(Text.c("&cOnly players can pay.")); return true
        }
        if (args.size < 2) {
            sender.sendMessage(Text.c("&eUsage: /pay <player> <amount> [currency]")); return true
        }

        val target = economy.findOnlinePlayer(args[0])
        if (target == null || target.uniqueId == sender.uniqueId) {
            sender.sendMessage(Text.c("&cInvalid target player.")); return true
        }

        val amount = args[1].toDoubleOrNull() ?: run {
            sender.sendMessage(Text.c("&cAmount must be numeric.")); return true
        }
        if (amount <= 0) {
            sender.sendMessage(Text.c("&cAmount must be positive.")); return true
        }

        val currency = economy.findCurrency(if (args.size >= 3) args[2] else null) ?: run {
            sender.sendMessage(Text.c("&cUnknown currency.")); return true
        }

        if (!economy.withdraw(sender.uniqueId, currency.id, amount)) {
            sender.sendMessage(Text.c("&cInsufficient funds.")); return true
        }
        economy.deposit(target.uniqueId, currency.id, amount)
        sender.sendMessage(Text.c("&aPaid ${economy.format(amount, currency)} to ${target.name}"))
        target.sendMessage(Text.c("&aReceived ${economy.format(amount, currency)} from ${sender.name}"))
        return true
    }

    private fun handleEco(sender: CommandSender, args: Array<out String>): Boolean {
        if (!sender.hasPermission("smpcore.admin")) {
            sender.sendMessage(Text.c("&cNo permission.")); return true
        }
        if (args.size < 3) {
            sender.sendMessage(Text.c("&eUsage: /eco <set|add|remove> <player> <amount> [currency]")); return true
        }

        val mode = args[0].lowercase()
        val target = economy.findOnlinePlayer(args[1]) ?: run {
            sender.sendMessage(Text.c("&cPlayer not found.")); return true
        }
        val amount = args[2].toDoubleOrNull() ?: run {
            sender.sendMessage(Text.c("&cAmount must be numeric.")); return true
        }
        val currency = economy.findCurrency(if (args.size >= 4) args[3] else null) ?: run {
            sender.sendMessage(Text.c("&cUnknown currency.")); return true
        }

        val current = economy.getBalance(target.uniqueId, currency.id)
        when (mode) {
            "set" -> economy.setBalance(target.uniqueId, currency.id, amount)
            "add" -> economy.setBalance(target.uniqueId, currency.id, current + amount)
            "remove" -> economy.setBalance(target.uniqueId, currency.id, maxOf(0.0, current - amount))
            else -> {
                sender.sendMessage(Text.c("&cUnknown action.")); return true
            }
        }

        sender.sendMessage(Text.c("&aUpdated ${target.name} -> ${economy.format(economy.getBalance(target.uniqueId, currency.id), currency)}"))
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        return if (alias.equals("eco", true) && args.size == 1) mutableListOf("set", "add", "remove") else mutableListOf()
    }
}
