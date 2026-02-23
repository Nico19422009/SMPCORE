package com.smpcore.auction

import com.smpcore.config.ConfigManager
import com.smpcore.economy.EconomyManager
import com.smpcore.util.Text
import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException
import java.util.UUID

class AuctionHouseManager(
    private val plugin: JavaPlugin,
    private val config: ConfigManager,
    private val economy: EconomyManager
) {
    private lateinit var file: File
    private lateinit var data: FileConfiguration

    fun load() {
        file = File(plugin.dataFolder, "auctions.yml")
        if (!file.exists()) plugin.saveResource("auctions.yml", false)
        data = YamlConfiguration.loadConfiguration(file)
    }

    fun save() {
        try {
            data.save(file)
        } catch (e: IOException) {
            plugin.logger.severe("Failed saving auctions.yml: ${e.message}")
        }
    }

    fun enabled(): Boolean = config.settings().auctionHouseEnabled

    fun createListing(seller: Player, price: Double, currencyId: String?): Boolean {
        if (!economy.enabled()) {
            seller.sendMessage(Text.c("&cEconomy disabled; auction needs economy.")); return false
        }

        val currency = config.findCurrency(currencyId) ?: run {
            seller.sendMessage(Text.c("&cUnknown currency.")); return false
        }

        val fee = config.settings().auctionListingFee
        if (!economy.withdraw(seller.uniqueId, currency.id, fee)) {
            seller.sendMessage(Text.c("&cNot enough funds for listing fee: ${economy.format(fee, currency)}")); return false
        }

        val hand = seller.inventory.itemInMainHand
        if (hand.type.isAir) {
            seller.sendMessage(Text.c("&cHold an item in main hand to list.")); return false
        }

        val id = UUID.randomUUID().toString().substring(0, 8)
        val path = "listings.$id"
        data.set("$path.seller", seller.uniqueId.toString())
        data.set("$path.item", hand.clone())
        data.set("$path.price", price)
        data.set("$path.currency", currency.id)
        data.set("$path.createdAt", System.currentTimeMillis())
        save()

        seller.inventory.setItemInMainHand(null)
        seller.sendMessage(Text.c("&aListed item as #$id for ${economy.format(price, currency)}"))
        return true
    }

    fun listings(): List<AuctionListing> {
        val section = data.getConfigurationSection("listings") ?: return emptyList()
        val out = mutableListOf<AuctionListing>()
        for (id in section.getKeys(false)) {
            val path = "listings.$id"
            val sellerRaw = data.getString("$path.seller") ?: continue
            val item = data.getItemStack("$path.item") ?: continue
            if (item.type.isAir) continue
            val sellerId = try { UUID.fromString(sellerRaw) } catch (_: IllegalArgumentException) { continue }

            out += AuctionListing(
                id,
                sellerId,
                item,
                data.getDouble("$path.price"),
                data.getString("$path.currency", config.primaryCurrency().id)!!,
                data.getLong("$path.createdAt")
            )
        }
        return out
    }

    fun buy(buyer: Player, id: String): Boolean {
        val path = "listings.$id"
        if (!data.contains(path)) {
            buyer.sendMessage(Text.c("&cListing not found.")); return false
        }

        val sellerRaw = data.getString("$path.seller") ?: run {
            buyer.sendMessage(Text.c("&cListing is invalid.")); return false
        }
        val sellerId = try { UUID.fromString(sellerRaw) } catch (_: IllegalArgumentException) {
            buyer.sendMessage(Text.c("&cListing is invalid.")); return false
        }
        if (sellerId == buyer.uniqueId) {
            buyer.sendMessage(Text.c("&cYou cannot buy your own listing.")); return false
        }

        val price = data.getDouble("$path.price")
        val currencyId = data.getString("$path.currency", config.primaryCurrency().id)
        val currency = config.findCurrency(currencyId) ?: run {
            buyer.sendMessage(Text.c("&cListing has invalid currency.")); return false
        }

        if (!economy.withdraw(buyer.uniqueId, currency.id, price)) {
            buyer.sendMessage(Text.c("&cInsufficient funds.")); return false
        }

        val tax = price * (config.settings().auctionTaxPercent / 100.0)
        val payout = price - tax
        economy.deposit(sellerId, currency.id, payout)

        val item: ItemStack = data.getItemStack("$path.item") ?: run {
            buyer.sendMessage(Text.c("&cListing item is missing.")); return false
        }
        if (item.type.isAir) {
            buyer.sendMessage(Text.c("&cListing item is missing.")); return false
        }

        buyer.inventory.addItem(item)
        data.set(path, null)
        save()

        Bukkit.getPlayer(sellerId)?.sendMessage(
            Text.c("&aYour listing #$id sold for ${economy.format(price, currency)} (tax: ${economy.format(tax, currency)})")
        )
        buyer.sendMessage(Text.c("&aBought listing #$id for ${economy.format(price, currency)}"))
        return true
    }
}
