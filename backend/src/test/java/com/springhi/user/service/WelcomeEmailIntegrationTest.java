package com.springhi.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springhi.user.dto.SignupRequest;
import com.springhi.user.repository.UserRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class WelcomeEmailIntegrationTest {

    private static final AtomicReference<String> sentBody = new AtomicReference<>();
    private static final HttpServer server;

    static {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v3/mail/send", exchange -> {
                sentBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
            });
            server.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void sendGridProperties(DynamicPropertyRegistry registry) {
        registry.add("application.mail.sendgrid.api-key", () -> "test-key");
        registry.add("application.mail.sendgrid.url", () -> "http://127.0.0.1:" + server.getAddress().getPort() + "/v3/mail/send");
    }

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void committedSignupSendsWelcomeEmailFromVerifiedSender() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        SignupRequest request = new SignupRequest();
        request.setUsername("welcome_" + suffix);
        request.setEmail("welcome_" + suffix + "@example.com");
        request.setPassword("password123");
        sentBody.set(null);
        try {
            authService.signup(request);

            String body = sentBody.get();
            assertNotNull(body);
            JsonNode payload = new ObjectMapper().readTree(body);
            assertEquals("info@springhi.ai", payload.path("from").path("email").asText());
            assertEquals(request.getEmail(), payload.path("personalizations").path(0).path("to").path(0).path("email").asText());
            assertEquals("Welcome to SpringHi.ai", payload.path("subject").asText());
        } finally {
            userRepository.findByUsername(request.getUsername()).ifPresent(userRepository::delete);
        }
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }
}
