# Changelog

Add user-facing Horizon Nav changes under **Unreleased** before publishing each build. Keep entries focused on features, improvements, and fixes in the app—not GitHub Actions or release-publishing maintenance.

## Unreleased

### Improved
- Added Android Auto map settings controls for adjusting overlay UI scale and locator arrow scale. The Android Auto template interface uses tap-to-cycle scale levels rather than a continuous slider.

### Improved
- Increased the Android Auto locator arrow scale to almost twice its previous size.
- Fixed the What's New card to use the app's resolved day/night theme rather than a default dark palette.

## 1.0.246 — 2026-10-28

### Improved in-app updates
- Show the installed and available app versions in the update prompt.
- Display release notes in the update prompt when the release provides them.
- Show download progress as a percentage and downloaded/total megabytes.
- Improve update failure messages, including checksum verification failures, timeouts, and cancellations.
- Only open the APK in a browser when the device has no download service, avoiding a browser fallback that could bypass checksum verification after other download failures.
- Keep update detection working if `version.json` is missing or malformed by falling back to the version number in the release title or notes.

## Historical versions

Earlier builds were published by replacing assets on the single `app` release. Their original release notes and assets may not be recoverable from GitHub Releases. The new versioned-release history starts with the first successful publish using this workflow.
