-- Three states on the workout board instead of two (owner request).
--
-- The board already distinguished "nothing logged" from "something logged", and sank
-- the second to the bottom. That conflates two different things: an exercise you are
-- halfway through and one you are finished with look identical once the first set
-- lands, and the card you most need to see — the one you are working on right now —
-- sank to the bottom the moment you started it.
--
-- A row here says "I am done with this exercise for this session". Nothing else can
-- say it: sets logged tells you work happened, not that it finished, and there is no
-- set count that means "finished" for every exercise on every day.
--
-- Scoped to the session rather than to (user, date, exercise), because the session
-- already carries the user, the date, the plan and the day — and the same exercise on
-- two different plan days is two different pieces of work.
CREATE TABLE workout_exercise_completion (
    id           UUID NOT NULL,
    session_id   UUID NOT NULL,
    exercise_id  UUID NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_workout_exercise_completion PRIMARY KEY (id),
    CONSTRAINT fk_workout_exercise_completion_session
        FOREIGN KEY (session_id) REFERENCES workout_session (id) ON DELETE CASCADE,
    CONSTRAINT fk_workout_exercise_completion_exercise
        FOREIGN KEY (exercise_id) REFERENCES exercise (id) ON DELETE CASCADE,
    -- Marking a finished exercise finished again is a no-op, not a second row.
    CONSTRAINT uq_workout_exercise_completion UNIQUE (session_id, exercise_id)
);

CREATE INDEX ix_workout_exercise_completion_session ON workout_exercise_completion (session_id);
