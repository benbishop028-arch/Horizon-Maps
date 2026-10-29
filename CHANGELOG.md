# Changelog

Add user-facing Horizon Nav changes under **Unreleased** before publishing each build. Keep entries focused on features, improvements, and fixes in the app—not GitHub Actions or release-publishing maintenance. There is no need to delete entries after publishing: notes that an earlier release already included are left out of the next one automatically.

## Unreleased

- Fix lane guidance pushing the route line and lane strips off the road at ordinary junctions. Lanes are now lined up with the road only on motorway and trunk carriageways; elsewhere the lane to be in is drawn under the route line, and lane strips are only painted on the last stretch before the junction.
- Add alternative-route line colour customisation, with a pale blue-grey default for better contrast against grey roads.
- Improve Android package-installer access to downloaded update APKs by explicitly granting the content URI through ClipData as well as intent flags.
- Fix the map zooming in and out far too slowly while driving (it could take 10 seconds or more to close in for a turn), and the map starting zoomed out twice as far as intended.
- Fix roundabout arrows pointing the wrong way: they now show the direction the real exit goes, rather than guessing from the exit number.
- The "Look for updates by itself" switch works again, and automatic update and map-data checks run at most every half hour instead of every time the app opens or the connection comes back.
- Changing the alternative-route colour now applies straight away.
- The car screen's "Navigation arrow size" now sizes the arrow on the map (as on the phone) instead of the turn card.
- Stop the speedometer covering the trip card on left-hand-drive layouts, and stop the exit banner repeating what the road sign on the turn card already says.
- Smoother map drawing: the map is no longer capped at 30 frames a second on top of the camera's own pacing, which made it judder.
- Android Auto with left-hand drive: your arrow no longer hides under the turn and trip cards, and the speed limit sign no longer runs off the right-hand edge of the screen.
- No TomTom key is built into the app any more (anyone could pull it out of a shared APK); paste your own in Settings if you use TomTom search.

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
