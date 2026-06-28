package com.springhi.portfolio.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class ClaudeService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public ClaudeService(
            @Value("${claude.base-url:https://api.anthropic.com}") String baseUrl,
            @Value("${claude.model:claude-haiku-4-5}") String model,
            @Value("${claude.api-key:}") String apiKey,
            @Value("${claude.max-tokens:2048}") int maxTokens) {
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public String generateContent(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "temperature", 0.0,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        log.info("Calling Claude model={}", model);

        Map<?, ?> response = webClient.post()
                .uri("/v1/messages")
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) {
            throw new RuntimeException("Claude returned null response");
        }

        try {
            @SuppressWarnings("unchecked")
            List<Map<?, ?>> content = (List<Map<?, ?>>) response.get("content");
            return (String) content.get(0).get("text");
        } catch (Exception e) {
            log.error("Failed to parse Claude response: {}", response, e);
            throw new RuntimeException("Failed to parse Claude response: " + e.getMessage());
        }
    }
}
