package com.springhi.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class TelnyxService {

    private static final Logger log = LoggerFactory.getLogger(TelnyxService.class);

    @Value("${application.telnyx.api-key:}")
    private String apiKey;

    @Value("${application.telnyx.from-number:+15162000271}")
    private String fromNumber;

    public void sendSms(String toNumber, String text) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("Telnyx API key is not configured.");
        }
        String normalizedTo = toNumber.trim().replaceAll("[\\s\\-\\(\\)]", "");
        if (!normalizedTo.startsWith("+")) {
            normalizedTo = "+1" + normalizedTo;
        }
        log.info("Telnyx sendSms: from={} to={}", fromNumber, normalizedTo);
        String body = String.format(
                "{\"from\":\"%s\",\"to\":\"%s\",\"text\":\"%s\"}",
                fromNumber, normalizedTo, text.replace("\"", "\\\""));
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telnyx.com/v2/messages"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Telnyx response: status={} body={}", response.statusCode(), response.body());
            if (response.statusCode() != 200 && response.statusCode() != 201) {
                throw new RuntimeException("SMS send failed (HTTP " + response.statusCode() + "): " + response.body());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to send SMS: " + e.getMessage(), e);
        }
    }
}
