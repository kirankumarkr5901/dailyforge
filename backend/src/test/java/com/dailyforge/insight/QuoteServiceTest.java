package com.dailyforge.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.insight.domain.Quote;
import com.dailyforge.insight.domain.QuoteService;
import com.dailyforge.testsupport.TestUsers;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** One deterministic quote per user per day (spec §8.1) — never random, never a network call. */
@SpringBootTest
@ActiveProfiles("test")
class QuoteServiceTest {

    @Autowired private QuoteService quotes;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;

    @Test
    void theSameUserAndDateAlwaysProducesTheSameQuote() {
        UUID user = TestUsers.create(users, settings);
        LocalDate date = LocalDate.of(2026, 3, 12);

        Quote first = quotes.forDate(user, date);
        Quote second = quotes.forDate(user, date);

        assertThat(first).isNotNull();
        assertThat(first.getId()).isEqualTo(second.getId());
    }

    @Test
    void differentDaysCanProduceDifferentQuotes() {
        UUID user = TestUsers.create(users, settings);

        long distinctQuotes =
                java.util.stream.IntStream.range(0, 30)
                        .mapToObj(i -> quotes.forDate(user, LocalDate.of(2026, 1, 1).plusDays(i)))
                        .map(Quote::getId)
                        .distinct()
                        .count();

        // Not a strict requirement of the algorithm, but with 30 days against a pool of
        // 30 seeded quotes, always landing on the same one would mean the hash is broken.
        assertThat(distinctQuotes).isGreaterThan(1);
    }

}
