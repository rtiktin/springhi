package com.springhi.user;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;

public class TelnyxSmsTest {

    private static final String FROM_NUMBER = "+15162000271";
    private static final String TO_NUMBER   = "+19178607374";

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("TELNYX_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("TELNYX_API_KEY environment variable is not set.");
            System.exit(1);
        }

        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        String body = String.format(
                "{\"from\":\"%s\",\"to\":\"%s\",\"text\":\"Your security code is: %s\"}",
                FROM_NUMBER, TO_NUMBER, code);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.telnyx.com/v2/messages"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Status : " + response.statusCode());
        System.out.println("Body   : " + response.body());

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            System.out.println("Security code sent successfully: " + code);
        } else {
            System.err.println("Failed to send SMS. See body above for details.");
        }
    }
}
