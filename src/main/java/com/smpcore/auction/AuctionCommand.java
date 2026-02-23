package com.smpcore.auction;

import com.smpcore.config.ConfigManager;
import com.smpcore.economy.EconomyManager;
import com.smpcore.model.CurrencyDefinition;
import com.smpcore.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class AuctionCommand implements CommandExecutor {
    private final AuctionHouseManager auction;
    private final EconomyManager economy;
    private final ConfigManager config;

    public AuctionCommand(AuctionHouseManager auction, EconomyManager economy, ConfigManager config) {
        this.auction = auction;
        this.economy = economy;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!auction.enabled()) {
            sender.sendMessage(Text.c("&cAuction house is disabled."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.c("&cOnly players can use auction house."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Text.c("&eUsage: /ah <sell|browse|buy> ..."));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "sell" -> {
                if (args.length < 2) {
                    sender.sendMessage(Text.c("&eUsage: /ah sell <price> [currency]"));
                    return true;
                }
                double price;
                try {
                    price = Double.parseDouble(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Text.c("&cPrice must be numeric."));
                    return true;
                }
                auction.createListing(player, price, args.length > 2 ? args[2] : null);
                return true;
            }
            case "browse" -> {
                List<AuctionListing> listings = auction.listings().stream()
                        .sorted(Comparator.comparingLong(AuctionListing::createdAt).reversed())
                        .limit(10)
                        .collect(Collectors.toList());
                sender.sendMessage(Text.c("&6--- Auction Listings ---"));
                if (listings.isEmpty()) {
                    sender.sendMessage(Text.c("&7No active listings."));
                    return true;
                }
                for (AuctionListing listing : listings) {
                    CurrencyDefinition c = config.settings().findCurrency(listing.currencyId());
                    if (c == null) {
                        c = config.settings().primaryCurrency();
                    }
                    String itemName = listing.item().getType().name();
                    sender.sendMessage(Text.c("&e#" + listing.id() + " &f" + itemName + " &7- &a" + economy.format(listing.price(), c)));
                }
                return true;
            }
            case "buy" -> {
                if (args.length < 2) {
                    sender.sendMessage(Text.c("&eUsage: /ah buy <listingId>"));
                    return true;
                }
                auction.buy(player, args[1]);
                return true;
            }
            default -> {
                sender.sendMessage(Text.c("&cUnknown ah subcommand."));
                return true;
            }
        }
    }
}
