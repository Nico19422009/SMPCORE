package com.smpcore.trade

import com.smpcore.config.ConfigManager
import com.smpcore.economy.EconomyManager
import com.smpcore.util.Text
import org.bukkit.entity.Player
import java.util.UUID

class TradeManager(private val config: ConfigManager, private val economy: EconomyManager) {
    private val pendingRequests = mutableMapOf<UUID, UUID>()

    fun enabled(): Boolean = config.settings().tradingEnabled

    fun request(sender: Player, target: Player) {
        pendingRequests[target.uniqueId] = sender.uniqueId
        sender.sendMessage(Text.c("&aTrade request sent to ${target.name}"))
        target.sendMessage(Text.c("&e${sender.name} requested a trade. Use /trade accept"))
    }

    fun accept(target: Player): Boolean {
        val requesterId = pendingRequests.remove(target.uniqueId) ?: run {
            target.sendMessage(Text.c("&cNo pending trade requests.")); return false
        }
        val requester = target.server.getPlayer(requesterId) ?: run {
            target.sendMessage(Text.c("&cRequester is offline.")); return false
        }

        val targetItem = target.inventory.itemInMainHand
        val requesterItem = requester.inventory.itemInMainHand
        if (targetItem.type.isAir || requesterItem.type.isAir) {
            target.sendMessage(Text.c("&cBoth players must hold an item in main hand."))
            requester.sendMessage(Text.c("&cBoth players must hold an item in main hand."))
            return false
        }

        target.inventory.setItemInMainHand(requesterItem.clone())
        requester.inventory.setItemInMainHand(targetItem.clone())
        target.sendMessage(Text.c("&aTrade completed with ${requester.name}"))
        requester.sendMessage(Text.c("&aTrade completed with ${target.name}"))
        return true
    }

    fun payInTrade(from: Player, to: Player, amount: Double, currencyId: String?): Boolean {
        val currency = config.findCurrency(currencyId)
        if (currency == null || !economy.enabled()) return false
        if (!economy.withdraw(from.uniqueId, currency.id, amount)) return false
        economy.deposit(to.uniqueId, currency.id, amount)
        from.sendMessage(Text.c("&aTrade payment sent: ${economy.format(amount, currency)}"))
        to.sendMessage(Text.c("&aTrade payment received: ${economy.format(amount, currency)}"))
        return true
    }
}
