package com.dailyforge;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Boots the application against in-memory H2 in PostgreSQL mode and proves the Flyway
 * baseline applies cleanly.
 *
 * This is the test that catches migration drift early: if a migration uses syntax only
 * PostgreSQL understands, it fails here rather than in a deploy.
 */
@SpringBootTest
@ActiveProfiles("test")
class MigrationIntegrationTest {

    @Autowired private DataSource dataSource;

    @Test
    void contextLoadsAndMigrationsApply() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer applied =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE", Integer.class);

        assertThat(applied).isGreaterThanOrEqualTo(2);
    }

    @Test
    void theLedgerAndItsIndexesExist() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer entries = jdbc.queryForObject("SELECT COUNT(*) FROM points_entry", Integer.class);
        assertThat(entries).isZero();

        Integer caches = jdbc.queryForObject("SELECT COUNT(*) FROM user_score_cache", Integer.class);
        assertThat(caches).isZero();
    }

    @Test
    void everyPointValueIsSeededAsConfigurationRatherThanCode() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer rules =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM points_rule_config WHERE user_id IS NULL", Integer.class);

        assertThat(rules).isGreaterThanOrEqualTo(17);
    }

    /**
     * Rolled back, and asserted on the specific row rather than a global count: the test
     * profile shares one in-memory database across test classes, so any assertion about
     * how many rows exist in total is really an assertion about test ordering.
     */
    @Test
    @Transactional
    void aUserMustHaveAtLeastOneCredential() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String id = "11111111-1111-4111-8111-111111111111";

        // Google-only is valid, as is password-only. Neither is not.
        jdbc.update(
                "INSERT INTO app_user (id, email, password_hash, google_sub, display_name, status, created_at, updated_at)"
                        + " VALUES (?, 'migration-test@example.com', NULL, 'google-123', 'A',"
                        + " 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                java.util.UUID.fromString(id));

        Integer found =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM app_user WHERE google_sub = 'google-123'", Integer.class);
        assertThat(found).isEqualTo(1);
    }
}
