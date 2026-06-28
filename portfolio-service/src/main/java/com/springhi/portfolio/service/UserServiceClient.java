package com.springhi.portfolio.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);

    private final WebClient webClient;

    public UserServiceClient(@Value("${backend.url:http://localhost:8080}") String backendUrl) {
        this.webClient = WebClient.builder().baseUrl(backendUrl).build();
    }

    public Map<Long, String> getDisplayNames(List<Long> ids, String jwtToken) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();
        String idsParam = ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        try {
            Map<Long, String> result = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/users/display-names")
                            .queryParam("ids", idsParam)
                            .build())
                    .header("Authorization", jwtToken)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<Long, String>>() {})
                    .block();
            return result != null ? result : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("Failed to fetch display names from user service: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
