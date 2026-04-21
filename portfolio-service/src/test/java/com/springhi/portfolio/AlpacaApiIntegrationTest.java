package com.springhi.portfolio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AlpacaApiIntegrationTest {

    private static final String ALPACA_SNAPSHOTS_URL =
            "https://data.alpaca.markets/v2/stocks/snapshots?symbols=AAPL,TSLA";

    private static String apiKeyId;
    private static String apiSecretKey;

    @BeforeAll
    static void loadCredentials() throws Exception {
        Path yamlPath = Paths.get("src/main/resources/application-local.yaml");
        assertTrue(Files.exists(yamlPath),
                "application-local.yaml not found at " + yamlPath.toAbsolutePath());

        for (String line : Files.readAllLines(yamlPath)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("api_key_id:")) {
                apiKeyId = trimmed.substring("api_key_id:".length()).trim();
            } else if (trimmed.startsWith("api_secret_key:")) {
                apiSecretKey = trimmed.substring("api_secret_key:".length()).trim();
            }
        }

        assertNotNull(apiKeyId, "alpaca.api_key_id not found in application-local.yaml");
        assertNotNull(apiSecretKey, "alpaca.api_secret_key not found in application-local.yaml");
        assertFalse(apiKeyId.isEmpty(), "alpaca.api_key_id is blank");
        assertFalse(apiSecretKey.isEmpty(), "alpaca.api_secret_key is blank");
    }

    @Test
    void testAlpacaSnapshotsReturnsValidPrices() throws Exception {
        URL url = new URL(ALPACA_SNAPSHOTS_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("APCA-API-KEY-ID", apiKeyId);
        conn.setRequestProperty("APCA-API-SECRET-KEY", apiSecretKey);
        conn.setRequestProperty("accept", "application/json");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);

        int statusCode = conn.getResponseCode();

        String body;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            body = reader.lines().collect(Collectors.joining("\n"));
        }

        System.out.println("HTTP status: " + statusCode);
        System.out.println("Response body: " + body);

        assertEquals(200, statusCode,
                "Expected HTTP 200 but got " + statusCode + ": " + body);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(body);

        for (String symbol : new String[]{"AAPL", "TSLA"}) {
            assertTrue(root.has(symbol), "Response missing symbol: " + symbol);

            JsonNode snapshot = root.get(symbol);
            assertTrue(snapshot.has("latestTrade"), symbol + " missing latestTrade");

            double price = snapshot.get("latestTrade").get("p").asDouble();
            assertTrue(price > 0, symbol + " price must be > 0, got: " + price);

            System.out.printf("%s latest trade price: %.2f%n", symbol, price);
        }
    }
}
