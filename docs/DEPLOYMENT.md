# DailyForge — Deployment phase plan

The rule that shapes this document: **the app is deployable from the end of Phase 1 and stays deployable forever after.** Nothing here is a big-bang cutover. Each phase ends with something you can open on your phone.

Target stack, confirmed: Angular static build on **Netlify** (or Vercel), Spring Boot container on **Render** (or Fly.io), PostgreSQL on **Neon** (or Supabase). All three have usable free tiers. The vendor inside each slot is still swappable — nothing below depends on a vendor-specific API.

---

## Phase 0 — Local, file-backed H2. No accounts, no cloud.

**Goal:** the whole app runs on your machine with one command each side, and data survives a restart.

1. `backend/` boots on `spring.profiles.active=local` with
   `jdbc:h2:file:./.data/dailyforge;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`.
   File mode, not in-memory — you cannot test a 7-day streak against a database that forgets on restart. `.data/` goes in `.gitignore`.
2. Flyway runs from `V1__baseline.sql` on boot. `hibernate.ddl-auto=validate` from day one, so a drifting entity fails the build instead of silently reshaping the schema.
3. Tests use H2 **in-memory**, fresh per test class. Two different databases for two different jobs.
4. `frontend/` proxies `/api` to `localhost:8080` via `proxy.conf.json`, so there is no CORS in development and no hardcoded host anywhere.
5. A seed migration loads the demo user with two months of history, the exercise catalogue, the quote pool, and `points_rule_config`.

**Exit test:** create a habit, tick it seven days running by moving the system clock, and see the consistency bonus land in the ledger. Restart the backend. The points are still there.

---

## Phase 1 — Staging, on real infrastructure, still with test data.

**Goal:** prove the deployment pipeline while the data is still disposable. Do this *before* the app is finished, not after — a deploy pipeline discovered late is a week lost.

### 1a. Database — Neon
- Create a Neon project, one branch named `staging`. Copy the pooled connection string.
- Flyway runs the **same** migration files against PostgreSQL. This is why `MODE=PostgreSQL` and plain-SQL migrations were non-negotiable in Phase 0: there is no separate production schema to maintain.
- Verify with `./gradlew flywayInfo` pointed at Neon before deploying any code.

### 1b. Backend — Render
- Add a multi-stage `Dockerfile` to `backend/`: Gradle build stage, then a slim JRE 21 runtime stage. Render builds from the repo.
- Environment variables, none of them in git:
  `SPRING_PROFILES_ACTIVE=staging`, `DATABASE_URL`, `JWT_SECRET`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `CORS_ALLOWED_ORIGINS`, `APP_BASE_URL`.
- Health check path `/actuator/health/liveness` (never the full `/actuator/health`, which wakes the database on every poll). Render restarts the container when it fails.
- **Free-tier caveat, plan for it:** Render's free instances sleep after inactivity and cold-start in tens of seconds. That is tolerable for staging. For production either accept it, pay the smallest paid tier, or ping the health endpoint on a schedule.
- The hourly rollover job runs inside the app via `@Scheduled`. On a sleeping free instance it will miss ticks — which is exactly why §5.6 requires the job to be idempotent and to catch up any missed days rather than assuming it ran on time. Verify this behaviour here, not in production.

### 1c. Frontend — Netlify
- Build command `npm run build -- --configuration=staging`, publish directory `dist/frontend/browser`.
- `apiBaseUrl` comes from `environment.staging.ts`. Never a literal `localhost` in any committed file.
- A `_redirects` file with `/*  /index.html  200` so Angular's client-side routes survive a hard refresh.
- Security headers and a CSP in `netlify.toml`. Add `accounts.google.com` to `script-src` and `frame-src` for the sign-in button.

### 1d. Google OAuth
- In Google Cloud Console: one project, OAuth consent screen in **Testing** mode with your own account as a test user, then a Web application OAuth client.
- Authorised JavaScript origins: `http://localhost:4200` and the Netlify staging URL.
- Authorised redirect URIs: the backend's callback on both localhost and Render.
- **Implementation note:** the frontend obtains a Google ID token; the backend verifies its signature, `aud`, `iss` and expiry server-side, then issues DailyForge's own JWT and refresh token. A Google token is never accepted as a session token, and Google's user id is stored as `google_sub` on the user record so an email change does not orphan the account.
- Account linking: a Google sign-in whose verified email matches an existing password account links to that user rather than creating a duplicate.
- **Firebase is not required for any of this.** Your Firebase account stays as a fallback option (Firebase Auth, or Firebase Hosting instead of Netlify) if you later prefer it.

### 1e. CI — GitHub Actions
- On every push: `./gradlew test` and `npm test`, plus the Playwright journeys against a locally started stack.
- On green `main`: Render and Netlify auto-deploy. A red build must block the deploy, or CI is decoration.

**Exit test:** open the Netlify staging URL on your phone, sign in with Google, create a habit, tick it, watch the score animate. Close the app, reopen it tomorrow, and the streak is correct in your time zone.

---

## Phase 2 — Production. Same pipeline, real data.

Nothing new is invented here. Production is Phase 1 with different secrets and a stricter posture.

1. **Neon `main` branch** as the production database. Confirm point-in-time recovery is on and take a manual backup before every migration that touches `points_entry`.
2. **Secrets are rotated, not copied** from staging. `JWT_SECRET` is a fresh 256-bit value, read from the environment and never logged.
3. **A separate Google OAuth client** for production origins. Publish the consent screen if anyone other than you will sign in; keep it in Testing mode while it is only you.
4. **CORS** is pinned to the exact production origin. No wildcard, ever.
5. **Rate limits** live on `/auth/*`. This is the one endpoint family the whole internet can reach.
6. **Custom domain** on Netlify with automatic TLS; the backend keeps its Render hostname or gets an `api.` subdomain.
7. **Observability before you need it:** structured JSON logs with a request id, every ledger write logged at INFO with its rule code and amount, and an alert on the health check. When a points total ever looks wrong, the ledger plus these logs are the only way to reconstruct what happened.
8. **The migration drill, run once on staging first:** back up → deploy the migration → run the score `recalculate` endpoint → assert that the rebuilt total equals `SUM(amount)` for every user. That equality is the app's single most important invariant, and it is the one thing worth checking after every schema change.

**Exit test:** restore last night's backup into a scratch Neon branch, run `recalculate`, and confirm the score matches. If that drill has never been rehearsed, the backup is a hope, not a plan.

---

## Phase 3 — Android, via Capacitor.

This is spec milestone M9 and it is a packaging step, not a rewrite, provided M1's rules were honoured: API base URL from the environment, safe-area insets respected, token storage behind one `TokenStorage` interface.

1. `npm run build && npx cap sync android`.
2. Google sign-in on Android needs its **own** OAuth client of type Android, registered with the app's package name and signing-certificate SHA-1. The web client id will not work.
3. Point the debug build at your machine's LAN IP for on-device development, release builds at the production API.
4. Signed release build, then Play Console internal testing before any wider track.

---

## What is deliberately not decided yet

- **Netlify vs Vercel, Render vs Fly.io, Neon vs Supabase.** All three slots are interchangeable and the choice costs nothing to defer. Pick when you deploy Phase 1.
- **Whether production stays on a free tier.** The cold-start behaviour is the deciding factor, and you cannot judge it before Phase 1 exists.
- **Whether Firebase gets used at all.** Currently: no. Recorded as an available fallback, not a dependency.
