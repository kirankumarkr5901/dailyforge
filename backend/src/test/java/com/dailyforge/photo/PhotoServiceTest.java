package com.dailyforge.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.photo.domain.PhotoService;
import com.dailyforge.testsupport.TestUsers;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Photo upload links are scoped to the user, sized, and recorded only for their own objects. */
@SpringBootTest
@ActiveProfiles("test")
class PhotoServiceTest {

    @DynamicPropertySource
    static void r2(DynamicPropertyRegistry registry) {
        registry.add("dailyforge.r2.account-id", () -> "acc123");
        registry.add("dailyforge.r2.access-key-id", () -> "KEYID");
        registry.add("dailyforge.r2.secret-access-key", () -> "SECRET");
        registry.add("dailyforge.r2.bucket", () -> "photos");
    }

    @Autowired private PhotoService photos;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;

    @Test
    void anUploadLinkIsForOneSizedObjectInTheUsersOwnFolder() {
        UUID user = TestUsers.create(users, settings);

        var upload = photos.uploadUrl(user, 200_000);

        assertThat(upload.key()).startsWith("users/" + user + "/").endsWith(".jpg");
        assertThat(upload.uploadUrl())
                .startsWith("https://acc123.r2.cloudflarestorage.com/photos/users/" + user + "/")
                .contains("X-Amz-SignedHeaders=content-length%3Bhost")
                .contains("X-Amz-Expires=600");
        assertThatThrownBy(() -> photos.uploadUrl(user, 10_000_000)).isInstanceOf(ApiException.class);
    }

    @Test
    void onlyTheUsersOwnUploadCanBeRecordedAndListsNewestFirst() {
        UUID user = TestUsers.create(users, settings);
        UUID other = TestUsers.create(users, settings);
        String mine = photos.uploadUrl(user, 1000).key();
        String theirs = photos.uploadUrl(other, 1000).key();

        assertThatThrownBy(() -> photos.record(user, theirs, LocalDate.of(2026, 11, 1))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> photos.record(user, "users/" + user + "/../x.jpg", LocalDate.of(2026, 11, 1)))
                .isInstanceOf(ApiException.class);

        photos.record(user, mine, LocalDate.of(2026, 11, 1));
        photos.record(user, photos.uploadUrl(user, 1000).key(), LocalDate.of(2026, 11, 20));

        var list = photos.list(user);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).takenOn()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(list.get(0).url()).contains("X-Amz-Expires=3600").contains("X-Amz-Signature=");
        assertThat(photos.list(other)).isEmpty();
    }
}
