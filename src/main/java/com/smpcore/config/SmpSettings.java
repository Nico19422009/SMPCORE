package com.smpcore.config;

import java.util.Collections;
import java.util.List;

public record SmpSettings(
        String serverName,
        boolean sidebarEnabled,
        String sidebarTitle,
        long sidebarUpdateIntervalTicks,
        boolean economyEnabled,
        boolean tradingEnabled,
        boolean auctionHouseEnabled,
        String primaryCurrencyPlural,
        int secondaryCurrencyCount
) {
    public List<String> sidebarLines() {
        return List.of(
                "&7Server: &f" + serverName,
                "&7Economy: " + boolColor(economyEnabled),
                "&7Trading: " + boolColor(tradingEnabled),
                "&7Auction: " + boolColor(auctionHouseEnabled),
                "&7Coin: &f" + primaryCurrencyPlural,
                "&7Extra Currencies: &f" + secondaryCurrencyCount
        );
    }

    private String boolColor(boolean enabled) {
        return enabled ? "&aEnabled" : "&cDisabled";
    }

    public List<String> statusText() {
        return Collections.unmodifiableList(List.of(
                "&eSMP Profile: &f" + serverName,
                "&eSidebar: " + boolColor(sidebarEnabled),
                "&eEconomy: " + boolColor(economyEnabled),
                "&eTrading: " + boolColor(tradingEnabled),
                "&eAuction House: " + boolColor(auctionHouseEnabled),
                "&ePrimary Coin: &f" + primaryCurrencyPlural,
                "&eExtra Currencies: &f" + secondaryCurrencyCount
        ));
    }
}
