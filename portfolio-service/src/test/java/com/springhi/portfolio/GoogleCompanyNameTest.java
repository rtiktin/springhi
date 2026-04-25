package com.springhi.portfolio;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GoogleCompanyNameTest {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final List<String> EXCHANGES =
            List.of("NASDAQ", "NYSE", "NYSE_ARCA", "NYSEARCA", "BATS");

    /**
     * Fetches the company name for a given stock symbol via Google Finance.
     * Tries common exchanges in order until one returns a valid HTML page.
     *
     * Google Finance title format: "Apple Inc. (AAPL) Stock Price, News... - Google Finance"
     * We extract everything before the first " (" as the company name.
     */
    static String fetchCompanyName(String symbol) throws Exception {
        for (String exchange : EXCHANGES) {
            String urlStr = "https://www.google.com/finance/quote/" +
                            symbol.toUpperCase() + ":" + exchange;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);
                conn.setInstanceFollowRedirects(true);

                int status = conn.getResponseCode();
                if (status != 200) continue;

                String body;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    body = reader.lines().collect(Collectors.joining("\n"));
                }

                String name = extractCompanyNameFromHtml(body, symbol);
                if (name != null && !name.isBlank()) {
                    System.out.printf("[%s:%s] Company name: %s%n", symbol, exchange, name);
                    return name;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Extracts the company name from the HTML page.
     * Tries two patterns:
     *   1. <title>Apple Inc. (AAPL) Stock Price... - Google Finance</title>
     *   2. <meta name="description" content="Apple Inc. (AAPL) ...">
     */
    private static String extractCompanyNameFromHtml(String html, String symbol) {
        String titleTag = extractBetween(html, "<title>", "</title>");
        if (titleTag != null) {
            int idx = titleTag.indexOf(" (" + symbol.toUpperCase() + ")");
            if (idx > 0) {
                return titleTag.substring(0, idx).trim();
            }
            if (titleTag.contains(" (") ) {
                return titleTag.substring(0, titleTag.indexOf(" (")).trim();
            }
        }

        String descContent = extractBetween(html, "name=\"description\" content=\"", "\"");
        if (descContent != null) {
            int idx = descContent.indexOf(" (");
            if (idx > 0) return descContent.substring(0, idx).trim();
        }

        return null;
    }

    private static String extractBetween(String text, String start, String end) {
        int s = text.indexOf(start);
        if (s < 0) return null;
        s += start.length();
        int e = text.indexOf(end, s);
        if (e < 0) return null;
        return text.substring(s, e);
    }

    @Test
    void testFetchCompanyNameForApple() throws Exception {
        String name = fetchCompanyName("AAPL");
        System.out.println("AAPL -> " + name);
        assertNotNull(name, "Company name should not be null for AAPL");
        assertFalse(name.isBlank(), "Company name should not be blank for AAPL");
    }

    @Test
    void testFetchCompanyNameForTesla() throws Exception {
        String name = fetchCompanyName("TSLA");
        System.out.println("TSLA -> " + name);
        assertNotNull(name, "Company name should not be null for TSLA");
        assertFalse(name.isBlank(), "Company name should not be blank for TSLA");
    }

    @Test
    void testFetchCompanyNamesForMultipleSymbols() throws Exception {
        String[] symbols = {"AAPL", "TSLA", "MSFT", "GOOGL", "AMZN"};
        for (String symbol : symbols) {
            String name = fetchCompanyName(symbol);
            System.out.printf("%s -> %s%n", symbol, name);
            assertNotNull(name, "Company name should not be null for " + symbol);
            assertFalse(name.isBlank(), "Company name should not be blank for " + symbol);
        }
    }
}
