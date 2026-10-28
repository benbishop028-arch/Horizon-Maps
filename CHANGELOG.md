# Changelog

Add user-facing Horizon Nav changes under **Unreleased** before publishing each build. Keep entries focused on features, improvements, and fixes in the app—not GitHub Actions or release-publishing maintenance.

## Unreleased

- Fetch the installed release notes directly by tag as a fallback if GitHub's releases-list API fails, while retaining notes for skipped releases on multi-version upgrades.
- Automatically fit the Android Auto speedometer and speed-limit sign to the available display area to prevent edge clipping, including split-screen layouts.
- Add a dedicated Android Auto speedometer/speed-limit sign size slider.


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
