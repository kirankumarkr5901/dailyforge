package com.dailyforge.photo.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.error.ErrorCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Progress photos in Cloudflare R2. The server never handles the image bytes: the phone
 * shrinks the photo, asks here for a short-lived upload link scoped to one new object
 * under {@code users/{userId}/}, uploads straight to R2, then records it. Viewing hands
 * out one-hour GET links; nothing in the bucket is public.
 */
@Service
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);

    /** The phone shrinks photos to well under this before uploading. */
    public static final long MAX_BYTES = 3 * 1024 * 1024;
    private static final int UPLOAD_SECONDS = 600;
    private static final int VIEW_SECONDS = 3600;

    private final JdbcTemplate jdbc;
    private final R2Properties r2;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public PhotoService(JdbcTemplate jdbc, R2Properties r2, Clock clock) {
        this.jdbc = jdbc;
        this.r2 = r2;
        this.clock = clock;
    }

    public record Photo(UUID id, LocalDate takenOn, String url) {}

    public record Upload(String key, String uploadUrl) {}

    public boolean enabled() {
        return r2.enabled();
    }

    /** A link to PUT one new JPEG of exactly {@code sizeBytes} under this user's folder. */
    public Upload uploadUrl(UUID userId, long sizeBytes) {
        requireEnabled();
        if (sizeBytes < 1 || sizeBytes > MAX_BYTES) {
            throw ApiException.outOfRange("sizeBytes", "Photos must be under 3 MB.");
        }
        String key = "users/" + userId + "/" + UUID.randomUUID() + ".jpg";
        String url = sign("PUT", key, UPLOAD_SECONDS, Map.of("content-length", String.valueOf(sizeBytes)));
        return new Upload(key, url);
    }

    /** Records an uploaded photo. The key must be one of this user's own. */
    @Transactional
    public Photo record(UUID userId, String key, LocalDate takenOn) {
        requireEnabled();
        if (key == null || !key.matches("users/" + userId + "/[0-9a-f-]{36}\\.jpg")) {
            throw ApiException.outOfRange("key", "That upload does not belong to this account.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO progress_photo (id, user_id, taken_on, object_key, created_at) VALUES (?, ?, ?, ?, ?)",
                id, userId, Date.valueOf(takenOn), key, Timestamp.from(Instant.now()));
        return new Photo(id, takenOn, sign("GET", key, VIEW_SECONDS, Map.of()));
    }

    /** Newest first, each with a one-hour view link. */
    @Transactional(readOnly = true)
    public List<Photo> list(UUID userId) {
        if (!enabled()) {
            return List.of();
        }
        return jdbc.query(
                "SELECT id, taken_on, object_key FROM progress_photo WHERE user_id = ? ORDER BY taken_on DESC, created_at DESC",
                (rs, i) -> new Photo(
                        rs.getObject(1, UUID.class), rs.getDate(2).toLocalDate(), sign("GET", rs.getString(3), VIEW_SECONDS, Map.of())),
                userId);
    }

    /** Removes the record, and the image from R2 (a failed R2 delete only leaves an orphan, logged). */
    @Transactional
    public void delete(UUID userId, UUID id) {
        requireEnabled();
        List<String> keys = jdbc.queryForList(
                "SELECT object_key FROM progress_photo WHERE id = ? AND user_id = ?", String.class, id, userId);
        if (keys.isEmpty()) {
            throw ApiException.notFound("That photo");
        }
        jdbc.update("DELETE FROM progress_photo WHERE id = ?", id);
        try {
            HttpResponse<Void> res = http.send(
                    HttpRequest.newBuilder(URI.create(sign("DELETE", keys.get(0), 60, Map.of())))
                            .timeout(Duration.ofSeconds(15))
                            .DELETE()
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() >= 300) {
                log.warn("R2 delete of {} answered {}", keys.get(0), res.statusCode());
            }
        } catch (Exception e) {
            log.warn("R2 delete of {} failed: {}", keys.get(0), e.toString());
        }
    }

    private String sign(String method, String key, int seconds, Map<String, String> headers) {
        return S3Presigner.presign(
                method, r2.host(), "/" + r2.bucket() + "/" + key, "auto", r2.accessKeyId(), r2.secretAccessKey(),
                ZonedDateTime.now(clock), seconds, headers);
    }

    private void requireEnabled() {
        if (!enabled()) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Progress photos aren't set up on the server yet.");
        }
    }
}
