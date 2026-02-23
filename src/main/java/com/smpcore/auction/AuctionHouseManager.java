package com.smpcore.auction;

import com.smpcore.config.ConfigManager;
import com.smpcore.economy.EconomyManager;
import com.smpcore.model.CurrencyDefinition;
import com.smpcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AuctionHouseManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final EconomyManager economy;
    private File file;
    private FileConfiguration data;

    public AuctionHouseManager(JavaPlugin plugin, ConfigManager config, EconomyManager economy) {
        this.plugin = plugin;
        this.config = config;
        this.economy = economy;
    }

    public void load() {
        this.file = new File(plugin.getDataFolder(), "auctions.yml");
        if (!file.exists()) {
            plugin.saveResource("auctions.yml", false);
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed saving auctions.yml: " + e.getMessage());
        }
    }

    public boolean enabled() {
        return config.settings().auctionHouseEnabled();
    }

    public boolean createListing(Player seller, double price, String currencyId) {
        if (!economy.enabled()) {
            seller.sendMessage(Text.c("&cEconomy disabled; auction needs economy."));
            return false;
        }

        CurrencyDefinition currency = config.settings().findCurrency(currencyId);
        if (currency == null) {
            seller.sendMessage(Text.c("&cUnknown currency."));
            return false;
        }

        double fee = config.settings().auctionListingFee();
        if (!economy.withdraw(seller.getUniqueId(), currency.id(), fee)) {
            seller.sendMessage(Text.c("&cNot enough funds for listing fee: " + economy.format(fee, currency)));
            return false;
        }

        ItemStack hand = seller.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            seller.sendMessage(Text.c("&cHold an item in main hand to list."));
            return false;
        }

        String id = UUID.randomUUID().toString().substring(0, 8);
        String path = "listings." + id;
        data.set(path + ".seller", seller.getUniqueId().toString());
        data.set(path + ".item", hand.clone());
        data.set(path + ".price", price);
        data.set(path + ".currency", currency.id());
        data.set(path + ".createdAt", System.currentTimeMillis());
        save();

        seller.getInventory().setItemInMainHand(null);
        seller.sendMessage(Text.c("&aListed item as #" + id + " for " + economy.format(price, currency)));
        return true;
    }

    public List<AuctionListing> listings() {
        List<AuctionListing> out = new ArrayList<>();
        Set<String> keys = data.getConfigurationSection("listings") == null
                ? Set.of()
                : data.getConfigurationSection("listings").getKeys(false);
        for (String id : keys) {
            String path = "listings." + id;
            String sellerRaw = data.getString(path + ".seller");
            if (sellerRaw == null) {
                continue;
            }

            ItemStack item = data.getItemStack(path + ".item");
            if (item == null || item.getType().isAir()) {
                continue;
            }

            UUID sellerId;
            try {
                sellerId = UUID.fromString(sellerRaw);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            out.add(new AuctionListing(
                    id,
                    sellerId,
                    item,
                    data.getDouble(path + ".price"),
                    data.getString(path + ".currency", config.settings().primaryCurrency().id()),
                    data.getLong(path + ".createdAt")
            ));
        }
        return out;
    }

    public boolean buy(Player buyer, String id) {
        String path = "listings." + id;
        if (!data.contains(path)) {
            buyer.sendMessage(Text.c("&cListing not found."));
            return false;
        }

        String sellerRaw = data.getString(path + ".seller");
        if (sellerRaw == null) {
            buyer.sendMessage(Text.c("&cListing is invalid."));
            return false;
        }

        UUID sellerId;
        try {
            sellerId = UUID.fromString(sellerRaw);
        } catch (IllegalArgumentException ignored) {
            buyer.sendMessage(Text.c("&cListing is invalid."));
            return false;
        }

        if (sellerId.equals(buyer.getUniqueId())) {
            buyer.sendMessage(Text.c("&cYou cannot buy your own listing."));
            return false;
        }

        double price = data.getDouble(path + ".price");
        String currencyId = data.getString(path + ".currency", config.settings().primaryCurrency().id());
        CurrencyDefinition currency = config.settings().findCurrency(currencyId);
        if (currency == null) {
            buyer.sendMessage(Text.c("&cListing has invalid currency."));
            return false;
        }

        if (!economy.withdraw(buyer.getUniqueId(), currency.id(), price)) {
            buyer.sendMessage(Text.c("&cInsufficient funds."));
            return false;
        }

        double tax = price * (config.settings().auctionTaxPercent() / 100.0);
        double payout = price - tax;
        economy.deposit(sellerId, currency.id(), payout);

        ItemStack item = data.getItemStack(path + ".item");
        if (item == null || item.getType().isAir()) {
            buyer.sendMessage(Text.c("&cListing item is missing."));
            return false;
        }

        buyer.getInventory().addItem(item);
        data.set(path, null);
        save();

        Player seller = Bukkit.getPlayer(sellerId);
        if (seller != null) {
            seller.sendMessage(Text.c("&aYour listing #" + id + " sold for " + economy.format(price, currency) + " (tax: " + economy.format(tax, currency) + ")"));
        }
        buyer.sendMessage(Text.c("&aBought listing #" + id + " for " + economy.format(price, currency)));
        return true;
    }
}
