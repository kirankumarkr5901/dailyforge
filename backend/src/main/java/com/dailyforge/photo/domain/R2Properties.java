package com.dailyforge.photo.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloudflare R2 (S3-compatible storage) for progress photos. Any blank value switches
 * photos off: the Body page then says photos aren't set up, instead of failing.
 * See docs/progress-photos.md for the bucket, API token and CORS setup.
 */
@ConfigurationProperties(prefix = "dailyforge.r2")
public record R2Properties(String accountId, String accessKeyId, String secretAccessKey, String bucket) {

    public R2Properties {
        accountId = trim(accountId);
        accessKeyId = trim(accessKeyId);
        secretAccessKey = trim(secretAccessKey);
        bucket = trim(bucket);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    public boolean enabled() {
        return !accountId.isEmpty() && !accessKeyId.isEmpty() && !secretAccessKey.isEmpty() && !bucket.isEmpty();
    }

    public String host() {
        return accountId + ".r2.cloudflarestorage.com";
    }
}
