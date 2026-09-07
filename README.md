# DailyForge

A manual-logging fitness and habit tracker where every logged action becomes points, and points are the single currency across the whole app. Workouts, runs, habits, activities and goals all feed one score, and that score is spendable on rewards you define yourself.

- **Specification:** [docs/DAILYFORGE_SPEC.md](docs/DAILYFORGE_SPEC.md) — the source of truth.
- **Product record:** [PRODUCT.md](PRODUCT.md) — users, purpose, invariants, confirmed decisions.
- **Deployment:** [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) — four phases, deployable from phase 1.

---

## Status

**M0 — Foundations. Complete.** Monorepo, both scaffolds, the Flyway baseline for the points ledger, the design token system with theme and motion switching, fifteen UI primitives, and the gallery that proves them.

**M1 — Identity and shell. Complete.** Signup, login, refresh with token rotation, logout and settings. The app shell with header, bottom navigation, drawer and a desktop rail. Anonymous browsing, with any write answering `AUTH_REQUIRED` so the sign-in sheet can open and replay the action afterwards.

Google sign-in is built but **dark by default**: it appears only when `GOOGLE_CLIENT_ID` is set, and the API says so at `/api/v1/auth/capabilities`. No code change is needed to switch it on — see [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) §1d.

**M2 — Points engine. Complete.** The spine of the app (spec §5): an append-only ledger, idempotent awards, reversal, reconciliation, the score cache, the daily rollover job, and the guardrail caps. `/dev/points` remains as an internal debug page for the engine directly (its one write endpoint does not exist at all when `SPRING_PROFILES_ACTIVE=prod`).

**M3 — Habits. Complete.** The first feature that actually earns a point (spec §4.3, §5.4, §8): create, edit, reorder and archive habits; the streak and consistency-bonus calculator, matching the spec's own worked example exactly; a strict habit's missed-day penalty; the whole-day commitment bonus; the daily rollover job closing out missed scheduled days; and the board read model with its server-computed bonus-hint strip. The tracker UI ticks and unticks live against the real engine, with an Undo toast on every change and the live bonus preview shown while creating a habit.

**M4 — Workouts. Complete.** The shared exercise catalog (spec §6), workout plans with days and an Extras bucket for unassigned exercises, and the tracker itself (spec §8.3): logging a set, the personal-record calculator (recent and lifetime, reconciled the same way a habit's streak is — a heavier set overtakes the old record and reverses its bonus), the whole-session completion celebration, and a rest timer that starts automatically after a set. Bodyweight exercises rank by added weight only, per the plan; cardio exercises earn the per-set point with no PR bonus, per the plan's own table.

Plan-day reordering is up/down rather than drag-and-drop, and an elite exercise's multi-day placement has no dedicated assignment picker yet — both are reachable through the ordinary add/move actions, matching the same trade the habit planner made at M3. The exercise history chart is a plain list for now; a charting library is not yet part of the stack.

Screens for M5–M7 are routed to a placeholder naming the milestone that builds them, so the navigation is real and testable now.

Milestones M5–M9 are listed in spec §11. **M5 is the run tracker.**

## Requirements

- **JDK 21.** A newer JDK is fine — Gradle resolves a 21 toolchain automatically on first build.
- **Node 22+** and npm 10+.
- Docker, only if you want to rehearse a migration against real PostgreSQL.

## Running it

Two terminals.

```bash
# backend — http://localhost:8080
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'

# frontend — http://localhost:4200
cd frontend
npm install
npm start
```

The frontend proxies `/api` to the backend, so there is no CORS in development and no hostname is hardcoded anywhere.

### Environment variables

| Variable | Needed | Notes |
|---|---|---|
| `DAILYFORGE_AUTH_JWT_SECRET` | Deployed environments | At least 32 bytes. The app refuses to start on anything shorter, because a short HMAC key is a forgeable one. Local dev has a throwaway default. |
| `GOOGLE_CLIENT_ID` | Only for Google sign-in | Blank disables the feature cleanly rather than half-enabling a broken button. |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Staging and production | PostgreSQL. Unused locally. |
| `CORS_ALLOWED_ORIGINS` | Staging and production | Exact origins, never a wildcard. |

Open **http://localhost:4200/dev/ui** for the design gallery: every primitive, every state, with theme, motion and width switches in the bar at the top.

### The database

Local development uses **file-mode H2** at `backend/.data/dailyforge`, not in-memory. That is deliberate: you cannot test a seven-day streak against a database that forgets on restart. Delete the `.data` directory to start clean.

Tests use in-memory H2, fresh per run. Production uses PostgreSQL 16. The same Flyway migrations run on all three, which is why they avoid `citext`, native enums, `jsonb` and partial indexes — see the note at the top of `V1__baseline.sql`.

The H2 console is at http://localhost:8080/h2-console on the `local` profile (JDBC URL `jdbc:h2:file:./.data/dailyforge`, user `sa`, no password).

## Tests

```bash
cd backend && ./gradlew check     # unit + slice tests, plus coverage verification
cd frontend && npm run test:ci    # vitest
```

Both run in CI on every push and pull request. The points module (spec §10: "target 90%+ coverage... this is where correctness actually matters") carries its own enforced floor in `backend/build.gradle.kts` — `./gradlew check` fails if line coverage under `com.dailyforge.points.*` drops below 85%.

## Layout

```
dailyforge/
├── backend/           Spring Boot 4, Java 21, Gradle (Kotlin DSL)
│   └── src/main/java/com/dailyforge/
│       ├── common/    time, errors, config — DayService lives here
│       └── …          one package per module, added milestone by milestone
├── frontend/          Angular 20, standalone components
│   └── src/
│       ├── styles/    tokens.css and base.css — the design system
│       └── app/
│           ├── core/  theme, motion, i18n, logical dates
│           ├── shared/ui/  the fifteen primitives
│           └── dev/ui/     the design gallery
├── docs/
└── docker-compose.yml PostgreSQL, for rehearsing migrations
```

## The rules that are not negotiable

These are the ones that cause subtle, expensive bugs when broken. Full detail in spec §4–§5.

1. Points are awarded only by `PointsService`. Nothing else writes to `points_entry`.
2. The ledger is append-only. Undo writes a compensating entry; it never deletes or edits one.
3. Score is `SUM(amount)`. The cache must be exactly rebuildable from the ledger.
4. The frontend never calculates points — it renders what the API returned.
5. All day logic goes through `DayService`. No `LocalDate.now()` elsewhere, no `new Date()` for logical dates in the frontend.
6. No magic numbers. Every point value comes from `points_rule_config`.
7. Every write endpoint is idempotent via `Idempotency-Key`.
8. Every query is scoped by `user_id` at the repository layer.
9. Schema changes go through Flyway. `ddl-auto=validate`, always.
10. Editing the past triggers reconciliation in the same transaction.

## Design

**Cold steel, earned heat.** The interface is a quiet workshop — cool greys, precise rules, tabular numerals. Warm colour appears only where you have earned something. An untouched day is grey; a logged day glows; a personal record is the hottest thing on screen.

The rule is enforced by the token system rather than by memory: heat tokens are earned-value tokens, and a component that is not showing an earned quantity has none to reach for. There is deliberately no warm "primary" or "brand" colour available.

Design tokens are CSS custom properties in `frontend/src/styles/tokens.css`. Never hardcode a colour in a component.
