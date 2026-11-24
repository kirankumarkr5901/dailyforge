package com.dailyforge.workout.repo;

import com.dailyforge.workout.domain.Exercise;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {

    Optional<Exercise> findByIdAndArchivedAtIsNull(UUID id);

    /** Catalog (owner null) plus this user's own, matching the search text (spec: "catalog + user's own"). */
    /**
     * Two queries, not one with "(:q is null or ...)": Postgres cannot infer the type of
     * a parameter that is only ever compared with null, and rejected the whole request
     * ("could not determine data type of parameter") whenever the search box was empty.
     * H2 accepts it, which is why no test caught it.
     */
    @Query(
            "select e from Exercise e where e.archivedAt is null "
                    + "and (e.ownerUserId is null or e.ownerUserId = :userId) "
                    + "order by e.name asc")
    List<Exercise> findAllVisible(@Param("userId") UUID userId);

    @Query(
            "select e from Exercise e where e.archivedAt is null "
                    + "and (e.ownerUserId is null or e.ownerUserId = :userId) "
                    + "and e.searchName like concat('%', :q, '%') "
                    + "order by e.name asc")
    List<Exercise> search(@Param("userId") UUID userId, @Param("q") String normalisedQuery);
}
