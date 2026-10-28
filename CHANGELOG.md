# Changelog

Add user-facing Horizon Nav changes under **Unreleased** before publishing each build. Keep entries focused on features, improvements, and fixes in the app—not GitHub Actions or release-publishing maintenance.

## Unreleased

### Improved What's New appearance
- Added a light/day-mode colour scheme for the What's New card, using the app's active theme so text and release notes remain readable in both day and night modes.

### Fixed initial map zoom
- Apply the initial two-step zoom-out after the lane zoom floor, so the first-open overview adjustment is not overridden while stationary.
- Keep the existing automatic zoom behaviour once movement is detected.

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
