package com.springhi.user.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Service
public class GoogleOAuthService {

    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String ISSUER = "https://accounts.google.com";

    private final WebClient webClient;
    private final JwtDecoder jwtDecoder;

    @Value("${application.security.google.client-id:}")
    private String clientId;

    @Value("${application.security.google.client-secret:}")
    private String clientSecret;

    public GoogleOAuthService(WebClient.Builder builder) {
        this.webClient = builder.build();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build();
        OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(ISSUER);
        decoder.setJwtValidator(validator);
        this.jwtDecoder = decoder;
    }

    public record GoogleUserInfo(String sub, String email, String givenName, String familyName) {}

    public GoogleUserInfo exchange(String code) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new RuntimeException("Google sign-in is not configured on the server.");
        }
        Map<String, Object> tokenResponse;
        try {
            tokenResponse = webClient.post()
                    .uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("code", code)
                            .with("client_id", clientId)
                            .with("client_secret", clientSecret)
                            .with("redirect_uri", "postmessage")
                            .with("grant_type", "authorization_code"))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
        } catch (WebClientResponseException e) {
            throw new RuntimeException("Google sign-in failed: the authorization code is invalid or expired.");
        }
        if (tokenResponse == null) {
            throw new RuntimeException("Google sign-in failed: no response from Google.");
        }
        if (tokenResponse.containsKey("error")) {
            throw new RuntimeException("Google sign-in failed: "
                    + tokenResponse.getOrDefault("error_description", tokenResponse.get("error")));
        }
        String idToken = (String) tokenResponse.get("id_token");
        if (idToken == null || idToken.isBlank()) {
            throw new RuntimeException("Google sign-in failed: missing id_token.");
        }

        Jwt jwt = jwtDecoder.decode(idToken);
        List<String> audience = jwt.getAudience();
        if (audience == null || !audience.contains(clientId)) {
            throw new RuntimeException("Google sign-in failed: token audience mismatch.");
        }
        Object emailVerified = jwt.getClaim("email_verified");
        boolean verified = Boolean.TRUE.equals(emailVerified)
                || "true".equalsIgnoreCase(String.valueOf(emailVerified));
        if (!verified) {
            throw new RuntimeException("Google sign-in failed: email is not verified by Google.");
        }
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Google sign-in failed: no email in Google token.");
        }
        return new GoogleUserInfo(
                jwt.getClaimAsString("sub"),
                email.trim().toLowerCase(),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name")
        );
    }
}
