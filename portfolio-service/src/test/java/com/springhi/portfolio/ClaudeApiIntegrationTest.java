package com.springhi.portfolio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ClaudeApiIntegrationTest {

    private static final String CLAUDE_URL = "https://api.anthropic.com/v1/messages";
    private static final String CLAUDE_VERSION = "2023-06-01";
    private static final String MODEL = "claude-sonnet-4-6";

    private static String apiKey;

    @BeforeAll
    static void loadCredentials() throws Exception {
        Path yamlPath = Paths.get("src/main/resources/application-local.yaml");
        assertTrue(Files.exists(yamlPath),
                "application-local.yaml not found at " + yamlPath.toAbsolutePath());

        boolean inClaudeBlock = false;
        for (String line : Files.readAllLines(yamlPath)) {
            if (line.trim().equals("claude:")) {
                inClaudeBlock = true;
                continue;
            }
            if (inClaudeBlock) {
                if (!line.startsWith(" ") && !line.startsWith("\t")) {
                    inClaudeBlock = false;
                } else {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("api-key:")) {
                        apiKey = trimmed.substring("api-key:".length()).trim();
                    }
                }
            }
        }

        assertNotNull(apiKey, "claude.api-key not found in application-local.yaml");
        assertFalse(apiKey.isBlank(), "claude.api-key is blank");
        assertFalse(apiKey.equals("your-claude-api-key-here"),
                "Replace 'your-claude-api-key-here' in application-local.yaml with your real Claude API key");
    }

    @Test
    void testClaudeReturnsValidResponse() throws Exception {
        String requestBody = """
                {
                  "model": "%s",
                  "max_tokens": 256,
                  "messages": [
                    {"role": "user", "content": "Reply with exactly one sentence confirming you are working correctly."}
                  ]
                }
                """.formatted(MODEL);

        URL url = new URL(CLAUDE_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-api-key", apiKey);
        conn.setRequestProperty("anthropic-version", CLAUDE_VERSION);
        conn.setDoOutput(true);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }

        int statusCode = conn.getResponseCode();

        String body;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        statusCode >= 200 && statusCode < 300
                                ? conn.getInputStream()
                                : conn.getErrorStream()))) {
            body = reader.lines().collect(Collectors.joining("\n"));
        }

        System.out.println("HTTP status: " + statusCode);
        System.out.println("Response body: " + body);

        assertEquals(200, statusCode,
                "Expected HTTP 200 but got " + statusCode + ": " + body);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(body);

        assertTrue(root.has("content"), "Response missing 'content' array");
        assertTrue(root.get("content").isArray(), "'content' is not an array");
        assertTrue(root.get("content").size() > 0, "'content' array is empty");

        JsonNode firstBlock = root.get("content").get(0);
        assertEquals("text", firstBlock.get("type").asText(), "First content block type is not 'text'");

        String text = firstBlock.get("text").asText();
        assertFalse(text.isBlank(), "Claude returned empty text");

        System.out.println("Claude response: " + text);

        assertTrue(root.has("model"), "Response missing 'model' field");
        assertTrue(root.has("usage"), "Response missing 'usage' field");

        JsonNode usage = root.get("usage");
        int inputTokens = usage.get("input_tokens").asInt();
        int outputTokens = usage.get("output_tokens").asInt();
        System.out.printf("Tokens used — input: %d, output: %d%n", inputTokens, outputTokens);

        assertTrue(inputTokens > 0, "input_tokens should be > 0");
        assertTrue(outputTokens > 0, "output_tokens should be > 0");
    }
}
