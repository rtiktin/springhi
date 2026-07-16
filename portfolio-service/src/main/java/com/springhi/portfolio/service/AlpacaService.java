package com.springhi.portfolio.service;

import com.springhi.portfolio.dto.AlpacaSnapshotResponse;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AlpacaService {

    private static final Logger log = LoggerFactory.getLogger(AlpacaService.class);

    private final WebClient webClient;
    private final String apiKeyId;
    private final String apiSecretKey;

    public AlpacaService(
            @Value("${alpaca.base-url}") String baseUrl,
            @Value("${alpaca.api_key_id}") String apiKeyId,
            @Value("${alpaca.api_secret_key}") String apiSecretKey,
            @Value("${alpaca.ssl-bypass:false}") boolean sslBypass) throws SSLException {
        this.apiKeyId = apiKeyId;
        this.apiSecretKey = apiSecretKey;

        WebClient.Builder builder = WebClient.builder().baseUrl(baseUrl);

        if (sslBypass) {
            log.warn("Alpaca SSL verification is DISABLED (ssl-bypass=true) — for local development only");
            io.netty.handler.ssl.SslContext sslContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
            HttpClient httpClient = HttpClient.create().secure(t -> t.sslContext(sslContext));
            builder.clientConnector(new ReactorClientHttpConnector(httpClient));
        }

        this.webClient = builder.build();
    }

    private static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final List<String> GOOGLE_EXCHANGES =
            List.of("NASDAQ", "NYSE", "NYSE_ARCA", "NYSEARCA", "BATS");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Optional<String> fetchCompanyName(String symbol) {
        Optional<String> fromYahoo = fetchCompanyNameFromYahoo(symbol);
        if (fromYahoo.isPresent()) return fromYahoo;
        return fetchCompanyNameFromGoogle(symbol);
    }

    private Optional<String> fetchCompanyNameFromYahoo(String symbol) {
        try {
            String urlStr = "https://query1.finance.yahoo.com/v8/finance/chart/"
                            + symbol.toUpperCase() + "?metrics=longName";
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", BROWSER_USER_AGENT);
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(8_000);
            conn.setInstanceFollowRedirects(true);

            if (conn.getResponseCode() != 200) {
                log.info("Yahoo Finance returned HTTP {} for {}", conn.getResponseCode(), symbol);
                return Optional.empty();
            }

            String body;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                body = reader.lines().collect(Collectors.joining("\n"));
            }

            JsonNode root = MAPPER.readTree(body);
            JsonNode meta = root.path("chart").path("result").path(0).path("meta");

            for (String field : new String[]{"longName", "shortName"}) {
                JsonNode node = meta.path(field);
                if (!node.isMissingNode() && !node.isNull() && !node.asText().isBlank()) {
                    String name = node.asText().trim();
                    log.info("Yahoo Finance company name for {}: {}", symbol, name);
                    return Optional.of(name);
                }
            }
        } catch (Exception e) {
            log.info("Yahoo Finance lookup failed for {}: {}", symbol, e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<String> fetchCompanyNameFromGoogle(String symbol) {
        for (String exchange : GOOGLE_EXCHANGES) {
            try {
                String urlStr = "https://www.google.com/finance/quote/" +
                                symbol.toUpperCase() + ":" + exchange;
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", BROWSER_USER_AGENT);
                conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                conn.setConnectTimeout(8_000);
                conn.setReadTimeout(8_000);
                conn.setInstanceFollowRedirects(true);

                if (conn.getResponseCode() != 200) continue;

                String body;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    body = reader.lines().collect(Collectors.joining("\n"));
                }

                String name = extractNameFromHtml(body, symbol);
                if (name != null && !name.isBlank()) {
                    log.info("Google Finance company name for {}:{}: {}", symbol, exchange, name);
                    return Optional.of(name);
                }
            } catch (Exception e) {
                log.debug("Google Finance lookup failed for {}:{} — {}", symbol, exchange, e.getMessage());
            }
        }
        log.warn("Could not resolve company name for {} from any source", symbol);
        return Optional.empty();
    }

    private String extractNameFromHtml(String html, String symbol) {
        String title = extractBetween(html, "<title>", "</title>");
        if (title != null) {
            int idx = title.indexOf(" (" + symbol.toUpperCase() + ")");
            if (idx > 0) return title.substring(0, idx).trim();
            idx = title.indexOf(" (");
            if (idx > 0) return title.substring(0, idx).trim();
        }
        String desc = extractBetween(html, "name=\"description\" content=\"", "\"");
        if (desc != null) {
            int idx = desc.indexOf(" (");
            if (idx > 0) return desc.substring(0, idx).trim();
        }
        return null;
    }

    private String extractBetween(String text, String start, String end) {
        int s = text.indexOf(start);
        if (s < 0) return null;
        s += start.length();
        int e = text.indexOf(end, s);
        if (e < 0) return null;
        return text.substring(s, e);
    }

    public Optional<AlpacaSnapshotResponse.Snapshot> fetchSnapshot(String symbol) {
        Map<String, AlpacaSnapshotResponse.Snapshot> response = fetchSnapshots(List.of(symbol));
        if (!response.containsKey(symbol)) {
            log.warn("No snapshot returned from Alpaca for symbol: {}", symbol);
            return Optional.empty();
        }
        return Optional.ofNullable(response.get(symbol));
    }

    public Map<LocalDate, BigDecimal> fetchHistoricalDailyCloses(String symbol, LocalDate start, LocalDate end) {
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        try {
            String startStr = start.format(DateTimeFormatter.ISO_LOCAL_DATE);
            String endStr = end.format(DateTimeFormatter.ISO_LOCAL_DATE);
            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/stocks/bars")
                            .queryParam("symbols", symbol)
                            .queryParam("timeframe", "1Day")
                            .queryParam("start", startStr)
                            .queryParam("end", endStr)
                            .queryParam("limit", 400)
                            .queryParam("adjustment", "raw")
                            .build())
                    .header("APCA-API-KEY-ID", apiKeyId)
                    .header("APCA-API-SECRET-KEY", apiSecretKey)
                    .header("accept", "application/json")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            if (response == null) return result;
            JsonNode bars = response.path("bars").path(symbol);
            if (bars.isArray()) {
                for (JsonNode bar : bars) {
                    String t = bar.path("t").asText(null);
                    double close = bar.path("c").asDouble(0);
                    if (t != null && close > 0) {
                        LocalDate date = java.time.Instant.parse(t).atZone(ZoneOffset.UTC).toLocalDate();
                        result.put(date, BigDecimal.valueOf(close));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch historical bars for {}: {}", symbol, e.getMessage());
        }
        return result;
    }

    public Map<String, AlpacaSnapshotResponse.Snapshot> fetchSnapshots(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return Map.of();
        String joined = String.join(",", symbols);
        try {
            Map<String, AlpacaSnapshotResponse.Snapshot> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/stocks/snapshots")
                            .queryParam("symbols", joined)
                            .build())
                    .header("APCA-API-KEY-ID", apiKeyId)
                    .header("APCA-API-SECRET-KEY", apiSecretKey)
                    .header("accept", "application/json")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, AlpacaSnapshotResponse.Snapshot>>() {})
                    .block();
            return response != null ? response : Map.of();
        } catch (Exception e) {
            log.error("Failed to fetch Alpaca snapshots for [{}]: {}", joined, e.getMessage());
            return Map.of();
        }
    }
}
