package org.programhub.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Writes to Google Sheets as ProgramHub's Google service account. An admin shares their spreadsheet with the
 * service account's email (as Editor); no per-admin Google sign-in. Configured with GOOGLE_SERVICE_ACCOUNT_JSON
 * (the service account's JSON key, as downloaded from Google Cloud — raw or base64).
 */
@Component
@Slf4j
public class GoogleSheetsClient {

    private static final String SCOPE = "https://www.googleapis.com/auth/spreadsheets";
    private static final Pattern SHEET_URL = Pattern.compile("/spreadsheets/(?:u/\\d+/)?d/([a-zA-Z0-9-_]{20,})");
    private static final Pattern BARE_ID = Pattern.compile("^[a-zA-Z0-9-_]{30,}$");

    /** A Sheets call failed; {@code status} is the HTTP status (403 = not shared, 404 = no such sheet). */
    public static class SheetsException extends RuntimeException {
        public final int status;
        public SheetsException(int status, String message) { super(message); this.status = status; }
    }

    public record Spreadsheet(String title, List<String> tabs) {}

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String clientEmail;
    private final PrivateKey privateKey;
    private final String tokenUri;
    /** Sheets API base (overridable for local testing). */
    private final String api;
    private String token;
    private Instant tokenExpires = Instant.EPOCH;

    public GoogleSheetsClient(@Value("${google.service-account-json:}") String keyJson) {
        this(keyJson, "https://sheets.googleapis.com/v4/spreadsheets/");
    }

    @Autowired
    public GoogleSheetsClient(@Value("${google.service-account-json:}") String keyJson,
                              @Value("${google.sheets-api:https://sheets.googleapis.com/v4/spreadsheets/}") String api) {
        this.api = api.endsWith("/") ? api : api + "/";
        String email = null, uri = "https://oauth2.googleapis.com/token";
        PrivateKey key = null;
        if (keyJson != null && !keyJson.isBlank()) {
            try {
                String raw = keyJson.trim();
                if (!raw.startsWith("{")) raw = new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
                JsonNode n = json.readTree(raw);
                email = n.path("client_email").asText(null);
                if (n.hasNonNull("token_uri")) uri = n.get("token_uri").asText();
                String pem = n.path("private_key").asText("")
                    .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
                key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
            } catch (Exception e) {
                log.error("GOOGLE_SERVICE_ACCOUNT_JSON is set but couldn't be read — Google Sheets backup is off: {}", e.getMessage());
                email = null; key = null;
            }
        }
        this.clientEmail = email;
        this.privateKey = key;
        this.tokenUri = uri;
    }

    public boolean isConfigured() { return clientEmail != null && privateKey != null; }

    /** The email admins share their sheet with. */
    public String serviceAccountEmail() { return clientEmail; }

    /** The spreadsheet id from a pasted link (or a bare id); null when it doesn't look like a Google Sheet. */
    public static String sheetId(String urlOrId) {
        if (urlOrId == null) return null;
        String v = urlOrId.trim();
        Matcher m = SHEET_URL.matcher(v);
        if (m.find()) return m.group(1);
        return BARE_ID.matcher(v).matches() ? v : null;
    }

    private synchronized String accessToken() throws Exception {
        if (token != null && Instant.now().isBefore(tokenExpires.minusSeconds(60))) return token;
        Instant now = Instant.now();
        String assertion = Jwts.builder()
            .issuer(clientEmail)
            .claim("scope", SCOPE)
            .audience().single(tokenUri)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(3600)))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();
        String form = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
            + "&assertion=" + URLEncoder.encode(assertion, StandardCharsets.UTF_8);
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(tokenUri))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) throw new SheetsException(res.statusCode(), "Google sign-in failed: " + res.body());
        JsonNode n = json.readTree(res.body());
        token = n.path("access_token").asText();
        tokenExpires = now.plusSeconds(n.path("expires_in").asLong(3600));
        return token;
    }

    private JsonNode call(String method, String url, Object body) {
        if (!isConfigured()) throw new SheetsException(503, "Google Sheets isn't set up on this server");
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken()).header("Content-Type", "application/json");
            HttpRequest.BodyPublisher pub = body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
            HttpResponse<String> res = http.send(b.method(method, pub).build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) {
                String msg = res.body();
                try { msg = json.readTree(res.body()).path("error").path("message").asText(msg); } catch (Exception ignored) { /* raw body */ }
                throw new SheetsException(res.statusCode(), msg);
            }
            return res.body() == null || res.body().isBlank() ? json.createObjectNode() : json.readTree(res.body());
        } catch (SheetsException e) {
            throw e;
        } catch (Exception e) {
            throw new SheetsException(502, "Couldn't reach Google Sheets: " + e.getMessage());
        }
    }

    private static String range(String tab, String cells) {
        String quoted = "'" + tab.replace("'", "''") + "'" + (cells == null ? "" : "!" + cells);
        return URLEncoder.encode(quoted, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Title and tab names — also proves the sheet is shared with us. */
    public Spreadsheet open(String sheetId) {
        JsonNode n = call("GET", api + sheetId + "?fields=properties.title,sheets.properties.title", null);
        List<String> tabs = new ArrayList<>();
        n.path("sheets").forEach(s -> tabs.add(s.path("properties").path("title").asText()));
        return new Spreadsheet(n.path("properties").path("title").asText(""), tabs);
    }

    /** Adds the tab when it's missing, with the heading row frozen. */
    public void ensureTab(String sheetId, String tab, List<String> existingTabs) {
        if (existingTabs.contains(tab)) return;
        call("POST", api + sheetId + ":batchUpdate", Map.of("requests", List.of(Map.of("addSheet", Map.of("properties",
            Map.of("title", tab, "gridProperties", Map.of("frozenRowCount", 1)))))));
    }

    /**
     * Replaces the tab's contents with {@code rows} (written as plain text, never as formulas), then clears
     * anything left below, e.g. after someone was deleted.
     */
    public void replace(String sheetId, String tab, List<List<String>> rows) {
        call("PUT", api + sheetId + "/values/" + range(tab, "A1") + "?valueInputOption=RAW",
            Map.of("range", "'" + tab.replace("'", "''") + "'!A1", "majorDimension", "ROWS", "values", rows));
        call("POST", api + sheetId + "/values/" + range(tab, "A" + (rows.size() + 1) + ":ZZZ") + ":clear", Map.of());
    }
}
