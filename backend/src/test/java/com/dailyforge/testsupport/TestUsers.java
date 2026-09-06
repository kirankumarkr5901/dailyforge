package com.dailyforge.testsupport;

import com.dailyforge.identity.domain.User;
import com.dailyforge.identity.domain.UserSettings;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared across every module's tests: {@code points_entry.user_id} (and now
 * {@code habit.user_id}) has a real foreign key to {@code app_user} — a UUID with no
 * backing row is rejected, correctly, by the database. Tests that only need a plain id
 * to act against still want a minimal real user rather than a loosened schema.
 */
public final class TestUsers {

    private static final AtomicInteger counter = new AtomicInteger();

    private TestUsers() {}

    public static UUID create(UserRepository users, UserSettingsRepository settings) {
        User user =
                User.withPassword(
                        "test-user-" + counter.incrementAndGet() + "@example.com",
                        "Test User",
                        "not-a-real-hash");
        // Flushed rather than merely saved: a test that mixes this fixture with a raw
        // JdbcTemplate statement in the same transaction needs the row to actually exist
        // in the database, not just be pending in Hibernate's session, before that
        // statement's own foreign key is checked.
        users.saveAndFlush(user);
        settings.saveAndFlush(UserSettings.forUser(user.getId(), "UTC"));
        return user.getId();
    }
}
