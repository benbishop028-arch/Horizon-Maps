# Working on Horizon Nav

Horizon Nav is a hobby Android navigation app (Kotlin, Jetpack Compose, Mapbox Maps SDK v11) for phones, Android head units and
Android Auto. Licence: GPL-3.0. The owner is not a developer: explain changes in plain words (what changed, what they need to do).

## Where things are
- Source: `app/src/main/kotlin/com/headunit/launcher/`
  - `NavEngine.kt`: routing, guidance, alerts, voice, search
  - `MapBinding.kt`: the map view and its layers
  - `Mapbox.kt` (Mapbox REST), `Overpass.kt` (OpenStreetMap speed limits and road data), `OfflineRouter.kt` (offline routing)
  - `Prefs.kt`: settings (`PreferenceKeys`)
  - `ui/ImmersiveNav.kt` (phone and head unit nav screen), `ui/CarOverlay.kt` (Android Auto screen), `ui/SettingsScreen.kt`
    and `ui/SettingsIndex.kt` (settings and settings search)
  - `car/`: Android Auto session
  - `I18n*.kt`: translations
- Unit tests: `app/src/test/kotlin/com/headunit/launcher/`
- There is no local build: GitHub Actions (`.github/workflows/build.yml`) builds every push to `main`.

## Rules
1. **Ask the owner before pushing anything.** Once they approve, push straight to `main` (there is no development branch).
2. **One commit per change, not one per file.** Put every file a fix touches into a single commit, and push once. Each push
   starts a build and uses up a version number, so many small pushes waste both.
3. **After pushing, check the Actions build passed.** If it failed, read the log, fix it and push the fix.
4. **Every app change gets a `CHANGELOG.md` entry** under `## Unreleased`, in its single `### New`, `### Improved` or `### Fixed`
   section (don't add new headings; there is one of each). Format: `- Headline: plain description (thanks to <github user>, #<issue>)`.
   Write for users, not developers. Only user-facing changes go there.
5. **All on-screen text goes through `L.t("...")`**, and new text is translated into all the app's languages (`I18n*.kt`).
6. **Show the owner a mockup before changing how any screen looks.** Under-the-hood work needs none.
7. **Never put a TomTom or Mapbox key in the code, the APK or the build.** Users enter their own keys in Settings.
8. **The APK signing key lives only in GitHub Secrets.** Never regenerate, replace, print or commit it. Never ask for the Play
   service account JSON (secret `PLAY_SERVICE_ACCOUNT_JSON`).
9. **Never post, comment, close or merge on GitHub yourself.** Give the owner suggested reply text; they post it. Keep the owner's
   email address private. When applying someone's pull request, keep them as the commit author.
10. **Think about slow head units** (the owner's has 6 GB RAM): no heavy work on the main thread, no per-frame allocations, and
    don't read fast-changing state (the turn, the speed, the position) at the top of a big screen; read it inside the smallest
    composable that needs it.
11. **Messages shown to users are short and plain.** Never include coordinates, keys or other personal details in them; put
    technical detail in the problem report instead.

## Releasing
After a push, the owner publishes with Actions › Build › Run workflow, branch `main`, tick "publish", and leaves `send_to_play`
on "not this time" for normal releases. Add `play-test.txt` (containing `open`) to the repo root only after Google approves the
closed test.
