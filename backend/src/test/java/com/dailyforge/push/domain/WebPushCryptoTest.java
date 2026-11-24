package com.dailyforge.push.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Web Push crypto against the RFC's own example, plus a VAPID signature check. */
class WebPushCryptoTest {

    /** RFC 8291, section 5 ("Push Message Encryption Example"). */
    @Test
    void encryptionMatchesTheRfc8291Example() {
        byte[] plaintext = WebPushCrypto.decode("V2hlbiBJIGdyb3cgdXAsIEkgd2FudCB0byBiZSBhIHdhdGVybWVsb24");
        byte[] asPublic = WebPushCrypto.decode(
                "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
        byte[] asPrivate = WebPushCrypto.decode("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw");
        byte[] uaPublic = WebPushCrypto.decode(
                "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] auth = WebPushCrypto.decode("BTBZMqHH6r4Tts7J_aSIgg");
        byte[] salt = WebPushCrypto.decode("DGv6ra1nlYgDCS1FRnbzlw");

        KeyPair sender = new KeyPair(WebPushCrypto.publicKey(asPublic), WebPushCrypto.privateKey(asPrivate));
        byte[] body = WebPushCrypto.encrypt(plaintext, uaPublic, auth, salt, sender);

        assertThat(WebPushCrypto.encode(body)).isEqualTo(
                "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
    }

    @Test
    void keysRoundTripThroughTheirRawForms() {
        KeyPair pair = WebPushCrypto.generateKeyPair();
        byte[] pub = WebPushCrypto.uncompressed((java.security.interfaces.ECPublicKey) pair.getPublic());
        byte[] priv = WebPushCrypto.scalar((java.security.interfaces.ECPrivateKey) pair.getPrivate());

        assertThat(pub).hasSize(65);
        assertThat(priv).hasSize(32);
        assertThat(WebPushCrypto.uncompressed(WebPushCrypto.publicKey(pub))).isEqualTo(pub);
    }

    @Test
    void theVapidJwtIsSignedByTheServerKey() throws Exception {
        KeyPair pair = WebPushCrypto.generateKeyPair();
        byte[] pub = WebPushCrypto.uncompressed((java.security.interfaces.ECPublicKey) pair.getPublic());
        byte[] priv = WebPushCrypto.scalar((java.security.interfaces.ECPrivateKey) pair.getPrivate());

        String header = WebPushCrypto.vapidAuthorization(
                "https://fcm.googleapis.com", "mailto:test@example.com", pub, priv, Instant.now().getEpochSecond());

        assertThat(header).startsWith("vapid t=").contains(", k=" + WebPushCrypto.encode(pub));
        String jwt = header.substring("vapid t=".length(), header.indexOf(','));
        String[] parts = jwt.split("\\.");
        String claims = new String(WebPushCrypto.decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"https://fcm.googleapis.com\"").contains("\"sub\":\"mailto:test@example.com\"");

        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(pair.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(WebPushCrypto.decode(parts[2]))).isTrue();
        assertThat(Arrays.copyOf(WebPushCrypto.decode(parts[2]), 1)).isNotNull();
    }
}
