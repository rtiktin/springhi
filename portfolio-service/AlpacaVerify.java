import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.stream.*;
import javax.net.ssl.*;
import java.security.*;
import java.security.cert.*;

public class AlpacaVerify {

    static void disableSslVerification() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }
        };
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAll, new SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((host, session) -> true);
    }

    public static void main(String[] args) throws Exception {
        disableSslVerification();

        // Read credentials from application-local.yaml
        Path yamlPath = Paths.get("src/main/resources/application-local.yaml");
        if (!Files.exists(yamlPath)) {
            System.err.println("FAIL: application-local.yaml not found at " + yamlPath.toAbsolutePath());
            System.exit(1);
        }

        String apiKeyId = null, apiSecretKey = null;
        for (String line : Files.readAllLines(yamlPath)) {
            String t = line.trim();
            if (t.startsWith("api_key_id:"))    apiKeyId    = t.substring("api_key_id:".length()).trim();
            if (t.startsWith("api_secret_key:")) apiSecretKey = t.substring("api_secret_key:".length()).trim();
        }

        if (apiKeyId == null || apiKeyId.isEmpty()) {
            System.err.println("FAIL: api_key_id not found in application-local.yaml");
            System.exit(1);
        }
        if (apiSecretKey == null || apiSecretKey.isEmpty()) {
            System.err.println("FAIL: api_secret_key not found in application-local.yaml");
            System.exit(1);
        }

        System.out.println("Credentials loaded OK");
        System.out.println("  api_key_id     : " + apiKeyId.substring(0, Math.min(6, apiKeyId.length())) + "...");

        // Call Alpaca snapshots endpoint
        String urlStr = "https://data.alpaca.markets/v2/stocks/snapshots?symbols=AAPL,TSLA";
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("APCA-API-KEY-ID", apiKeyId);
        conn.setRequestProperty("APCA-API-SECRET-KEY", apiSecretKey);
        conn.setRequestProperty("accept", "application/json");
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(15_000);

        int status = conn.getResponseCode();
        System.out.println("HTTP status: " + status);
        System.out.println("Response headers: " + conn.getHeaderFields());

        InputStream is = (status == 200) ? conn.getInputStream() : conn.getErrorStream();
        String body;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is))) {
            body = r.lines().collect(Collectors.joining("\n"));
        }

        System.out.println("Response (first 500 chars): " + body.substring(0, Math.min(500, body.length())));

        if (status != 200) {
            System.err.println("FAIL: Expected HTTP 200, got " + status);
            System.exit(1);
        }

        // Basic assertions without a JSON library
        boolean hasAapl = body.contains("\"AAPL\"");
        boolean hasTsla = body.contains("\"TSLA\"");
        boolean hasLatestTrade = body.contains("\"latestTrade\"");

        System.out.println("\nAssertions:");
        System.out.println("  Response contains AAPL        : " + (hasAapl ? "PASS" : "FAIL"));
        System.out.println("  Response contains TSLA        : " + (hasTsla ? "PASS" : "FAIL"));
        System.out.println("  Response contains latestTrade : " + (hasLatestTrade ? "PASS" : "FAIL"));

        if (!hasAapl || !hasTsla || !hasLatestTrade) {
            System.err.println("\nFAIL: One or more assertions failed");
            System.exit(1);
        }

        System.out.println("\nPASS: Alpaca API is reachable and returns valid price data");
    }
}
