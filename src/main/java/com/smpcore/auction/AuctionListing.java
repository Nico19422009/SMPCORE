package com.smpcore.auction;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class AuctionListing {
    private final String id;
    private final UUID seller;
    private final ItemStack item;
    private final double price;
    private final String currencyId;
    private final long createdAt;

    public AuctionListing(String id, UUID seller, ItemStack item, double price, String currencyId, long createdAt) {
        this.id = id;
        this.seller = seller;
        this.item = item;
        this.price = price;
        this.currencyId = currencyId;
        this.createdAt = createdAt;
    }

    public String id() { return id; }
    public UUID seller() { return seller; }
    public ItemStack item() { return item; }
    public double price() { return price; }
    public String currencyId() { return currencyId; }
    public long createdAt() { return createdAt; }

    public String getId() { return id; }
    public UUID getSeller() { return seller; }
    public ItemStack getItem() { return item; }
    public double getPrice() { return price; }
    public String getCurrencyId() { return currencyId; }
    public long getCreatedAt() { return createdAt; }
}
