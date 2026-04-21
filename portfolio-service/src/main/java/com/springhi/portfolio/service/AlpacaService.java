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

import javax.net.ssl.SSLException;
import java.util.Map;
import java.util.Optional;

@Service
public class AlpacaService {

    private static final Logger log = LoggerFactory.getLogger(AlpacaService.class);

    private final WebClient webClient;
    private final String apiKeyId;
    private final String apiSecretKey;

    public AlpacaService(
            @Value("${alpaca.base-url}") String baseUrl,
            @Value("${alpaca.api_key_id}") String apiKeyId,
            @Value("${alpaca.api_secret_key}") String apiSecretKey) throws SSLException {
        this.apiKeyId = apiKeyId;
        this.apiSecretKey = apiSecretKey;

        io.netty.handler.ssl.SslContext sslContext = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
        HttpClient httpClient = HttpClient.create().secure(t -> t.sslContext(sslContext));

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    public Optional<AlpacaSnapshotResponse.Snapshot> fetchSnapshot(String symbol) {
        try {
            Map<String, AlpacaSnapshotResponse.Snapshot> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/stocks/snapshots")
                            .queryParam("symbols", symbol)
                            .build())
                    .header("APCA-API-KEY-ID", apiKeyId)
                    .header("APCA-API-SECRET-KEY", apiSecretKey)
                    .header("accept", "application/json")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, AlpacaSnapshotResponse.Snapshot>>() {})
                    .block();

            if (response == null || !response.containsKey(symbol)) {
                log.warn("No snapshot returned from Alpaca for symbol: {}", symbol);
                return Optional.empty();
            }
            return Optional.ofNullable(response.get(symbol));
        } catch (Exception e) {
            log.error("Failed to fetch Alpaca snapshot for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }
}
