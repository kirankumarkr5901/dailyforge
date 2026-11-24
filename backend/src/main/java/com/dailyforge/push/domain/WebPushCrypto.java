package com.dailyforge.push.domain;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The two pieces of cryptography Web Push needs, on the JDK alone (no extra libraries):
 *
 * <ul>
 *   <li><b>Message encryption</b>, RFC 8291 ("aes128gcm"): the payload is encrypted for
 *       the browser's subscription keys, so the push service relaying it cannot read it.
 *   <li><b>VAPID</b>, RFC 8292: a short-lived ES256 JWT that tells the push service which
 *       server is sending, signed with the server's key pair.
 * </ul>
 *
 * Keys travel as unpadded base64url, the way browsers and VAPID key generators write them.
 */
public final class WebPushCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RECORD_SIZE = 4096;

    private WebPushCrypto() {}

    // ------------------------------------------------------------------ keys

    public static ECParameterSpec p256() {
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            return params.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 is not available", e);
        }
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            return gen.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot generate a P-256 key pair", e);
        }
    }

    /** A public key from its 65-byte uncompressed form (0x04 || X || Y). */
    public static ECPublicKey publicKey(byte[] uncompressed) {
        if (uncompressed.length != 65 || uncompressed[0] != 0x04) {
            throw new IllegalArgumentException("Expected a 65-byte uncompressed P-256 point");
        }
        try {
            ECPoint point = new ECPoint(
                    new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
                    new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65)));
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, p256()));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Not a valid P-256 public key", e);
        }
    }

    /** A private key from its 32-byte scalar. */
    public static ECPrivateKey privateKey(byte[] scalar) {
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), p256()));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Not a valid P-256 private key", e);
        }
    }

    public static byte[] uncompressed(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 0x04;
        copyUnsigned(key.getW().getAffineX(), out, 1);
        copyUnsigned(key.getW().getAffineY(), out, 33);
        return out;
    }

    public static byte[] scalar(ECPrivateKey key) {
        byte[] out = new byte[32];
        copyUnsigned(key.getS(), out, 0);
        return out;
    }

    private static void copyUnsigned(BigInteger value, byte[] dest, int offset) {
        byte[] raw = value.toByteArray();
        int start = raw.length > 32 ? raw.length - 32 : 0;
        int len = raw.length - start;
        System.arraycopy(raw, start, dest, offset + 32 - len, len);
    }

    public static byte[] decode(String base64url) {
        return Base64.getUrlDecoder().decode(base64url.trim().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    public static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ------------------------------------------------------------------ RFC 8291

    /** Encrypts {@code payload} for a subscription, with a fresh salt and sender key pair. */
    public static byte[] encrypt(byte[] payload, byte[] uaPublic, byte[] authSecret) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(payload, uaPublic, authSecret, salt, generateKeyPair());
    }

    /** The deterministic core, separate so the RFC 8291 test vector can drive it. */
    static byte[] encrypt(byte[] payload, byte[] uaPublic, byte[] authSecret, byte[] salt, KeyPair sender) {
        try {
            byte[] asPublic = uncompressed((ECPublicKey) sender.getPublic());

            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(sender.getPrivate());
            agreement.doPhase(publicKey(uaPublic), true);
            byte[] ecdhSecret = agreement.generateSecret();

            byte[] keyInfo = concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[] {0}, uaPublic, asPublic);
            byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);
            byte[] cek = hkdf(salt, ikm, concat("Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII), new byte[] {0}), 16);
            byte[] nonce = hkdf(salt, ikm, concat("Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII), new byte[] {0}), 12);

            // A single record: the payload followed by the last-record delimiter 0x02.
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(concat(payload, new byte[] {2}));

            ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
            header.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
            return concat(header.array(), ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Web Push encryption failed", e);
        }
    }

    /** HKDF-SHA-256 extract-then-expand, for outputs of at most 32 bytes. */
    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] okm = mac.doFinal(concat(info, new byte[] {1}));
        return Arrays.copyOf(okm, length);
    }

    // ------------------------------------------------------------------ RFC 8292

    /**
     * The Authorization header value for one push request: a VAPID JWT for the push
     * service's origin, valid for 12 hours, and the server's public key.
     */
    public static String vapidAuthorization(String audienceOrigin, String subject, byte[] publicKey, byte[] privateKey, long nowEpochSeconds) {
        try {
            String header = encode("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
            String claims = encode(("{\"aud\":\"" + audienceOrigin + "\",\"exp\":" + (nowEpochSeconds + 12 * 3600)
                            + ",\"sub\":\"" + subject + "\"}")
                    .getBytes(StandardCharsets.UTF_8));
            String signingInput = header + "." + claims;
            // P1363 gives the raw 64-byte R||S that JWS wants, instead of DER.
            Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
            signer.initSign(privateKey(privateKey));
            signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            String jwt = signingInput + "." + encode(signer.sign());
            return "vapid t=" + jwt + ", k=" + encode(publicKey);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("VAPID signing failed", e);
        }
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }
}
