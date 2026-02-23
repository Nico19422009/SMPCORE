package com.smpcore.auction;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public record AuctionListing(
        String id,
        UUID seller,
        ItemStack item,
        double price,
        String currencyId,
        long createdAt
) {
}
