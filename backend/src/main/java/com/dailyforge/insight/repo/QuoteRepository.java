package com.dailyforge.insight.repo;

import com.dailyforge.insight.domain.Quote;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteRepository extends JpaRepository<Quote, UUID> {

    /** Ordered by id so the pool has a stable index for the deterministic pick (spec §8.1). */
    List<Quote> findAllByActiveTrueOrderById();
}
