package com.smpcore.auction

import org.bukkit.inventory.ItemStack
import java.util.UUID

data class AuctionListing(
    val id: String,
    val seller: UUID,
    val item: ItemStack,
    val price: Double,
    val currencyId: String,
    val createdAt: Long
)
