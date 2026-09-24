package com.dailyforge.job.domain;

/**
 * Which kind of interview round an application is sitting in (owner feedback: an
 * INTERVIEW status alone did not say what was actually happening — "Technical round 1
 * to 3 and HR round 1 to 2").
 *
 * The per-stage ceiling lives here rather than in the service, so the rule and the
 * vocabulary it constrains cannot drift apart.
 */
public enum InterviewStage {
    TECHNICAL(3),
    HR(2);

    private final int maxRound;

    InterviewStage(int maxRound) {
        this.maxRound = maxRound;
    }

    public int maxRound() {
        return maxRound;
    }

    /** "Technical round 2" — the label the rejection reason is also built from. */
    public String label(Integer round) {
        String name = this == TECHNICAL ? "Technical" : "HR";
        return round != null && round > 0 ? name + " round " + round : name;
    }
}
