# ADR 0002 — PWA instead of a native package, and how multi-device sync works

**Date:** 2026-09-08
**Status:** Accepted

## Context

The app is going live. The owner uses it from a desktop and an Android phone, asked to
"download as an app in android", and — after weighing the options — clarified that "app
for android meaning not actual app, PWA also works".

Two requirements came with it, and they turned out to be the same requirement seen from
two angles:

> It should be able to change incrementally.

> It should be stale data free and should be synced. If desktop has old data and mobile
> has old data, after few days if I login in desktop then desktop should not override
> the data with old, first it should sync with latest data.

Hosting was chosen as the free tiers of Neon, Render and Netlify, with Google sign-in
switched on.

## Decisions

1. **Ship as an installable PWA; do not package with Capacitor.** Spec milestone M9 and
   `DEPLOYMENT.md` phase 3 both assumed a Capacitor wrap and a Play Store track. That is
   now explicitly not the plan. The deciding factor is the owner's *other* requirement:
   a packaged app makes every change a rebuild, a re-sign and a reinstall (or a store
   review), whereas a PWA that is already installed picks up a new build on next launch.
   The install itself is not meaningfully worse — a home-screen icon, no browser chrome,
   its own task-switcher entry.

   Reversible: nothing in the frontend depends on this. The API base URL is already
   injected at build time, tokens already sit behind one `TokenStorage` interface, and
   safe-area insets are already respected — the three things M1 was told to keep clean
   precisely so a wrap stays cheap. If a store presence is ever wanted, Capacitor is
   still a packaging step.

2. **Optimistic concurrency, not last-write-wins, on everything a user can edit.**
   Every such row carries a `version`; the client echoes it as `If-Match`; the server
   refuses the write with `409 STALE_WRITE` when the row has moved on. Eight tables are
   covered (habit, user_settings, job_application, reward, run, exercise, workout_set,
   workout_plan).

   **JPA's `@Version` alone does not solve the stated problem**, and it is worth being
   explicit about why, because it looks like it should. `@Version` protects two
   transactions racing in the same instant. An ordinary update loads the row fresh,
   mutates it and saves — so a client whose copy is four days old still writes cleanly
   over everything that happened since, and `@Version` never fires. The explicit
   comparison against the version the client actually read is the real guarantee;
   `@Version` is kept as the second line of defence and mapped to the same 409.

   `goal` is deliberately excluded: `GoalService.list()` recomputes and saves progress on
   every read, so its version would advance without anyone editing anything and every
   legitimate edit would conflict.

3. **A refused write is a re-read, never a retry.** The interceptor that sees a 409
   explains it and refreshes what is on screen. Replaying the write would be exactly the
   overwrite the mechanism exists to prevent, and silently merging is worse — the server
   cannot know which of two human intentions was meant to win.

4. **Screens re-read when the device may be behind.** `SyncStore` (the third of the
   three shared stores the spec always named but which had never been built) signals a
   refresh when the tab becomes visible after more than 30 seconds away, the network
   returns, a session is restored or signed in, or a write was refused as stale. This is
   what makes "if I login in desktop after a few days, sync first" true in practice —
   the stale display is fixed before the user can act on it, and the conflict check
   catches whatever slips through.

5. **The service worker must not be able to strand a device on an old build.** It caches
   the app shell, which is how an installed PWA ends up running a month-old bundle
   against a current API. `AppUpdateService` checks on start and hourly and offers a
   Reload; `netlify.toml` serves `index.html`, `ngsw.json` and `ngsw-worker.js` with
   no-store so the check can see the truth. An unrecoverable cache reloads itself.

6. **Accept the free tier's cold start, and say so in the UI.** Render's free instances
   sleep after ~15 minutes and take 30–60 seconds to wake. Rather than a skeleton that
   is indistinguishable from a hang, a request still running after 4 seconds explains
   itself once. A windowed GitHub Actions cron keeps the instance warm during waking
   hours; it is a window rather than round-the-clock because the free tier's 750
   instance-hours a month do not cover 24/7 across more than one service.

## Consequences

- The daily rollover job does not run while the instance sleeps. It was already required
  to be idempotent and to catch up missed days (spec §5.6), so an overnight sleep makes
  it late, never wrong. This is the single largest correctness cost of the free tier and
  it is a known, bounded one.
- Every future editable entity should get a `version` column and an `If-Match` check at
  the same time as its edit endpoint, not later. `StaleWrite.check` exists so that is one
  line.
- `DEPLOYMENT.md` phase 3 (Android via Capacitor) is superseded by this ADR.
- Offline write queueing (spec §4.6) is still **not** built. Reads and writes both
  require the network. That remains open, and it interacts with this ADR: a replayed
  offline write is a stale write by definition, so the queue will have to carry the
  version it was made against and surface conflicts rather than resolving them silently.
