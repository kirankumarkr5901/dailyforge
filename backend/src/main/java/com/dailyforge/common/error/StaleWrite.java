package com.dailyforge.common.error;

import org.springframework.http.HttpStatus;

/**
 * The multi-device rule, in one place: a write may only land on the row the client
 * actually read.
 *
 * <p>Note that JPA's own {@code @Version} does <em>not</em> solve this on its own. It
 * protects two transactions racing inside the same instant, but an update loads the row
 * fresh, mutates it and saves — so a client whose copy is four days old still writes
 * cleanly over everything that happened since. The explicit comparison here is what
 * makes the staleness visible, and it is the reason every editable entity exposes its
 * version to the client at all.
 *
 * <p>A null {@code expected} skips the check. That keeps callers that genuinely have no
 * prior read (a server-side job, an internal transition) working without inventing a
 * version for them; every client-facing edit endpoint passes one.
 */
public final class StaleWrite {

    private StaleWrite() {}

    public static void check(Long expected, long actual, String what) {
        if (expected != null && expected != actual) {
            throw new ApiException(
                    ErrorCode.STALE_WRITE,
                    HttpStatus.CONFLICT,
                    what + " changed somewhere else since you loaded it. Reload to see the latest.");
        }
    }
}
