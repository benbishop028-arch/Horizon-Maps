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
- Left-hand drive: the speedometer moves back to the bottom left (the cards are on the right), so the turn card no longer covers it; the speed limit sign stays on the screen side of it. The exit banner no longer repeats what the road sign on the turn card already says.
- Smoother map drawing: the map is no longer capped at 30 frames a second on top of the camera's own pacing, which made it judder.
- Android Auto with left-hand drive: your arrow no longer hides under the turn and trip cards, and the speed limit sign no longer runs off the right-hand edge of the screen.
- Android Auto: the speedometer sits down in the bottom-right corner again, level with the trip card, instead of floating halfway up the screen.
- Android Auto, tidier screen: hazards ahead now sit along the bottom of the turn card, the sound button has moved onto the trip card, the "Use the right lane" line is gone where the lane arrows already show it, the trip card has a slimmer progress bar and bigger labels, and while the next turn is far away the turn card shrinks to one line.
- Android Auto: the car arrow and route line are a little bigger, so they stand out from the buildings.
- Traffic on the roads around you now stays on the map while you navigate (it used to vanish as soon as a route started). Settings > Map can still limit it to your route.
- Adding a TomTom key no longer switches the map to TomTom's flat map, which made the 3D buildings and models disappear. Maps that were switched that way go back to the 3D Mapbox map once (TomTom's map can still be picked in Settings).
- Satellite view now works with the downloaded map chosen, and stays visible at night instead of going almost black.
- When the map can't load for want of a working Mapbox key (missing, pasted with hidden characters, or turned down by Mapbox), a box now says so and lets you paste a new one, instead of a blank map with stars. Stray spaces and invisible characters are now taken out of pasted keys.
- Big 3D buildings beside the road no longer vanish as you drive past them (the clear-view area round your arrow is tighter).
- Offline maps > Around home is no longer squashed to one side.
- Spoken directions: a new Voice channel setting (Settings > Sound) for head units where the beeps sound but the voice doesn't; a clear message when there's no text-to-speech voice or language installed; and alerts no longer cut a direction off half way.
- Outside the UK and Ireland, motorway exits are now called "exit 12" rather than "junction 12".
- If the app fails to start several times in a row (for example when the graphics driver crashes), it starts with the heaviest 3D features turned down and says so.
- Android Auto: fixed a black car screen when Android Auto starts the app before it has been opened on the phone (after an update, for example); the turn card uses the compact lane strip so it no longer grows huge at junctions; Settings explains how to get the app into Android Auto, and the update card warns AAEnabler users that updating removes it from Android Auto.
- The What's new card after an update now shows the changes straight away, even with no connection: the notes are built into the app instead of fetched from GitHub, which often refused.
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
