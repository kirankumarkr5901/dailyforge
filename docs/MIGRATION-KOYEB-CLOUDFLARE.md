# Migrating: Render → Koyeb, Netlify → Cloudflare Pages

Two separate moves for two separate reasons. Do them one at a time, and keep the old
host running until the new one is verified — at no point should you be without a working
app.

**Why each move:**

- **Koyeb**, for cold starts. Render's free tier stops the container after ~15 minutes
  idle and the next request pays 30–60s to boot. Measured warm, the API answers in
  0.17–0.32s; the problem is entirely the sleeping.
- **Cloudflare Pages**, for the credit allowance. Netlify's 300-credit cycle was half
  gone in a day of iterating. Cloudflare Pages is unlimited bandwidth and 500 builds a
  month, and unlike GitHub Pages it can set response headers, which this app needs.

**What neither move fixes:** login takes ~2.2s because bcrypt runs at cost factor 12 on
a shared free CPU. Koyeb's free tier is also a shared CPU, so expect an improvement, not
a cure. See the note at the end.

---

## Part 1 — Backend to Koyeb

### 1.1 Create the service

1. <https://app.koyeb.com> → sign in with GitHub → **Create Web Service**
2. Source: **GitHub** → repository `daily-forge` (or `dailyforge` once the migration of
   the repo itself is finished) → branch `main`
3. Builder: **Dockerfile**
   - Dockerfile location: `backend/Dockerfile`
   - Work directory / build context: `backend`
4. Instance: **Free** (`nano`). Region: **Singapore** (`sin`) — it must match Neon, or
   every query crosses the planet.
5. Exposed port: **8080**, protocol HTTP, path `/`
6. Health check: **HTTP**, port 8080, path `/actuator/health`

### 1.2 Environment variables

Copy these across from Render (**Render → dailyforge-api → Environment**). The database
values are the same — you are pointing a second service at the same Neon database, which
is exactly why no data moves.

| Key | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | same as Render |
| `DATABASE_USERNAME` | same as Render |
| `DATABASE_PASSWORD` | same as Render (mark **secret**) |
| `DB_POOL_SIZE` | `3` |
| `DAILYFORGE_AUTH_JWT_SECRET` | **copy the existing value from Render** (mark secret) |
| `CORS_ALLOWED_ORIGINS` | your current site URL for now |
| `GOOGLE_CLIENT_ID` | same as Render |

> **Copy `DAILYFORGE_AUTH_JWT_SECRET`, do not generate a new one.** It signs the access
> tokens already on your devices. A new secret invalidates every session instantly and
> signs you out everywhere — which is the exact symptom we just spent a long time fixing.

### 1.3 Memory

The free instance is small. `backend/Dockerfile` already sets
`-XX:MaxRAMPercentage=75 -XX:+UseSerialGC`, which is sized for exactly this. If the
service restarts in a loop with an OOM in the logs, drop `DB_POOL_SIZE` to `2` before
anything else — each pooled connection costs real memory.

### 1.4 Verify before switching anything

```
https://<your-app>-<org>.koyeb.app/actuator/health     → {"status":"UP"}
```

In the logs you should see Flyway report the schema already at its current version and
apply nothing — the database is shared and already migrated. If it tries to apply
migrations from scratch, you have pointed it at the wrong database. Stop and check.

Then leave it idle for 20 minutes and hit it again. That is the whole point of the move:
it should still answer immediately.

### 1.5 Cut over

1. **Cloudflare/Netlify → environment** → set `API_BASE_URL` to
   `https://<your-app>-<org>.koyeb.app/api/v1` → redeploy the frontend
2. **Koyeb → environment** → set `CORS_ALLOWED_ORIGINS` to the exact frontend origin
3. **Google Cloud Console → Credentials → your OAuth client** → the frontend origin is
   what matters here; it only changes if the frontend URL changes
4. Test sign-in, log a habit, then delete the Render service

---

## Part 2 — Frontend to Cloudflare Pages

### 2.1 Create the project

1. <https://dash.cloudflare.com> → **Workers & Pages** → **Create** → **Pages** →
   **Connect to Git** → the repository → branch `main`
2. Build settings:
   - Framework preset: **None**
   - Build command: `npm run build:deploy`
   - Build output directory: `dist/frontend/browser`
   - **Root directory: `frontend`**
3. Environment variables (Production **and** Preview):

| Key | Value |
|---|---|
| `API_BASE_URL` | `https://<your-api>/api/v1` |
| `NODE_VERSION` | `22` |

### 2.2 What carries the config

Cloudflare Pages does not read `netlify.toml`. Two files in `frontend/public/` do the
job instead, and Angular copies them into the build output:

- `_redirects` — the SPA fallback, so a hard refresh on `/habits` serves the app
- `_headers` — the security headers, the CSP, and the no-store rules on `index.html`,
  `ngsw.json` and `ngsw-worker.js`

Those last three are not optional. They are what lets a new deploy reach a device that
has already installed the PWA; without them an installed app can sit on a stale bundle
indefinitely, which is how two auth fixes stayed invisible on a real phone.

### 2.3 Verify

```
https://<project>.pages.dev/            → the app loads
https://<project>.pages.dev/habits      → 200, not 404   (proves _redirects)
curl -sI https://<project>.pages.dev/ | grep -i content-security-policy   (proves _headers)
curl -sI https://<project>.pages.dev/index.html | grep -i cache-control   → no-store
```

### 2.4 Cut over

1. **Koyeb → `CORS_ALLOWED_ORIGINS`** → `https://<project>.pages.dev`
2. **Google Cloud Console → Credentials → Authorised JavaScript origins** → add
   `https://<project>.pages.dev` (keep the old one until you delete the old site)
3. Test sign-in and a write on the new URL
4. Only then delete the Netlify site

---

## Your data

Nothing in either move touches it. Every habit, workout and point lives in Neon, and
both hosts are stateless in front of it. You could delete both services and recreate
them from this repository without losing a row.

---

## The remaining 2 seconds

Login is slow because bcrypt runs at cost factor 12 and a free shared CPU is roughly 9×
slower at it than a dedicated one — measured: `/auth/capabilities` 0.17s versus
`/auth/login` 2.2s on the same instance, the difference being one bcrypt comparison.

Lowering `dailyforge.auth.bcrypt-strength` to `10` cuts the work fourfold and stays
above the usual floor. But **the cost factor is stored inside each hash**, so the change
only applies to passwords set afterwards: existing accounts keep their cost-12 hashes
and their 2.2s logins until the password is changed. Worth doing for what comes later,
not a fix for today.
