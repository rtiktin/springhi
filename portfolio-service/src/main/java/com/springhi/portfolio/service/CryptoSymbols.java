package com.springhi.portfolio.service;

import java.util.Set;

public final class CryptoSymbols {

    private CryptoSymbols() {}

    private static final Set<String> BASE_SYMBOLS = Set.of(
            "BTC", "ETH", "SOL", "XRP", "ADA", "DOGE", "DOT", "MATIC",
            "AVAX", "LINK", "LTC", "BCH", "UNI", "ATOM", "XLM", "TRX",
            "ETC", "FIL", "APT", "NEAR", "ARB", "OP", "INJ", "SUI",
            "SEI", "TIA", "RNDR", "IMX", "AAVE", "MKR", "GRT", "ALGO",
            "SAND", "MANA", "AXS", "FTM", "HBAR", "VET", "THETA", "EGLD",
            "XMR", "ZEC", "DASH", "XTZ", "SUSHI", "CRV", "COMP", "PEPE",
            "SHIB", "BNB", "TON", "FLOKI", "BONK", "WIF", "JUP", "PYTH"
    );

    public static boolean isCrypto(String symbol) {
        if (symbol == null) return false;
        String s = symbol.trim().toUpperCase();
        if (s.isEmpty()) return false;
        if (s.endsWith("-USD") || s.endsWith("/USD")) return true;
        return BASE_SYMBOLS.contains(baseSymbol(s));
    }

    public static String baseSymbol(String symbol) {
        if (symbol == null) return "";
        String s = symbol.trim().toUpperCase();
        if (s.isEmpty()) return s;
        int dash = s.indexOf('-');
        if (dash > 0) return s.substring(0, dash);
        int slash = s.indexOf('/');
        if (slash > 0) return s.substring(0, slash);
        if (s.length() > 3 && s.endsWith("USD")) {
            String prefix = s.substring(0, s.length() - 3);
            if (BASE_SYMBOLS.contains(prefix)) return prefix;
        }
        return s;
    }

    public static String yahooSymbol(String symbol) {
        return baseSymbol(symbol) + "-USD";
    }

    public static String alpacaSymbol(String symbol) {
        return baseSymbol(symbol) + "/USD";
    }
}
