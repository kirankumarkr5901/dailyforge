# Progress photos (Cloudflare R2)

Progress photos are stored in Cloudflare R2, which is S3-compatible and has a free tier of
10 GB. The phone shrinks each photo to about 1600 px (a few hundred KB) and uploads it
**straight to R2** with a short-lived upload link from the API. The API never handles the
image itself. Viewing uses one-hour links. The bucket is never public.

Photos stay switched off until all four settings below are present.

## 1. Create the bucket

1. Cloudflare dashboard → **R2 Object Storage**. The free plan needs a payment method on
   file but does not charge within the free limits.
2. **Create bucket**, e.g. `dailyforge-photos`. Leave public access **off**.

## 2. Allow the app to upload (CORS)

Open the bucket → **Settings → CORS policy → Edit**, and paste this, with your app's
address:

```json
[
  {
    "AllowedOrigins": ["https://dailyforge.pages.dev"],
    "AllowedMethods": ["GET", "PUT"],
    "AllowedHeaders": ["content-type"],
    "MaxAgeSeconds": 3600
  }
]
```

Without this, the browser blocks uploads to R2.

## 3. Create an access key

R2 → **Manage R2 API tokens → Create API token**:

- Permissions: **Object Read & Write**
- Specify bucket: only `dailyforge-photos`

Copy the **Access Key ID** and **Secret Access Key** (the secret is shown once), and your
**Account ID** (on the R2 overview page).

## 4. Add the settings to Render

`dailyforge-api` → **Environment**:

| Variable | Value |
|---|---|
| `R2_ACCOUNT_ID` | the account ID |
| `R2_ACCESS_KEY_ID` | the access key ID |
| `R2_SECRET_ACCESS_KEY` | the secret access key (keep it private, never commit it) |
| `R2_BUCKET` | `dailyforge-photos` |

Save; Render redeploys. The Body page then shows **Add photo**.

## How it fits together

- Objects are stored as `users/{user id}/{random id}.jpg`. The API refuses to record an
  object outside the signed-in user's own folder.
- An upload link is valid for 10 minutes and accepts a body of exactly the size it was
  issued for (the `content-length` header is signed).
- Deleting a photo removes its record and the object in R2. If the R2 delete fails, the
  record is still removed and the leftover object is logged.
- The app's security headers allow `https://*.r2.cloudflarestorage.com` for uploads
  (`connect-src`) and display (`img-src`).
