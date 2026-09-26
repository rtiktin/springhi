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
import java.util.ArrayList;
import java.util.Iterator;
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

    private static String alpacaStockSymbol(String symbol) {
        return "BRK-B".equalsIgnoreCase(symbol) ? "BRK.B" : symbol;
    }

    public Map<LocalDate, BigDecimal> fetchHistoricalDailyCloses(String symbol, LocalDate start, LocalDate end) {
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        try {
            String alpacaSymbol = alpacaStockSymbol(symbol);
            String startStr = start.format(DateTimeFormatter.ISO_LOCAL_DATE);
            String endStr = end.format(DateTimeFormatter.ISO_LOCAL_DATE);
            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/stocks/bars")
                            .queryParam("symbols", alpacaSymbol)
                            .queryParam("timeframe", "1Day")
                            .queryParam("start", startStr)
                            .queryParam("end", endStr)
                            .queryParam("limit", 400)
                            .queryParam("adjustment", "raw")
                            .queryParam("feed", "iex")
                            .build())
                    .header("APCA-API-KEY-ID", apiKeyId)
                    .header("APCA-API-SECRET-KEY", apiSecretKey)
                    .header("accept", "application/json")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            if (response == null) return result;
            JsonNode bars = response.path("bars").path(alpacaSymbol);
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

    public record DividendInfo(LocalDate exDate, BigDecimal amount) {}

    public List<DividendInfo> fetchDividends(String symbol, LocalDate from, LocalDate to) {
        List<DividendInfo> results = new ArrayList<>();
        try {
            long period1 = from.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
            long period2 = to.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
            String urlStr = "https://query1.finance.yahoo.com/v8/finance/chart/"
                    + symbol.toUpperCase()
                    + "?period1=" + period1
                    + "&period2=" + period2
                    + "&interval=1d&events=div";

            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", BROWSER_USER_AGENT);
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(8_000);
            conn.setInstanceFollowRedirects(true);

            if (conn.getResponseCode() != 200) {
                log.info("Yahoo Finance dividends returned HTTP {} for {}", conn.getResponseCode(), symbol);
                return results;
            }

            String body;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                body = reader.lines().collect(Collectors.joining("\n"));
            }

            JsonNode root = MAPPER.readTree(body);
            JsonNode dividends = root.path("chart").path("result").path(0)
                    .path("events").path("dividends");

            if (!dividends.isMissingNode() && dividends.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = dividends.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    JsonNode div = entry.getValue();
                    long ts = div.path("date").asLong(0);
                    double amount = div.path("amount").asDouble(0);
                    if (ts > 0 && amount > 0) {
                        LocalDate exDate = java.time.Instant.ofEpochSecond(ts)
                                .atZone(ZoneOffset.UTC).toLocalDate();
                        if (!exDate.isBefore(from) && !exDate.isAfter(to)) {
                            results.add(new DividendInfo(exDate, BigDecimal.valueOf(amount)));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch dividends for {}: {}", symbol, e.getMessage());
        }
        return results;
    }

    public Map<String, AlpacaSnapshotResponse.Snapshot> fetchSnapshots(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return Map.of();
        String joined = symbols.stream().map(AlpacaService::alpacaStockSymbol).distinct().collect(Collectors.joining(","));
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
            if (response == null) return Map.of();
            Map<String, AlpacaSnapshotResponse.Snapshot> result = new LinkedHashMap<>();
            for (String symbol : symbols) {
                AlpacaSnapshotResponse.Snapshot snapshot = response.get(alpacaStockSymbol(symbol));
                if (snapshot != null) result.put(symbol, snapshot);
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to fetch Alpaca snapshots for [{}]: {}", joined, e.getMessage());
            return Map.of();
        }
    }

    public Optional<AlpacaSnapshotResponse.Snapshot> fetchCryptoSnapshot(String symbol) {
        String ySymbol = CryptoSymbols.yahooSymbol(symbol);
        JsonNode root = fetchJson("https://query1.finance.yahoo.com/v8/finance/chart/" + ySymbol + "?interval=1d");
        if (root == null) return Optional.empty();
        JsonNode result = root.path("chart").path("result").path(0);
        if (result.isMissingNode() || result.isNull()) {
            log.warn("No crypto snapshot from Yahoo for {} ({})", symbol, ySymbol);
            return Optional.empty();
        }
        JsonNode meta = result.path("meta");
        JsonNode quote = result.path("indicators").path("quote").path(0);
        JsonNode timestamps = result.path("timestamp");
        int count = timestamps.isArray() ? timestamps.size() : 0;

        AlpacaSnapshotResponse.Bar dailyBar = cryptoBar(quote, timestamps, count - 1);
        AlpacaSnapshotResponse.Bar prevDailyBar = cryptoBar(quote, timestamps, count - 2);

        BigDecimal latestPrice = scalar(meta, "regularMarketPrice");
        if (latestPrice == null && dailyBar != null) latestPrice = dailyBar.close();
        AlpacaSnapshotResponse.Trade latestTrade = latestPrice != null
                ? new AlpacaSnapshotResponse.Trade(latestPrice, null, java.time.Instant.now().toString())
                : null;

        if (dailyBar == null && latestTrade == null) {
            log.warn("No usable crypto data from Yahoo for {}", symbol);
            return Optional.empty();
        }
        return Optional.of(new AlpacaSnapshotResponse.Snapshot(dailyBar, prevDailyBar, latestTrade));
    }

    public Map<LocalDate, BigDecimal> fetchCryptoDailyCloses(String symbol, LocalDate start, LocalDate end) {
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        long period1 = start.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        long period2 = end.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        String ySymbol = CryptoSymbols.yahooSymbol(symbol);
        JsonNode root = fetchJson("https://query1.finance.yahoo.com/v8/finance/chart/" + ySymbol
                + "?period1=" + period1 + "&period2=" + period2 + "&interval=1d");
        if (root == null) return result;
        JsonNode result0 = root.path("chart").path("result").path(0);
        if (result0.isMissingNode() || result0.isNull()) return result;
        JsonNode timestamps = result0.path("timestamp");
        JsonNode closes = result0.path("indicators").path("quote").path(0).path("close");
        if (!timestamps.isArray() || !closes.isArray()) return result;
        int n = Math.min(timestamps.size(), closes.size());
        for (int i = 0; i < n; i++) {
            JsonNode t = timestamps.get(i);
            JsonNode c = closes.get(i);
            if (t == null || t.isNull() || c == null || c.isNull() || c.isMissingNode()) continue;
            long epoch = t.asLong(0);
            double close = c.asDouble(0);
            if (epoch > 0 && close > 0) {
                LocalDate date = java.time.Instant.ofEpochSecond(epoch).atZone(ZoneOffset.UTC).toLocalDate();
                result.put(date, BigDecimal.valueOf(close));
            }
        }
        log.info("Fetched {} crypto daily bars for {} from Yahoo", result.size(), symbol);
        return result;
    }

    private JsonNode fetchJson(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", BROWSER_USER_AGENT);
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(8_000);
            conn.setInstanceFollowRedirects(true);
            if (conn.getResponseCode() != 200) {
                log.info("HTTP {} from {}", conn.getResponseCode(), urlStr);
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                return MAPPER.readTree(reader.lines().collect(Collectors.joining("\n")));
            }
        } catch (Exception e) {
            log.warn("Fetch failed for {}: {}", urlStr, e.getMessage());
            return null;
        }
    }

    private AlpacaSnapshotResponse.Bar cryptoBar(JsonNode quote, JsonNode timestamps, int i) {
        if (i < 0 || quote == null || quote.isMissingNode()) return null;
        BigDecimal open = arrayDecimal(quote, "open", i);
        BigDecimal high = arrayDecimal(quote, "high", i);
        BigDecimal low = arrayDecimal(quote, "low", i);
        BigDecimal close = arrayDecimal(quote, "close", i);
        Long volume = arrayLong(quote, "volume", i);
        String ts = null;
        if (timestamps != null && timestamps.isArray() && i < timestamps.size()) {
            JsonNode t = timestamps.get(i);
            if (t != null && !t.isNull()) {
                long epoch = t.asLong(0);
                if (epoch > 0) ts = java.time.Instant.ofEpochSecond(epoch).toString();
            }
        }
        if (open == null && high == null && low == null && close == null && volume == null && ts == null) {
            return null;
        }
        return new AlpacaSnapshotResponse.Bar(open, high, low, close, volume, ts);
    }

    private BigDecimal scalar(JsonNode parent, String field) {
        JsonNode n = parent.path(field);
        return (n.isMissingNode() || n.isNull()) ? null : BigDecimal.valueOf(n.asDouble(0));
    }

    private BigDecimal arrayDecimal(JsonNode holder, String field, int i) {
        JsonNode arr = holder.path(field);
        if (!arr.isArray() || i < 0 || i >= arr.size()) return null;
        JsonNode n = arr.get(i);
        return (n == null || n.isNull() || n.isMissingNode()) ? null : BigDecimal.valueOf(n.asDouble(0));
    }

    private Long arrayLong(JsonNode holder, String field, int i) {
        JsonNode arr = holder.path(field);
        if (!arr.isArray() || i < 0 || i >= arr.size()) return null;
        JsonNode n = arr.get(i);
        return (n == null || n.isNull() || n.isMissingNode()) ? null : n.asLong(0);
    }
}
