# Horizon Maps Plus key server

A small Cloudflare Worker that lets the Google Play edition use the developer's Mapbox and TomTom accounts without putting
any key inside the app.

- `POST /session`: the app asks for access. Plus subscribers (checked with Google Play) get a Mapbox token that lasts one
  hour and a session for TomTom. Everyone else (the free OpenStreetMap version) gets a token for Mapbox's map engine only.
- `/tomtom/...`: TomTom requests from Plus subscribers, passed on with the real TomTom key added here.

The keys never leave the server. The app only ever holds tokens that stop working within an hour.

## Setting it up (once)

1. **Cloudflare**: make a free account at dash.cloudflare.com. Install Wrangler (`npm i -g wrangler`), then `wrangler login`.
2. **Mapbox**: at account.mapbox.com › Tokens, create a new token with the secret scope `tokens:write` and the public
   scopes `styles:read`, `styles:tiles`, `fonts:read`, `datasets:read`, `vision:read`. Copy it (it starts `sk.`).
3. **TomTom**: your TomTom API key from developer.tomtom.com.
4. **Google Play**: in Google Cloud, create a service account, download its JSON key, and in Play Console › Users and
   permissions invite its email with "View financial data, orders and cancellation survey responses".
5. **Secrets**: from this folder run each of these and paste the value when asked:
   ```
   wrangler secret put MAPBOX_USER            # your Mapbox username
   wrangler secret put MAPBOX_SECRET          # the sk. token from step 2
   wrangler secret put TOMTOM_KEY             # step 3
   wrangler secret put SESSION_SECRET         # any long random text
   wrangler secret put GOOGLE_SERVICE_ACCOUNT # the whole JSON file from step 4
   ```
6. **Deploy**: `wrangler deploy`. It prints the server's address (like `https://horizon-maps-keys.<you>.workers.dev`).
7. **Switch it on in the app**: put that address in `app/build.gradle.kts`, in the `play` flavour's `KEY_SERVER`.

## Play Console

One subscription, product ID `horizon_plus`, with two base plans:
- `monthly`: £1.99, renews monthly
- `yearly`: £18.99, renews yearly (20% less than twelve months)

Each with an offer giving new subscribers a 7-day free trial.
