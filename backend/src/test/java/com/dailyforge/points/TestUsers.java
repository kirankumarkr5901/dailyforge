package com.dailyforge.points;

import com.dailyforge.identity.domain.User;
import com.dailyforge.identity.domain.UserSettings;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code points_entry.user_id} has a real foreign key to {@code app_user} — a UUID with
 * no backing row is rejected, correctly, by the database. The engine's own tests still
 * want a plain id to award against, so this creates a minimal real user rather than
 * loosening the schema for the sake of a test.
 */
final class TestUsers {

    private static final AtomicInteger counter = new AtomicInteger();

    private TestUsers() {}

    static UUID create(UserRepository users, UserSettingsRepository settings) {
        User user =
                User.withPassword(
                        "points-test-" + counter.incrementAndGet() + "@example.com",
                        "Points Test",
                        "not-a-real-hash");
        // Flushed rather than merely saved: a test that mixes this fixture with a raw
        // JdbcTemplate statement in the same transaction (PointsRuleConfigServiceTest
        // does) needs the row to actually exist in the database, not just be pending in
        // Hibernate's session, before that statement's own foreign key is checked.
        users.saveAndFlush(user);
        settings.saveAndFlush(UserSettings.forUser(user.getId(), "UTC"));
        return user.getId();
    }
}
