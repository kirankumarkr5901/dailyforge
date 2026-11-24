package com.dailyforge.push.domain;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Stores subscriptions and delivers notifications to them. A push service answering
 * 404 or 410 means the browser dropped that subscription (uninstalled, permission
 * revoked), so the row is deleted instead of retried forever.
 */
@Component
public class PushSender {

    private static final Logger log = LoggerFactory.getLogger(PushSender.class);

    private final JdbcTemplate jdbc;
    private final PushProperties props;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public PushSender(JdbcTemplate jdbc, PushProperties props, ObjectMapper json) {
        this.jdbc = jdbc;
        this.props = props;
        this.json = json;
    }

    public record Subscription(String endpoint, String p256dh, String auth) {}

    /** What the phone shows. {@code url} is where tapping it opens the app. */
    public record Message(String title, String body, String tag, String url) {}

    public boolean enabled() {
        return props.enabled();
    }

    public String publicKey() {
        return props.vapidPublicKey();
    }

    /** Saves a subscription for this user; the same endpoint again just moves to them. */
    public void subscribe(UUID userId, Subscription s) {
        jdbc.update("DELETE FROM push_subscription WHERE endpoint = ?", s.endpoint());
        jdbc.update(
                "INSERT INTO push_subscription (id, user_id, endpoint, p256dh, auth, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                userId,
                s.endpoint(),
                s.p256dh(),
                s.auth(),
                Timestamp.from(Instant.now()));
    }

    public void unsubscribe(UUID userId, String endpoint) {
        jdbc.update("DELETE FROM push_subscription WHERE user_id = ? AND endpoint = ?", userId, endpoint);
    }

    public List<Subscription> subscriptions(UUID userId) {
        return jdbc.query(
                "SELECT endpoint, p256dh, auth FROM push_subscription WHERE user_id = ?",
                (rs, i) -> new Subscription(rs.getString(1), rs.getString(2), rs.getString(3)),
                userId);
    }

    /** Sends to every device the user subscribed. Returns how many accepted it. */
    public int send(UUID userId, Message message, int ttlSeconds) {
        if (!enabled()) {
            return 0;
        }
        byte[] payload = payload(message);
        int delivered = 0;
        for (Subscription s : subscriptions(userId)) {
            if (deliver(s, payload, ttlSeconds)) {
                delivered++;
            }
        }
        return delivered;
    }

    /**
     * The shape Angular's service worker turns into a notification by itself, including
     * what a tap does ({@code onActionClick}): bring the app forward, or open it.
     */
    private byte[] payload(Message m) {
        var notification = new java.util.LinkedHashMap<String, Object>();
        notification.put("title", m.title());
        notification.put("body", m.body());
        notification.put("icon", "/icon-192.png");
        notification.put("badge", "/icon-192.png");
        notification.put("tag", m.tag());
        notification.put("renotify", true);
        notification.put("vibrate", List.of(300, 120, 300));
        notification.put(
                "data",
                java.util.Map.of(
                        "onActionClick",
                        java.util.Map.of("default", java.util.Map.of("operation", "focusLastFocusedOrOpen", "url", m.url()))));
        return json.writeValueAsString(java.util.Map.of("notification", notification)).getBytes(StandardCharsets.UTF_8);
    }

    private boolean deliver(Subscription s, byte[] payload, int ttlSeconds) {
        try {
            URI endpoint = URI.create(s.endpoint());
            String audience = endpoint.getScheme() + "://" + endpoint.getHost();
            byte[] body = WebPushCrypto.encrypt(payload, WebPushCrypto.decode(s.p256dh()), WebPushCrypto.decode(s.auth()));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(15))
                    .header("TTL", String.valueOf(ttlSeconds))
                    .header("Urgency", "high")
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("Authorization", WebPushCrypto.vapidAuthorization(
                            audience,
                            props.subject(),
                            WebPushCrypto.decode(props.vapidPublicKey()),
                            WebPushCrypto.decode(props.vapidPrivateKey()),
                            Instant.now().getEpochSecond()))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 404 || status == 410) {
                jdbc.update("DELETE FROM push_subscription WHERE endpoint = ?", s.endpoint());
                return false;
            }
            if (status >= 300) {
                log.warn("Push rejected with {}: {}", status, response.body());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Push delivery failed: {}", e.toString());
            return false;
        }
    }
}
