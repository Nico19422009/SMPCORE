package com.smpcore.config;

import com.smpcore.model.CurrencyDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SmpSettings {
    private final String serverName;
    private final boolean sidebarEnabled;
    private final String sidebarTitle;
    private final long sidebarUpdateIntervalTicks;
    private final boolean economyEnabled;
    private final boolean tradingEnabled;
    private final boolean auctionHouseEnabled;
    private final CurrencyDefinition primaryCurrency;
    private final List<CurrencyDefinition> secondaryCurrencies;
    private final double startingBalance;
    private final double auctionListingFee;
    private final double auctionTaxPercent;

    public SmpSettings(
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
        this.serverName = serverName;
        this.sidebarEnabled = sidebarEnabled;
        this.sidebarTitle = sidebarTitle;
        this.sidebarUpdateIntervalTicks = sidebarUpdateIntervalTicks;
        this.economyEnabled = economyEnabled;
        this.tradingEnabled = tradingEnabled;
        this.auctionHouseEnabled = auctionHouseEnabled;
        this.primaryCurrency = primaryCurrency;
        this.secondaryCurrencies = Collections.unmodifiableList(new ArrayList<CurrencyDefinition>(secondaryCurrencies));
        this.startingBalance = startingBalance;
        this.auctionListingFee = auctionListingFee;
        this.auctionTaxPercent = auctionTaxPercent;
    }

    public String serverName() { return serverName; }
    public boolean sidebarEnabled() { return sidebarEnabled; }
    public String sidebarTitle() { return sidebarTitle; }
    public long sidebarUpdateIntervalTicks() { return sidebarUpdateIntervalTicks; }
    public boolean economyEnabled() { return economyEnabled; }
    public boolean tradingEnabled() { return tradingEnabled; }
    public boolean auctionHouseEnabled() { return auctionHouseEnabled; }
    public CurrencyDefinition primaryCurrency() { return primaryCurrency; }
    public List<CurrencyDefinition> secondaryCurrencies() { return secondaryCurrencies; }
    public double startingBalance() { return startingBalance; }
    public double auctionListingFee() { return auctionListingFee; }
    public double auctionTaxPercent() { return auctionTaxPercent; }

    public List<String> sidebarLines() {
        List<String> lines = new ArrayList<String>();
        lines.add("&7Server: &f" + serverName);
        lines.add("&7Economy: " + boolColor(economyEnabled));
        lines.add("&7Trading: " + boolColor(tradingEnabled));
        lines.add("&7Auction: " + boolColor(auctionHouseEnabled));
        lines.add("&7Coin: &f" + primaryCurrency.plural());
        lines.add("&7Extra Currencies: &f" + secondaryCurrencies.size());
        return lines;
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
        if (id == null || id.trim().isEmpty()) {
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
