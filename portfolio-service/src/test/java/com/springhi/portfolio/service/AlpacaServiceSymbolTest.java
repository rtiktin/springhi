package com.springhi.portfolio.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlpacaServiceSymbolTest {

    private HttpServer server;
    private AlpacaService service;
    private final AtomicReference<String> snapshotQuery = new AtomicReference<>();
    private final AtomicReference<String> barsQuery = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/stocks/snapshots", exchange -> {
            snapshotQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] body = "{\"BRK.B\":{\"latestTrade\":{\"p\":510.25}},\"AAPL\":{\"latestTrade\":{\"p\":200}}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.createContext("/v2/stocks/bars", exchange -> {
            barsQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] body = "{\"bars\":{\"BRK.B\":[{\"t\":\"2026-09-25T00:00:00Z\",\"c\":510.25}]}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.start();
        service = new AlpacaService("http://127.0.0.1:" + server.getAddress().getPort(), "test", "test", false);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void mapsBerkshireSymbolForSingleAndBatchSnapshots() {
        assertEquals(new BigDecimal("510.25"), service.fetchSnapshot("BRK-B").orElseThrow().latestTrade().price());
        assertTrue(snapshotQuery.get().contains("BRK.B"));
        assertEquals(List.of("BRK-B", "AAPL"), List.copyOf(service.fetchSnapshots(List.of("BRK-B", "AAPL")).keySet()));
        assertTrue(snapshotQuery.get().contains("BRK.B"));
        assertTrue(snapshotQuery.get().contains("AAPL"));
    }

    @Test
    void mapsBerkshireSymbolForHistoricalBars() {
        assertEquals(new BigDecimal("510.25"), service.fetchHistoricalDailyCloses("BRK-B", LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 26))
                .get(LocalDate.of(2026, 9, 25)));
        assertTrue(barsQuery.get().contains("BRK.B"));
    }
}
