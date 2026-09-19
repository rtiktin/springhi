package com.springhi.portfolio.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Service
public class ChatGptService {

    private static final Logger log = LoggerFactory.getLogger(ChatGptService.class);

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public ChatGptService(
            @Value("${openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${openai.model:gpt-5.5}") String model,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.max-tokens:2048}") int maxTokens) {
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public String generateContent(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_completion_tokens", maxTokens,
                "seed", 42,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        log.info("Calling ChatGPT model={}", model);

        Map<?, ?> response;
        try {
            response = webClient.post()
                    .uri("/v1/chat/completions")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("ChatGPT API error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("ChatGPT API error " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        }

        if (response == null) {
            throw new RuntimeException("ChatGPT returned null response");
        }

        try {
            @SuppressWarnings("unchecked")
            List<Map<?, ?>> choices = (List<Map<?, ?>>) response.get("choices");
            Map<?, ?> choice = choices.get(0);
            Map<?, ?> message = (Map<?, ?>) choice.get("message");
            String content = message.get("content") instanceof String c ? c : null;
            String refusal = message.get("refusal") instanceof String r ? r : null;
            String finishReason = choice.get("finish_reason") instanceof String f ? f : null;

            if (content == null || content.isBlank()) {
                log.error("ChatGPT returned empty content. model={}, finish_reason={}, refusal={}, usage={}, rawResponse={}",
                        model, finishReason, refusal, response.get("usage"), response);
                if (refusal != null && !refusal.isBlank()) {
                    throw new RuntimeException("ChatGPT refused to respond: " + refusal);
                }
                throw new RuntimeException("ChatGPT returned empty content (finish_reason=" + finishReason
                        + "). The reasoning model likely exhausted max_completion_tokens=" + maxTokens
                        + " before emitting output — raise OPENAI_MAX_TOKENS.");
            }
            return content;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse ChatGPT response: {}", response, e);
            throw new RuntimeException("Failed to parse ChatGPT response: " + e.getMessage());
        }
    }
}
