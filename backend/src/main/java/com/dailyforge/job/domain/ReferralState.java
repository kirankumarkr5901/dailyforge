package com.dailyforge.job.domain;

/**
 * How long a referral request has been waiting, expressed as what to do about it.
 *
 * <p>Named for the action rather than the colour it is drawn in. The owner described
 * these as green, orange and red, but a colour is a rendering decision — the frontend
 * maps these three to the theme's own tokens, and the API stays meaningful to anything
 * that is not a screen.
 *
 * <p>The thresholds live in {@link ReferralProperties}, not here, because "how long is
 * too long to wait" is a judgement that should be tunable without a code change.
 */
public enum ReferralState {

    /** Recently asked. Your referrer is probably still getting to it; let them. */
    WAITING,

    /** Long enough that a polite nudge is reasonable, and still worth waiting for. */
    FOLLOW_UP,

    /** Long enough that the referral should stop blocking you. Apply directly. */
    APPLY_DIRECTLY
}
