package com.dailyforge.photo.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * AWS Signature Version 4 query-string presigning, on the JDK alone: a URL that lets
 * whoever holds it do one thing (GET, PUT or DELETE one object) until it expires.
 * Cloudflare R2 speaks the same S3 protocol, with region "auto".
 *
 * <p>Payloads are UNSIGNED-PAYLOAD, as in every browser upload; a PUT can additionally
 * sign the {@code content-length} header, so the link only accepts a body of exactly that
 * size. Checked against AWS's own published presigned-URL example.
 */
public final class S3Presigner {

    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private S3Presigner() {}

    /**
     * @param host           e.g. {@code <account>.r2.cloudflarestorage.com}
     * @param path           already-absolute object path, e.g. {@code /bucket/users/1/a.jpg}
     * @param signedHeaders  extra headers to sign besides host (lower-case names), may be empty
     */
    public static String presign(
            String method,
            String host,
            String path,
            String region,
            String accessKeyId,
            String secretAccessKey,
            ZonedDateTime now,
            int expiresSeconds,
            Map<String, String> signedHeaders) {
        try {
            ZonedDateTime utc = now.withZoneSameInstant(ZoneOffset.UTC);
            String amzDate = AMZ_DATE.format(utc);
            String date = DATE.format(utc);
            String scope = date + "/" + region + "/s3/aws4_request";

            TreeMap<String, String> headers = new TreeMap<>();
            headers.put("host", host);
            signedHeaders.forEach((k, v) -> headers.put(k.toLowerCase(), v.trim()));
            String signedHeaderNames = String.join(";", headers.keySet());

            TreeMap<String, String> query = new TreeMap<>();
            query.put("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
            query.put("X-Amz-Credential", accessKeyId + "/" + scope);
            query.put("X-Amz-Date", amzDate);
            query.put("X-Amz-Expires", String.valueOf(expiresSeconds));
            query.put("X-Amz-SignedHeaders", signedHeaderNames);
            StringBuilder canonicalQuery = new StringBuilder();
            query.forEach((k, v) -> {
                if (canonicalQuery.length() > 0) canonicalQuery.append('&');
                canonicalQuery.append(encode(k)).append('=').append(encode(v));
            });

            StringBuilder canonicalHeaders = new StringBuilder();
            headers.forEach((k, v) -> canonicalHeaders.append(k).append(':').append(v).append('\n'));

            String canonicalRequest = method + "\n"
                    + encodePath(path) + "\n"
                    + canonicalQuery + "\n"
                    + canonicalHeaders + "\n"
                    + signedHeaderNames + "\n"
                    + "UNSIGNED-PAYLOAD";

            String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n" + hex(sha256(canonicalRequest));

            byte[] key = hmac(("AWS4" + secretAccessKey).getBytes(StandardCharsets.UTF_8), date);
            key = hmac(key, region);
            key = hmac(key, "s3");
            key = hmac(key, "aws4_request");
            String signature = hex(hmac(key, stringToSign));

            return "https://" + host + encodePath(path) + "?" + canonicalQuery + "&X-Amz-Signature=" + signature;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not sign the storage URL", e);
        }
    }

    /** RFC 3986: unreserved characters stay, everything else is %XX (upper case). */
    static String encode(String value) {
        StringBuilder out = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xff);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~') {
                out.append(c);
            } else {
                out.append('%').append(String.format("%02X", b & 0xff));
            }
        }
        return out.toString();
    }

    /** Each path segment encoded; the slashes between them kept. */
    static String encodePath(String path) {
        StringBuilder out = new StringBuilder();
        for (String segment : path.split("/", -1)) {
            if (out.length() > 0 || path.startsWith("/")) out.append('/');
            out.append(encode(segment));
        }
        String s = out.toString();
        return s.startsWith("//") ? s.substring(1) : s;
    }

    private static byte[] sha256(String s) throws GeneralSecurityException {
        return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(byte[] key, String data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
