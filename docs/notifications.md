# Phone notifications (Web Push)

DailyForge sends two kinds of notification, both through standard Web Push:

- **Rest is over**: when a rest between sets ends, even with the app in the background or closed.
- **Daily reminder**: once a day at a time you pick, only if something is still open (habits, a challenge, protein).

## One-time server setup

The server signs every push with a VAPID key pair (P-256). Notifications stay switched off
until the keys are set.

1. Generate a key pair (any VAPID generator works, or OpenSSL):

   ```sh
   openssl ecparam -name prime256v1 -genkey -noout -out vapid.pem
   openssl ec -in vapid.pem -text -noout
   ```

   The public key is the 65-byte `pub` value and the private key is the 32-byte `priv`
   value, each written as unpadded base64url.

2. In Render, open the `dailyforge-api` service, go to **Environment**, and add:

   | Variable | Value |
   |---|---|
   | `VAPID_PUBLIC_KEY` | the public key |
   | `VAPID_PRIVATE_KEY` | the private key (secret: never commit it) |
   | `VAPID_SUBJECT` | `mailto:` followed by a contact address |

3. Save. Render redeploys with notifications on.

Changing the key pair later makes every existing subscription stop working: each device
then has to turn notifications on again in Settings.

## On the phone

Settings → Notifications → **Turn on notifications**, then **Send a test**.

- **Android (Chrome):** works in the browser and in the installed app.
- **iPhone (iOS 16.4+):** only from the installed app. Share → Add to Home Screen, open
  DailyForge from the Home Screen icon, then turn notifications on.

## Timing

The API sleeps outside the keep-warm window (01:00 to 18:59 UTC, 06:30 to 00:30 IST). A
reminder due while it sleeps is still sent if the server wakes within three hours of the
reminder time; otherwise that day's is skipped. Rest-over pushes are always on time,
because the server is awake while you train.
