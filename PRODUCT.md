# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Confirmed by the owner in this session, on top of the attached `docs/DAILYFORGE_SPEC.md`:

- **Frontend:** Angular 20, standalone components, signals + RxJS, `@angular/cdk` for behaviour, no Angular Material. Every component lives in its own folder with `.html`, `.ts`, `.scss` as three separate files — no inline templates or inline styles anywhere.
- **Backend:** Java 21, Spring Boot 3.3+, Gradle (Kotlin DSL), modular monolith. Chosen over a Firebase-native design because the append-only points ledger, the reconciliation pass (spec §5.3) and the daily rollover job need one transactional SQL database.
- **Database:** H2 in **file** mode for local development and testing (`./.data/dailyforge`, `MODE=PostgreSQL`), H2 in-memory per test class, PostgreSQL 16 in production. Flyway migrations from commit one; the same plain-SQL migrations run on both engines. `hibernate.ddl-auto=validate` only.
- **Auth:** Google OAuth **and** email/password, both in v1. One user record may carry either or both credentials. Anonymous read-only browsing is preserved.
- **Packaging:** Capacitor 7 Android wrap of the built Angular PWA, deferred to the spec's M9.
- **Deployment target:** free-tier friendly — Angular static build on Netlify or Vercel, Spring Boot container on Render or Fly.io, PostgreSQL on Neon or Supabase. The exact vendor within that set is still open; the deployment plan is written so the choice stays a late, cheap decision.

**Assumption, not yet confirmed:** the owner has a Firebase account and offered it "just in case". Given the free-tier deployment target, Google sign-in is planned as Google Identity Services on the frontend plus server-side ID-token verification in Spring Security, with **no Firebase SDK dependency**. Firebase remains available as a fallback identity provider or hosting option if the owner prefers it later.

## Users

The primary user is the owner: one person training seriously and running, who logs everything by hand and wants the app to be worth opening on days when motivation is absent. They log in three situations, and each has different constraints:

- **Mid-workout, in a gym**, phone in one hand, often with no signal, between sets. Logging must take two taps and must not lose data when the network is gone.
- **End of day, at home**, ticking habits and reviewing what the day earned.
- **Weekly review**, looking at the heatmap, personal records, goal progress and the job pipeline to decide what the next week looks like.

There is no second audience in v1: no coaches, no social feed, no shared accounts. Anonymous visitors can browse every page against a seeded demo dataset, but they are onlookers, not a user segment with their own jobs.

## Product Purpose

DailyForge is a manual-logging fitness and habit tracker where **every logged action converts into points, and points are the single currency across the whole app**. Workouts, runs, habits, one-off good and bad activities, and goals all feed one score, and that score is spendable on rewards the user defines themselves.

It exists because tracking apps fail on the days that matter — the days the user does not feel like training. The product's answer is that logging is fast, feedback is immediate and physical, and the history is visible enough that breaking a streak has a felt cost.

Success is behavioural, not analytic: the owner opens the app every day for months, the score is trusted enough to be spent, and no logged action is ever lost.

## Positioning

Three things a neighbouring tracker could not truthfully copy without rebuilding around them:

1. **One currency, not several dashboards.** Lifting, running, habits and life admin all resolve to a single number. Most trackers keep these in separate silos with separate streaks.
2. **The ledger is the product.** Points are an append-only record, not a display value. Undo writes a reversal, never a deletion, so the activity log tells the truth ("+15 PR bonus", then "−15 reversed") and the score can always be rebuilt from raw history.
3. **Manual by choice.** No GPS, no wearables, no AI logging. The deliberate friction of typing what you did is the mechanism, not a missing feature.

## Operating Context

- **Gym, offline.** Mutations queue in IndexedDB and replay in order on reconnect, keyed by an idempotency key generated per user action. The server is the only authority on score; local deltas show as "pending" until reconciled.
- **A user's day is not UTC.** Every user has an IANA time zone; streaks, heatmap cells, daily bonuses and "today's points" all resolve through the user's local date. One `DayService` owns that conversion.
- **The past gets edited.** Habits are editable for today and yesterday; workouts, runs, activities and body metrics for seven days. Any edit to history can invalidate an already-granted bonus, so every mutation triggers a recompute-and-reconcile pass in the same transaction.
- **Rituals the app must fit:** a workout is logged set by set as it happens; habits are ticked in one sitting at day's end; runs are logged once, after the fact; the weekly review is a scroll, not a task.
- **Thumb reach and one hand.** Mobile-first, 360px minimum, primary actions in the lower third of the screen.

## Capabilities and Constraints

**Confirmed in v1** — planner (workout plans and habits), workout tracker with personal records and a rest timer, habit board with streaks and consistency bonuses, run tracker with distance milestones and records by bracket, home with quote, score breakdown, twelve-month heatmap and activity log, goals, job application pipeline, body metrics, rewards, settings, and a first-launch guide.

**Explicit non-goals for v1:** GPS tracking, wearable sync, social feed, AI chat logging, nutrition tracking.

**Invariants that constrain every future change** (full detail in `docs/DAILYFORGE_SPEC.md` §4–§5; this numbered list is what the source comments cite):

1. Points are awarded only by `PointsService`; no other module writes to `points_entry`.
2. The ledger is append-only. Undo writes a compensating entry with `reverses_id` set.
3. Score is `SUM(amount)`. The cache must be rebuildable exactly from the ledger.
4. The frontend never calculates points; it renders what the API returned.
5. All day logic goes through `DayService`. No `LocalDate.now()` elsewhere, no `new Date()` for logical dates in the frontend.
6. No magic numbers — every point value, multiplier and cap comes from `points_rule_config` or the user's own configuration.
7. Every write endpoint is idempotent via the `Idempotency-Key` header.
8. Every query is scoped by `user_id` at the repository layer.
9. Schema changes go through Flyway migrations only.
10. Editing the past triggers reconciliation in the same transaction.

**Terminology** — *points* (the single currency), *ledger entry* (one immutable row), *reversal* (a compensating entry, never a delete), *streak* (consecutive scheduled days completed), *consistency bonus* (paid at every multiple of seven days), *commitment bonus* (all of a day's active habits done), *strict habit* (one that carries a penalty for missing), *elite exercise* (appears on several plan days, tracked once per date), *bracket* (a run distance tier), *reconciliation* (recompute, then diff against the ledger).

**Build order, confirmed for the first deployable slice:** spec milestones M0 through M3 — foundations and design system, identity and shell, the points engine, then habits. That yields an app where the owner signs in with Google, creates habits, logs them, and sees points, streaks and bonuses working for real. Workouts (M4), runs (M5), home (M6), goals/jobs/body (M7), rewards and onboarding (M8), Android and PostgreSQL (M9) follow in order.

**Open product decisions**, each with a default already chosen in spec §13 so no work blocks: milestone distances read as 21.1/42.2 km, one point per logged set, highest run milestone only, consistency exponent capped at 12, habit schedule days govern penalties, seven-day workout edit window, metric units, rewards at M8, zero points for job activity, a local curated quote pool, multiple plans with one active, and added-weight-only bodyweight personal records.

## Brand Commitments

- **Name:** DailyForge. Confirmed, not up for renaming. Package root `com.dailyforge`, npm scope `dailyforge`. No identifier in code is to be renamed.
- **Concept the owner has already committed to**, recorded here because it arrived as a fixed constraint rather than a visual proposal: *cold steel, earned heat* — the interface is a quiet workshop, and warm colour appears only where the user has earned something. An untouched day is grey; a logged day glows; a personal record is the hottest thing on screen.
- **Voice:** plain, direct, sentence case, never scolding. Guardrail messages are neutral, not accusatory ("That looks out of range. Check the distance."). Every empty state proposes a next action; every error says what to do next.
- **Mark:** an anvil, spark or ingot is the obvious direction. No logo asset exists yet.

## Evidence on Hand

- `docs/DAILYFORGE_SPEC.md` — the full build specification, version 1.0, supplied by the owner. It is the source of truth for contracts, data model, API surface, page specs and build order.
- **Nothing else exists.** No code, no designs, no logo, no licensed fonts, no quote pool, no exercise catalogue, no user data, and no hosting accounts provisioned beyond an unused Firebase account. There are no customers, no testimonials, no benchmarks, no pricing and no usage numbers — future work must not invent any.

## Product Principles

1. **The ledger is the truth; everything else is a view.** Any feature that would need points computed somewhere other than `PointsService`, or a score stored as a mutable running total, is designed wrong.
2. **Logging must survive the worst moment.** Between sets, one-handed, no signal, low motivation. Speed and resilience beat completeness of input.
3. **Warmth is earned, never decorative.** Colour, animation and celebration are rewards the interface pays out. If it is not showing an earned quantity, it stays cool and quiet.
4. **Undo is always available and always honest.** Every points-earning action can be taken back, the points come back with it, and the history says so plainly.
5. **Built to be maintained by one person.** Boring, readable, tested code; one milestone at a time, each independently runnable and demoable.

## Accessibility & Inclusion

No formal external standard is contractually required, but the owner set a quality floor that future work must hold:

- Usable at 360px wide and with a keyboard alone.
- Contrast at least 4.5:1 for text in both light and dark themes.
- Colour is never the sole carrier of meaning — the heatmap's glyphs and labels carry state independently of hue.
- The heatmap is keyboard-navigable and every cell carries a descriptive label ("12 March, 45 points, workout and run").
- `prefers-reduced-motion: reduce` must remove all non-essential motion and replace every celebration with a static state change plus the number update. The app is animation-heavy by design, so this path is not optional.
- Live regions announce points changes.
- Tap targets at least 44px.
