package com.springhi.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SendGridEmailServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void postsEmailToSendGridApi() throws Exception {
        AtomicReference<String> auth = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/mail/send", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        server.start();
        try {
            SendGridEmailService service = new SendGridEmailService("test-key", "http://127.0.0.1:" + server.getAddress().getPort() + "/v3/mail/send");
            service.send(message());

            assertEquals("Bearer test-key", auth.get());
            JsonNode payload = mapper.readTree(body.get());
            assertEquals("sender@example.com", payload.path("from").path("email").asText());
            assertEquals("recipient@example.com", payload.path("personalizations").path(0).path("to").path(0).path("email").asText());
            assertEquals("SpringHi test", payload.path("subject").asText());
            assertEquals("Test message", payload.path("content").path(0).path("value").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsMissingKey() {
        SendGridEmailService service = new SendGridEmailService("", "https://api.sendgrid.com/v3/mail/send");
        assertThrows(IllegalStateException.class, () -> service.send(message()));
    }

    @Test
    void sendsLiveTestEmailOnlyWhenExplicitlyEnabled() {
        String key = System.getenv("SENDGRID_API_KEY");
        String from = System.getenv().getOrDefault("MAIL_FROM", "info@springhi.ai");
        assumeTrue("true".equalsIgnoreCase(System.getenv("SENDGRID_LIVE_TEST"))
                && key != null && !key.isBlank());

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo("ross.m.tiktin@gmail.com");
        message.setSubject("SpringHi SendGrid test email");
        message.setText("This is a test of SpringHi's SendGrid email integration.");
        new SendGridEmailService(key, "https://api.sendgrid.com/v3/mail/send").send(message);
    }

    private SimpleMailMessage message() {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("sender@example.com");
        message.setTo("recipient@example.com");
        message.setSubject("SpringHi test");
        message.setText("Test message");
        return message;
    }
}
