package com.smpcore.config;

import com.smpcore.model.CurrencyDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record SmpSettings(
        String serverName,
        boolean sidebarEnabled,
        String sidebarTitle,
        long sidebarUpdateIntervalTicks,
        boolean economyEnabled,
        boolean tradingEnabled,
        boolean auctionHouseEnabled,
        CurrencyDefinition primaryCurrency,
        List<CurrencyDefinition> secondaryCurrencies,
        double startingBalance,
        double auctionListingFee,
        double auctionTaxPercent
) {
    public List<String> sidebarLines() {
        return List.of(
                "&7Server: &f" + serverName,
                "&7Economy: " + boolColor(economyEnabled),
                "&7Trading: " + boolColor(tradingEnabled),
                "&7Auction: " + boolColor(auctionHouseEnabled),
                "&7Coin: &f" + primaryCurrency.plural(),
                "&7Extra Currencies: &f" + secondaryCurrencies.size()
        );
    }

    public Map<String, CurrencyDefinition> currenciesById() {
        Map<String, CurrencyDefinition> map = new HashMap<>();
        map.put(primaryCurrency.id().toLowerCase(), primaryCurrency);
        for (CurrencyDefinition secondary : secondaryCurrencies) {
            map.put(secondary.id().toLowerCase(), secondary);
        }
        return map;
    }

    public CurrencyDefinition findCurrency(String id) {
        if (id == null || id.isBlank()) {
            return primaryCurrency;
        }
        return currenciesById().get(id.toLowerCase());
    }

    public List<String> statusText() {
        List<String> lines = new ArrayList<>();
        lines.add("&eSMP Profile: &f" + serverName);
        lines.add("&eSidebar: " + boolColor(sidebarEnabled));
        lines.add("&eEconomy: " + boolColor(economyEnabled));
        lines.add("&eTrading: " + boolColor(tradingEnabled));
        lines.add("&eAuction House: " + boolColor(auctionHouseEnabled));
        lines.add("&ePrimary Coin: &f" + primaryCurrency.plural() + " (&7" + primaryCurrency.id() + "&f)");
        lines.add("&eExtra Currencies: &f" + secondaryCurrencies.size());
        lines.add("&eAuction Fee/Tax: &f" + auctionListingFee + " / " + auctionTaxPercent + "%");
        return lines;
    }

    private String boolColor(boolean enabled) {
        return enabled ? "&aEnabled" : "&cDisabled";
    }
}
