package com.smpcore.model;

public final class CurrencyDefinition {
    private final String id;
    private final String singular;
    private final String plural;
    private final String symbol;

    public CurrencyDefinition(String id, String singular, String plural, String symbol) {
        this.id = id;
        this.singular = singular;
        this.plural = plural;
        this.symbol = symbol;
    }

    public String id() { return id; }
    public String singular() { return singular; }
    public String plural() { return plural; }
    public String symbol() { return symbol; }

    public String getId() { return id; }
    public String getSingular() { return singular; }
    public String getPlural() { return plural; }
    public String getSymbol() { return symbol; }
}
