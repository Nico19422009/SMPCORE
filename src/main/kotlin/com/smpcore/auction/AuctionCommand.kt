package com.smpcore.auction

import com.smpcore.config.ConfigManager
import com.smpcore.economy.EconomyManager
import com.smpcore.util.Text
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class AuctionCommand(
    private val auction: AuctionHouseManager,
    private val economy: EconomyManager,
    private val config: ConfigManager
) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!auction.enabled()) {
            sender.sendMessage(Text.c("&cAuction house is disabled.")); return true
        }
        if (sender !is Player) {
            sender.sendMessage(Text.c("&cOnly players can use auction house.")); return true
        }

        if (args.isEmpty()) {
            sender.sendMessage(Text.c("&eUsage: /ah <sell|browse|buy> ...")); return true
        }

        when (args[0].lowercase()) {
            "sell" -> {
                if (args.size < 2) {
                    sender.sendMessage(Text.c("&eUsage: /ah sell <price> [currency]")); return true
                }
                val price = args[1].toDoubleOrNull() ?: run {
                    sender.sendMessage(Text.c("&cPrice must be numeric.")); return true
                }
                auction.createListing(sender, price, if (args.size > 2) args[2] else null)
                return true
            }
            "browse" -> {
                val listings = auction.listings().sortedByDescending { it.createdAt }.take(10)
                sender.sendMessage(Text.c("&6--- Auction Listings ---"))
                if (listings.isEmpty()) {
                    sender.sendMessage(Text.c("&7No active listings.")); return true
                }
                listings.forEach { listing ->
                    val currency = config.findCurrency(listing.currencyId) ?: config.primaryCurrency()
                    sender.sendMessage(Text.c("&e#${listing.id} &f${listing.item.type.name} &7- &a${economy.format(listing.price, currency)}"))
                }
                return true
            }
            "buy" -> {
                if (args.size < 2) {
                    sender.sendMessage(Text.c("&eUsage: /ah buy <listingId>")); return true
                }
                auction.buy(sender, args[1])
                return true
            }
            else -> {
                sender.sendMessage(Text.c("&cUnknown ah subcommand.")); return true
            }
        }
    }
}
