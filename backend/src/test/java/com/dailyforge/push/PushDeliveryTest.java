package com.dailyforge.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyforge.challenge.domain.ChallengeService;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.push.domain.DailyReminderService;
import com.dailyforge.push.domain.PushSender;
import com.dailyforge.push.domain.WebPushCrypto;
import com.dailyforge.testsupport.TestUsers;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Real deliveries to a fake push service on localhost: the request carries an encrypted
 * aes128gcm body and a VAPID Authorization header, and a push service answering 410 Gone
 * removes the subscription. Also the reminder text and its once-a-day guard.
 */
@SpringBootTest
@ActiveProfiles("test")
class PushDeliveryTest {

    private static final KeyPair VAPID = WebPushCrypto.generateKeyPair();
    private static HttpServer server;
    private static final List<Map<String, String>> received = new CopyOnWriteArrayList<>();
    private static final AtomicInteger nextStatus = new AtomicInteger(201);

    @DynamicPropertySource
    static void vapidKeys(DynamicPropertyRegistry registry) {
        registry.add("dailyforge.push.vapid-public-key",
                () -> WebPushCrypto.encode(WebPushCrypto.uncompressed((ECPublicKey) VAPID.getPublic())));
        registry.add("dailyforge.push.vapid-private-key",
                () -> WebPushCrypto.encode(WebPushCrypto.scalar((ECPrivateKey) VAPID.getPrivate())));
    }

    @BeforeAll
    static void startFakePushService() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/push", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            received.add(Map.of(
                    "authorization", String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")),
                    "encoding", String.valueOf(exchange.getRequestHeaders().getFirst("Content-Encoding")),
                    "ttl", String.valueOf(exchange.getRequestHeaders().getFirst("TTL")),
                    "bodyLength", String.valueOf(body.length)));
            exchange.sendResponseHeaders(nextStatus.get(), -1);
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    @Autowired private PushSender sender;
    @Autowired private DailyReminderService reminders;
    @Autowired private ChallengeService challenges;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;

    private PushSender.Subscription subscription(String id) {
        KeyPair browser = WebPushCrypto.generateKeyPair();
        byte[] auth = new byte[16];
        new java.security.SecureRandom().nextBytes(auth);
        return new PushSender.Subscription(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/push/" + id,
                WebPushCrypto.encode(WebPushCrypto.uncompressed((ECPublicKey) browser.getPublic())),
                WebPushCrypto.encode(auth));
    }

    @Test
    void aNotificationIsEncryptedAndSignedForEachDevice() {
        received.clear();
        nextStatus.set(201);
        UUID user = TestUsers.create(users, settings);
        sender.subscribe(user, subscription("phone-" + user));

        int delivered = sender.send(user, new PushSender.Message("Rest is over", "Next set.", "tag", "/workout"), 120);

        assertThat(delivered).isEqualTo(1);
        assertThat(received).hasSize(1);
        Map<String, String> r = received.get(0);
        assertThat(r.get("encoding")).isEqualTo("aes128gcm");
        assertThat(r.get("ttl")).isEqualTo("120");
        assertThat(r.get("authorization")).startsWith("vapid t=").contains(", k=" + sender.publicKey());
        // salt(16) + record size(4) + key length(1) + key(65) + at least the GCM tag(16).
        assertThat(Integer.parseInt(r.get("bodyLength"))).isGreaterThan(16 + 4 + 1 + 65 + 16);
    }

    @Test
    void aSubscriptionThePushServiceSaysIsGoneIsRemoved() {
        nextStatus.set(410);
        UUID user = TestUsers.create(users, settings);
        sender.subscribe(user, subscription("gone-" + user));

        assertThat(sender.send(user, new PushSender.Message("x", "y", "t", "/"), 60)).isZero();
        assertThat(sender.subscriptions(user)).isEmpty();
        nextStatus.set(201);
    }

    @Test
    void theReminderListsWhatIsStillOpenAndGoesOutOncePerDay() {
        nextStatus.set(201);
        UUID user = TestUsers.create(users, settings);
        sender.subscribe(user, subscription("rem-" + user));
        challenges.create(user, "Plank", "trophy", 10, 30, 100);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        assertThat(reminders.openItems(user, today)).isEqualTo("Still to do: Plank (day 1)");
        assertThat(reminders.sendIfOpen(user, today)).isTrue();
        assertThat(reminders.sendIfOpen(user, today)).isFalse();
    }

    @Test
    void nothingOpenMeansNoReminder() {
        UUID user = TestUsers.create(users, settings);

        assertThat(reminders.openItems(user, LocalDate.now(ZoneOffset.UTC))).isNull();
    }
}
