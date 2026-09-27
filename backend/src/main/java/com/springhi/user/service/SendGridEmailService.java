package com.springhi.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

@Service
public class SendGridEmailService {

    private final String apiKey;
    private final URI url;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public SendGridEmailService(@Value("${application.mail.sendgrid.api-key:}") String apiKey,
                                @Value("${application.mail.sendgrid.url}") String url) {
        this.apiKey = apiKey;
        this.url = URI.create(url);
    }

    public void send(SimpleMailMessage message) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("SendGrid API key is not configured.");
        }
        if (message.getFrom() == null || message.getTo() == null || message.getTo().length == 0) {
            throw new IllegalArgumentException("Email sender and recipient are required.");
        }

        try {
            String body = mapper.writeValueAsString(Map.of(
                    "from", Map.of("email", message.getFrom()),
                    "personalizations", new Object[]{Map.of("to", Arrays.stream(message.getTo())
                            .map(address -> Map.of("email", address)).toList())},
                    "subject", message.getSubject(),
                    "content", new Object[]{Map.of("type", "text/plain", "value", message.getText())}
            ));
            HttpRequest request = HttpRequest.newBuilder(url)
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 202) {
                throw new IllegalStateException("SendGrid rejected email (HTTP " + response.statusCode() + ").");
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not format email request.", e);
        } catch (IOException e) {
            throw new IllegalStateException("Could not reach SendGrid.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Email delivery interrupted.", e);
        }
    }
}
