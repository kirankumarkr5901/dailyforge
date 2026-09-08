package com.dailyforge.common.error;

/**
 * Stable error codes (spec §4.7).
 *
 * The frontend maps a code to copy, so these names are part of the API contract: rename
 * one and you break a client. The message on the wire is a fallback for logs and
 * developers, never the source of the user-facing wording.
 */
public enum ErrorCode {

    /** The request needs a signed-in user. The client opens the login sheet and replays. */
    AUTH_REQUIRED,
    AUTH_INVALID_CREDENTIALS,
    AUTH_TOKEN_EXPIRED,
    AUTH_EMAIL_TAKEN,

    /** The caller is signed in but this row is not theirs. */
    FORBIDDEN,
    NOT_FOUND,

    /** Failed bean validation; `field` names the offending property. */
    VALIDATION_FAILED,

    /** Outside the allowed edit window (spec §4.3). */
    HABIT_LOCKED,
    ENTRY_LOCKED,

    /** Failed a sanity limit (spec §5.7). Copy stays neutral, never accusing. */
    OUT_OF_RANGE,
    DAILY_CAP_REACHED,

    /** Same idempotency key, different payload. */
    IDEMPOTENCY_CONFLICT,

    CONFLICT,
    INTERNAL_ERROR
}
