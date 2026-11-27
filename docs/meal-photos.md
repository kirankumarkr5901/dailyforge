# Meal photos ("Snap a meal")

On the Nutrition screen, **Snap a meal** takes a photo of a plate. The API sends it to
Google Gemini, which lists the dishes it sees with an estimated portion and its nutrition
(protein, calories, fat, carbs, fiber, sugar). Nothing is logged automatically: the phone
shows the estimates, the user fixes names or portions or leaves dishes out, then taps
**Add to today**.

The feature is off until the server has a Gemini API key. Without one, the button is
hidden.

## Setting it up (free)

1. Go to <https://aistudio.google.com/api-keys>, sign in with a Google account and create
   an API key. The free tier needs no billing account.
2. On Render, open the API service, **Environment**, and add `GEMINI_API_KEY` with that
   key. Save; Render redeploys.
3. Optional: set `GEMINI_MODEL` to use a different Gemini model. The default is
   `gemini-3.5-flash-lite`, the model Google suggests for new projects. `gemini-3.8-flash`
   may read busy plates better, and it is also free.

Never commit the key or paste it anywhere else.

## What to know

- **Privacy:** on the free tier, Google may use what is sent (the meal photo and a fixed
  instruction) to improve its products. The request carries no name, email or user ID.
  The app says this under the photo buttons.
- **Limits:** the free tier has a per-minute and per-day request limit for the whole
  project, shown at <https://aistudio.google.com/rate-limit>. The daily limit resets at
  midnight Pacific time. When it runs out, the app says so and manual logging still works.
  The API also caps each user at 30 photos a day.
- **Size:** the phone shrinks the photo to 1024 px on its long side (JPEG) before
  sending it, so a photo is usually 100 to 300 KB.
- **Accuracy:** these are estimates from one photo. Portions and oil or ghee are the usual
  misses, which is why every dish has a portion stepper before it is added.

## How it works

- `GET /api/v1/protein/meal-photo` returns `{ "enabled": true | false }`.
- `POST /api/v1/protein/meal-photo` with `{ "photo": "<base64 JPEG>" }` returns
  `{ "dishes": [ { name, portionG, proteinG, kcal, fatG, carbsG, fiberG, sugarG } ] }`.
  Values are already rounded and clamped to the app's per-entry limits.
- `POST /api/v1/protein/logs/many` with `{ date, items: [...] }` logs the chosen dishes
  in one write, with points reconciled once.
- The server calls Gemini's Interactions endpoint
  (`POST https://generativelanguage.googleapis.com/v1beta/interactions`) with the photo
  inline and a JSON schema for the answer; see `MealPhotoService`.
