// Horizon Maps Plus key server (a Cloudflare Worker). See README.md for setting it up.
//
// POST /session            the app asks for access. Body: { install, purchaseToken, product, package, version }.
//                          Plus subscribers (checked with Google Play) get a Mapbox token that lasts an hour, and a session for
//                          TomTom. Everyone else (the free OpenStreetMap version) gets a token for Mapbox's map engine only.
// ANY  /tomtom/<path>      a TomTom request from a Plus subscriber (header X-Plus-Session), passed on with the real TomTom key.
//
// Secrets (set with `wrangler secret put NAME`): MAPBOX_USER, MAPBOX_SECRET, TOMTOM_KEY, SESSION_SECRET, GOOGLE_SERVICE_ACCOUNT.
// The keys never leave this server: the app only ever sees hour-long tokens.

const PACKAGE = "com.headunit.nav.play";
const PRODUCT = "horizon_plus";
const HOUR = 3600;
// what an hour-long Mapbox token may do: Plus gets the full map (3D, satellite, traffic) and Mapbox's routes and search;
// the free version only what the map engine needs to start (its map itself comes from OpenStreetMap)
const PLUS_SCOPES = ["styles:read", "styles:tiles", "fonts:read", "datasets:read", "vision:read"];
const FREE_SCOPES = ["fonts:read"];
// TomTom services a Plus session may use (nothing else gets through)
const TOMTOM_PATHS = ["search/2/", "routing/1/", "traffic/services/", "maps/orbis/", "map/1/", "search/3/"];

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    try {
      if (url.pathname === "/session" && req.method === "POST") return await session(req, env);
      if (url.pathname.startsWith("/tomtom/")) return await tomtom(req, env, url);
      if (url.pathname === "/") return new Response("Horizon Maps key server", { status: 200 });
      return new Response("Not found", { status: 404 });
    } catch (e) {
      return json({ error: String(e && e.message || e) }, 500);
    }
  },
};

function json(o, status = 200) {
  return new Response(JSON.stringify(o), { status, headers: { "content-type": "application/json", "cache-control": "no-store" } });
}

// ------------------------------------------------------------------ the app's session

async function session(req, env) {
  const body = await req.json().catch(() => ({}));
  const install = String(body.install || "").slice(0, 64);
  if (!/^[0-9a-f-]{16,64}$/i.test(install)) return json({ error: "bad install id" }, 400);
  let plus = false, checked = false;
  const token = String(body.purchaseToken || "");
  if (token) {
    plus = await subscriptionActive(env, token);
    checked = true;
  }
  const mapbox = await mapboxToken(env, plus ? PLUS_SCOPES : FREE_SCOPES);
  const out = { mapbox, expiresIn: HOUR, plus, checked };
  if (plus) out.session = await sign(env, install, Math.floor(Date.now() / 1000) + HOUR);
  return json(out);
}

// an hour-long public token, made from the account's secret token (Mapbox's temporary tokens can't last longer than an hour)
async function mapboxToken(env, scopes) {
  const expires = new Date(Date.now() + HOUR * 1000).toISOString();
  const r = await fetch(`https://api.mapbox.com/tokens/v2/${env.MAPBOX_USER}?access_token=${env.MAPBOX_SECRET}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ expires, scopes }),
  });
  if (!r.ok) throw new Error("Mapbox token: HTTP " + r.status);
  return (await r.json()).token;
}

// ------------------------------------------------------------------ Google Play: is this subscription paid up?

async function subscriptionActive(env, purchaseToken) {
  const access = await googleAccessToken(env);
  const r = await fetch(`https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`,
    { headers: { authorization: "Bearer " + access } });
  if (!r.ok) return false;
  const s = await r.json();
  const okState = ["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD"].includes(s.subscriptionState);
  const okProduct = (s.lineItems || []).some(li => li.productId === PRODUCT && (!li.expiryTime || Date.parse(li.expiryTime) > Date.now()));
  return okState && okProduct;
}

let cachedGoogle = { token: "", until: 0 };

// a service account's sign-in, done by hand (a signed JWT swapped for an access token), so no libraries are needed
async function googleAccessToken(env) {
  const now = Math.floor(Date.now() / 1000);
  if (cachedGoogle.token && cachedGoogle.until > now + 60) return cachedGoogle.token;
  const sa = JSON.parse(env.GOOGLE_SERVICE_ACCOUNT);
  const head = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = b64url(JSON.stringify({
    iss: sa.client_email, scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600,
  }));
  const key = await crypto.subtle.importKey("pkcs8", pemToBytes(sa.private_key), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const sig = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(head + "." + claim)));
  const jwt = head + "." + claim + "." + b64urlBytes(sig);
  const r = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=" + jwt,
  });
  if (!r.ok) throw new Error("Google sign-in: HTTP " + r.status);
  const t = await r.json();
  cachedGoogle = { token: t.access_token, until: now + (t.expires_in || 3600) };
  return t.access_token;
}

function pemToBytes(pem) {
  const b = atob(pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, ""));
  return Uint8Array.from(b, c => c.charCodeAt(0));
}
function b64url(s) { return b64urlBytes(new TextEncoder().encode(s)); }
function b64urlBytes(bytes) {
  let s = ""; for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

// ------------------------------------------------------------------ TomTom, for Plus sessions

async function hmac(env, text) {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(env.SESSION_SECRET), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return b64urlBytes(new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(text))));
}
async function sign(env, install, exp) { return `${install}.${exp}.${await hmac(env, install + "." + exp)}`; }
async function verify(env, s) {
  const [install, exp, sig] = String(s || "").split(".");
  if (!install || !exp || !sig) return false;
  if (Number(exp) < Date.now() / 1000) return false;
  return sig === await hmac(env, install + "." + exp);
}

async function tomtom(req, env, url) {
  if (!await verify(env, req.headers.get("x-plus-session"))) return new Response("Not a Plus session", { status: 401 });
  const path = url.pathname.slice("/tomtom/".length);
  if (!TOMTOM_PATHS.some(p => path.startsWith(p))) return new Response("Not allowed", { status: 403 });
  const q = new URLSearchParams(url.search);
  q.delete("key");
  q.set("key", env.TOMTOM_KEY);
  const headers = { "content-type": req.headers.get("content-type") || "application/json" };
  // (the traffic incidents service is told which details to send in a header of its own)
  for (const h of ["attributes", "tomtom-api-version", "accept-language"]) { const v = req.headers.get(h); if (v) headers[h] = v; }
  const init = { method: req.method, headers };
  if (req.method !== "GET" && req.method !== "HEAD") init.body = await req.arrayBuffer();
  const r = await fetch(`https://api.tomtom.com/${path}?${q}`, init);
  return new Response(r.body, { status: r.status, headers: { "content-type": r.headers.get("content-type") || "application/json" } });
}
