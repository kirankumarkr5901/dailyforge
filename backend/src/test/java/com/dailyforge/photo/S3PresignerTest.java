package com.dailyforge.photo;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyforge.photo.domain.S3Presigner;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** SigV4 presigning against AWS's own published example. */
class S3PresignerTest {

    /**
     * "Example: GET object" from the Amazon S3 SigV4 query-string authentication docs:
     * examplebucket/test.txt, 2013-05-24, 86400 s, the documented example credentials.
     */
    @Test
    void matchesTheAwsPresignedUrlExample() {
        String url = S3Presigner.presign(
                "GET",
                "examplebucket.s3.amazonaws.com",
                "/test.txt",
                "us-east-1",
                "AKIAIOSFODNN7EXAMPLE",
                "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                ZonedDateTime.of(2013, 5, 24, 0, 0, 0, 0, ZoneOffset.UTC),
                86400,
                Map.of());

        assertThat(url).isEqualTo("https://examplebucket.s3.amazonaws.com/test.txt"
                + "?X-Amz-Algorithm=AWS4-HMAC-SHA256"
                + "&X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20130524%2Fus-east-1%2Fs3%2Faws4_request"
                + "&X-Amz-Date=20130524T000000Z"
                + "&X-Amz-Expires=86400"
                + "&X-Amz-SignedHeaders=host"
                + "&X-Amz-Signature=aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404");
    }

    @Test
    void aPutCanSignItsContentLength() {
        String url = S3Presigner.presign(
                "PUT", "acc.r2.cloudflarestorage.com", "/bucket/users/u1/a.jpg", "auto", "KEY", "SECRET",
                ZonedDateTime.now(ZoneOffset.UTC), 600, Map.of("content-length", "12345"));

        assertThat(url).startsWith("https://acc.r2.cloudflarestorage.com/bucket/users/u1/a.jpg?")
                .contains("X-Amz-SignedHeaders=content-length%3Bhost")
                .contains("X-Amz-Signature=");
    }
}
