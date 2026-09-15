package com.dailyforge.insight.domain;

import com.dailyforge.insight.repo.QuoteRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One deterministic quote per user per day (spec §8.1): {@code hash(userId, date) %
 * poolSize}, so refreshing the page never changes today's quote, and no third-party
 * service is ever called for it.
 */
@Service
public class QuoteService {

    private final QuoteRepository quotes;

    public QuoteService(QuoteRepository quotes) {
        this.quotes = quotes;
    }

    @Transactional(readOnly = true)
    public Quote forDate(UUID userId, LocalDate date) {
        List<Quote> pool = quotes.findAllByActiveTrueOrderById();
        if (pool.isEmpty()) {
            return null;
        }
        int index = Math.floorMod(userId.hashCode() * 31 + date.hashCode(), pool.size());
        return pool.get(index);
    }
}
