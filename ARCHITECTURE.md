# Horizon Nav architecture notes

This document records current ownership boundaries and the intended low-risk refactoring order. It is deliberately not a claim that the large navigation files have already been split.

## Current responsibilities

- `ui/`: Compose screens and presentation. Keep view state and interaction here; move reusable policy/validation out when it can be tested without Android UI.
- `NavEngine.kt`: navigation orchestration and route/session state. Its public navigation models and country conventions have now been moved to `NavModels.kt`; the engine remains large and should be split incrementally by cohesive responsibility, preserving its API.
- `MapBinding.kt`: Mapbox view/style/lifecycle integration. Base-style selection and the shared “full detail enables heavy layers” rule now live in the pure `MapStylePolicy.kt`; SDK style constants and actual map mutations remain in `MapBinding` so lifecycle ownership stays in one place.
- `Geo.kt`, `NavigationPolicy.kt`: geographic calculations and pure navigation decisions. GPS-accuracy-aware off-route distance thresholds and strike counting now live in `NavigationPolicy`, with boundary-focused unit tests; `NavEngine` still owns the actual reroute lifecycle and calls.
- `OfflineRouter.kt`, `OfflineMap.kt`, `OfflinePlaces.kt`, `OfflineServer.kt`: offline routing, map/places access, and loopback serving. Keep network parsing and request limits close to their boundaries.
- `Prefs.kt`: observable preferences, backup import/export, and compatibility migrations. New key names and migrations should be documented and tested; never include API keys in exported backups.
- `Updater.kt`: Android/GitHub/download/install I/O. Pure metadata validation is now in `UpdaterPolicy.kt`, so it can be tested independently of Android services.

## Refactoring rules

1. Keep existing package names, application IDs, preference names, and persisted formats stable unless a migration is explicitly included.
2. Extract small pure functions first; avoid broad moves of SDK-coupled code in one change.
3. Add tests around the extracted behavior before changing call sites.
4. Preserve lifecycle ownership and cancellation when moving asynchronous work.
5. Do not publish a release or change signing credentials as part of a source refactor.
6. Run unit tests and a release build in GitHub Actions before installing a refactored APK on a head unit.

## Next safe extractions

1. Continue extracting `NavEngine.kt` route matching / reroute decision helpers where inputs and outputs can be represented without Mapbox or Android objects. Off-route threshold and strike-count decisions are now extracted; route snapping and lifecycle remain in the engine.
2. Continue extracting `MapBinding.kt` layer-configuration decisions into pure policies, keeping SDK mutations in `MapBinding`. Base-style selection and the full-detail feature gate are extracted and covered by dedicated unit tests.
3. Keep preference keys centralised in `PreferenceKeys`; all literal keys used by the `Prefs` string/boolean/integer/float accessors and literal `prefs.put` calls have now been migrated, while dynamically composed keys remain explicit.
4. Continue replacing blocking retry loops only when their calling thread and cancellation/lifecycle contract are established; do not change timing-sensitive loops without an identified owner.


### Preference-key stability

API-key, map-style/detail, Mapbox migration/prompt, locator appearance, and commonly shared navigation/map/UI settings are declared in `PreferenceKeys`. Their call sites use constants so future changes are visible and deliberate. Persisted strings remain unchanged, preserving existing settings and backup compatibility. `PreferenceKeysTest` locks down representative legacy names. All literal keys passed to the `Prefs` typed accessors and literal `prefs.put` calls are now constants. Dynamically composed keys remain literal by design because they are generated at runtime; tests cover representative persisted names.

- `NavigationPolicy.kt` owns the pure heading-away reroute predicate, with boundary tests for distance, speed, heading difference and non-finite headings.

- `NavigationPolicy.kt` now also owns the pure implausible-progress-jump predicate. `NavEngine` supplies the candidate/trusted progress, speed and elapsed time, while retaining responsibility for route snapping and reroute lifecycle. Boundary tests cover the existing 400 m minimum allowance and speed/time allowance.
- `CrashGuardPrefs.kt`: stable names for the separate crash-recovery SharedPreferences file and its startup-failure / last-ANR keys. `MainActivity` and `FreezeWatch` share these constants; persisted names remain unchanged.

## Additional Priority 2 extraction (2026-10)

- `RouteMatcher.kt` owns the nearest-segment GPS-to-route projection used by `NavEngine`; the engine retains the last-segment hint and reroute/lifecycle state. The local scan window and 60 m full-route fallback remain unchanged.
- `MapRouteGeometry.kt` owns pure nearest-segment projection plus lane-shift point projection/splicing used by the rendered route line and locator. The 40 m tolerance is unchanged; Mapbox source/layer mutations remain in `MapBinding`.
- `NavigationPolicy.mapFrameLimit` now clamps the configured Mapbox frame-rate cap, and `MapBinding.setMaxFps` honours it instead of always forcing 60 FPS. This makes the existing 20/30/45/60 and auto/default choices affect renderer load as intended.
- Android Auto's pending destination wait is now interrupted when superseded or when the session is destroyed, and a token prevents a stale worker from clearing a newer pending wait.
- `FreezeWatch.start` is synchronized to prevent duplicate watchdog threads if startup paths race.

## Verification status

The new geometry helpers have standalone Kotlin smoke coverage and corresponding JUnit tests. The repository workflow already runs `:app:testPublicDebugUnitTest`, assembles the release APK, and verifies its signature. A full local Android build remains unavailable where this archive was prepared because Gradle and the Android SDK are not installed; CI must pass before treating a release as verified.


### Map worker lifecycle hardening

`MapBinding.destroy()` is now idempotent and marks the binding as destroyed before cleanup. Its single-thread workers for lamp and traffic-signal enrichment are shut down immediately, and their main-thread callbacks avoid touching map sources after destruction. Signal updates also refuse to queue new work once the binding has been destroyed. This reduces background work and stale callbacks when the car display is disconnected or the map is recreated.


## Additional Priority 2 completion pass

- Centralised the remaining literal preference names used by typed `Prefs` reads and literal `prefs.put` calls (including route-avoidance, country detection, last position, and update state) without changing their persisted strings. Added compatibility assertions for representative names. Runtime-composed keys remain dynamic.
- Extracted Android Auto viewport zoom compensation into `NavigationPolicy.carViewportZoomOffset`, leaving `NavEngine` responsible for deciding when the compensation applies. The pure policy has boundary/typical-display tests.
- `NavEngine` and `MapBinding` remain large because their remaining logic is tightly coupled to navigation lifecycle and Mapbox SDK state. Further broad file splitting is intentionally not claimed as complete without a full Android build/test pass; the cohesive pure helpers and lifecycle boundaries are extracted and documented.


## Priority 3: build, configuration and release polish

- CI prefers the checked-out Gradle source tree and retains `HorizonNav.zip` as a compatibility fallback for archive-only repositories.
- Update discovery uses a published `version.json` asset (`version`, `build`, `versionCode`) instead of scraping a version number from human-edited release title/body text. APK SHA-256 validation remains mandatory. Existing releases without `version.json` intentionally fail closed; the next published update adds the required metadata.
- The workflow passes the TomTom secret through an environment variable rather than interpolating it directly into a shell command.
- R8/minification and resource shrinking stay disabled pending a full release build and on-device regression tests. No keep-rule set is assumed safe without that validation.
- Full Android build, JUnit suite, signature verification and head-unit testing still need to run in the configured GitHub Actions environment/device; this local environment has no Android SDK/Gradle installation.


### Online-to-offline routing fallback

`NavigationFallbackPolicy` classifies connection-related exceptions (including wrapped I/O failures, DNS failures, timeouts and unavailable-network messages). Initial route planning and automatic rerouting use it to fall back to an installed offline routing pack when the online provider fails because of connectivity. Authentication and invalid-route errors are not silently treated as network failures.
