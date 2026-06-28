package com.springhi.portfolio.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private final WebClient webClient;
    private final String apiKey;
    private final String model;

    public GeminiService(
            @Value("${gemini.base-url}") String baseUrl,
            @Value("${gemini.model}") String model,
            @Value("${gemini.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.model = model;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public String generateContent(String prompt) {
        String path = "/v1beta/models/" + model + ":generateContent";

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of("temperature", 0.0, "seed", 42)
        );

        log.info("Calling Gemini model={}", model);

        Map<?, ?> response = webClient.post()
                .uri(path)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) {
            throw new RuntimeException("Gemini returned null response");
        }

        try {
            @SuppressWarnings("unchecked")
            List<Map<?, ?>> candidates = (List<Map<?, ?>>) response.get("candidates");
            Map<?, ?> content = (Map<?, ?>) candidates.get(0).get("content");
            @SuppressWarnings("unchecked")
            List<Map<?, ?>> parts = (List<Map<?, ?>>) content.get("parts");
            return (String) parts.get(0).get("text");
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", response, e);
            throw new RuntimeException("Failed to parse Gemini response: " + e.getMessage());
        }
    }
}
