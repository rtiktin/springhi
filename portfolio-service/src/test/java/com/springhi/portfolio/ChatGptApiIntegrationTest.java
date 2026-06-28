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

class ChatGptApiIntegrationTest {

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-5.5";

    private static String apiKey;

    @BeforeAll
    static void loadCredentials() throws Exception {
        Path yamlPath = Paths.get("src/main/resources/application-local.yaml");
        assertTrue(Files.exists(yamlPath),
                "application-local.yaml not found at " + yamlPath.toAbsolutePath());

        boolean inOpenAiBlock = false;
        for (String line : Files.readAllLines(yamlPath)) {
            if (line.trim().equals("openai:")) {
                inOpenAiBlock = true;
                continue;
            }
            if (inOpenAiBlock) {
                if (!line.startsWith(" ") && !line.startsWith("\t")) {
                    inOpenAiBlock = false;
                } else {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("api-key:")) {
                        apiKey = trimmed.substring("api-key:".length()).trim();
                    }
                }
            }
        }

        assertNotNull(apiKey, "openai.api-key not found in application-local.yaml");
        assertFalse(apiKey.isBlank(), "openai.api-key is blank");
        assertFalse(apiKey.equals("your-openai-api-key-here"),
                "Replace 'your-openai-api-key-here' in application-local.yaml with your real OpenAI API key");
    }

    @Test
    void testChatGptReturnsValidResponse() throws Exception {
        String requestBody = """
                {
                  "model": "%s",
                  "max_completion_tokens": 256,
                  "messages": [
                    {"role": "user", "content": "Reply with exactly one sentence confirming you are working correctly."}
                  ]
                }
                """.formatted(MODEL);

        URL url = new URL(OPENAI_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
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

        assertTrue(root.has("choices"), "Response missing 'choices' array");
        assertTrue(root.get("choices").isArray(), "'choices' is not an array");
        assertTrue(root.get("choices").size() > 0, "'choices' array is empty");

        JsonNode message = root.get("choices").get(0).get("message");
        assertNotNull(message, "choices[0].message is missing");

        String text = message.get("content").asText();
        assertFalse(text.isBlank(), "ChatGPT returned empty content");

        System.out.println("ChatGPT response: " + text);

        assertTrue(root.has("model"), "Response missing 'model' field");
        assertTrue(root.has("usage"), "Response missing 'usage' field");

        JsonNode usage = root.get("usage");
        int promptTokens = usage.get("prompt_tokens").asInt();
        int completionTokens = usage.get("completion_tokens").asInt();
        System.out.printf("Tokens used — prompt: %d, completion: %d%n", promptTokens, completionTokens);

        assertTrue(promptTokens > 0, "prompt_tokens should be > 0");
        assertTrue(completionTokens > 0, "completion_tokens should be > 0");
    }
}
