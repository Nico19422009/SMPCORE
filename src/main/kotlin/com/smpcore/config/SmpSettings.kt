package com.smpcore.config

import com.smpcore.model.CurrencyDefinition

class SmpSettings(
    val serverName: String,
    val sidebarEnabled: Boolean,
    val sidebarTitle: String,
    val sidebarUpdateIntervalTicks: Long,
    val economyEnabled: Boolean,
    val tradingEnabled: Boolean,
    val auctionHouseEnabled: Boolean,
    val primaryCurrency: CurrencyDefinition,
    val secondaryCurrencies: List<CurrencyDefinition>,
    val startingBalance: Double,
    val auctionListingFee: Double,
    val auctionTaxPercent: Double
) {
    fun sidebarLines(): List<String> = listOf(
        "&7Server: &f$serverName",
        "&7Economy: ${boolColor(economyEnabled)}",
        "&7Trading: ${boolColor(tradingEnabled)}",
        "&7Auction: ${boolColor(auctionHouseEnabled)}",
        "&7Coin: &f${primaryCurrency.plural}",
        "&7Extra Currencies: &f${secondaryCurrencies.size}"
    )

    fun currenciesById(): Map<String, CurrencyDefinition> {
        val map = mutableMapOf<String, CurrencyDefinition>()
        map[primaryCurrency.id.lowercase()] = primaryCurrency
        secondaryCurrencies.forEach { map[it.id.lowercase()] = it }
        return map
    }

    fun findCurrency(id: String?): CurrencyDefinition? {
        if (id.isNullOrBlank()) return primaryCurrency
        return currenciesById()[id.lowercase()]
    }

    fun statusText(): List<String> = listOf(
        "&eSMP Profile: &f$serverName",
        "&eSidebar: ${boolColor(sidebarEnabled)}",
        "&eEconomy: ${boolColor(economyEnabled)}",
        "&eTrading: ${boolColor(tradingEnabled)}",
        "&eAuction House: ${boolColor(auctionHouseEnabled)}",
        "&ePrimary Coin: &f${primaryCurrency.plural} (&7${primaryCurrency.id}&f)",
        "&eExtra Currencies: &f${secondaryCurrencies.size}",
        "&eAuction Fee/Tax: &f$auctionListingFee / $auctionTaxPercent%"
    )

    private fun boolColor(enabled: Boolean): String = if (enabled) "&aEnabled" else "&cDisabled"
}
