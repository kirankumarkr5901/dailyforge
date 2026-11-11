package com.dailyforge.workout.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.error.ErrorCode;
import com.dailyforge.workout.repo.WorkoutExerciseCompletionRepository;
import com.dailyforge.workout.repo.WorkoutSessionRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marking an exercise finished for a session, and taking it back.
 *
 * <p>Carries no points of its own, deliberately. Points are for work done, and the work
 * was already paid for when the sets were logged — paying again for saying "that's me
 * done" would be paying twice for one effort, and would make the button worth pressing
 * whether or not it was true.
 */
@Service
public class WorkoutCompletionService {

    private final WorkoutExerciseCompletionRepository completions;
    private final WorkoutSessionRepository sessions;

    public WorkoutCompletionService(
            WorkoutExerciseCompletionRepository completions, WorkoutSessionRepository sessions) {
        this.completions = completions;
        this.sessions = sessions;
    }

    /**
     * Marks one exercise finished. Idempotent: pressing it twice is the same as pressing
     * it once, which matters because the button and the network can both stutter.
     */
    @Transactional
    public void markComplete(UUID sessionId, UUID exerciseId, UUID userId) {
        requireOwnedSession(sessionId, userId);
        if (completions.findBySessionIdAndExerciseId(sessionId, exerciseId).isPresent()) {
            return;
        }
        try {
            completions.saveAndFlush(WorkoutExerciseCompletion.create(sessionId, exerciseId, Instant.now()));
        } catch (DataIntegrityViolationException raced) {
            // Two taps arrived together; the loser here is the winner of an idempotent
            // retry, exactly as in the points engine's own award path.
        }
    }

    /** Takes the mark back — you were not as done as you thought. */
    @Transactional
    public void clearComplete(UUID sessionId, UUID exerciseId, UUID userId) {
        requireOwnedSession(sessionId, userId);
        completions.deleteBySessionIdAndExerciseId(sessionId, exerciseId);
    }

    private WorkoutSession requireOwnedSession(UUID sessionId, UUID userId) {
        return sessions
                .findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "That session does not exist."));
    }
}
