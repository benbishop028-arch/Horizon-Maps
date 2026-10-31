# Changelog

Add user-facing Horizon Nav changes under **Unreleased** before publishing each build, under ### New, ### Improved or ### Fixed headings, each written as "Headline: what it does" (the What's new card shows the headline in bold with the rest underneath). Keep entries focused on features, improvements, and fixes in the app—not GitHub Actions or release-publishing maintenance. There is no need to delete entries after publishing: notes that an earlier release already included are left out of the next one automatically.

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
- New "Say road names" setting (Settings > Sound): turn it off for just the directions, e.g. "In 200 metres, turn left".
- Bioethanol (E85) as a fuel type: the fuel list then shows stations mapped as selling E85, with prices in France.
- Live fuel prices outside the UK: France and Austria by themselves, and Germany with a free Tankerkönig key (Settings > Fuel). Prices show the local way, e.g. €1.789.
- Avoid tolls in chosen countries only (Settings > Routes): a route that would pay a toll in one of them is planned without toll roads.
- Road name size: a slider for the name of the road you're on, under your arrow (Settings > Display, and for Android Auto in the car-screen sizes).
- Car type for the car arrow: saloon, hatchback, estate, SUV, pickup, van or sports, in any colour (Settings > Your arrow).
- New option (Settings > Map, off unless chosen): free-flowing main roads in green on the traffic map, as well as slow traffic in amber and red.
- Stop signs on your route are shown on the map, with a short chime before each one, where OpenStreetMap has them.
- Home and Work can be set or changed from Settings, and favourites can be renamed or made Home or Work.
- Easier stops: one-tap buttons in the stops box add the best fuel, charger, coffee, food, services or toilets along the route, plus your saved places; holding on the map while driving adds it as a stop.
- Electric cars: set your battery level and the least you want to arrive with on the route options; a charging stop is suggested when the trip needs one (with the charge you'll arrive at it with), the trip card shows the battery you'll arrive with and a Chargers ahead button, and Eco route uses TomTom's electric eco routing.
- No TomTom key is built into the app any more (anyone could pull it out of a shared APK); paste your own in Settings if you use TomTom search.

### Fixed
- Road name overlap: driving with no route, the road name under your arrow no longer sits on top of the "Heading NW" pill at the bottom (the pill already shows the road, so the one under the arrow steps aside while it's up).
- Junctions: the view now flattens towards 2D as you reach turns and roundabouts as intended, instead of swinging low enough to fill the screen with horizon. The junction close-up only applies at slip roads, exits and forks, where it moves closer and a little flatter.
- Android Auto trip card on wide screens: it no longer stretches right across a very wide car screen with its sound and end-route buttons hidden under Android Auto's own buttons; it's now just as wide as its figures and buttons.
- Fuel, parking and other nearby searches: they now use Mapbox's category search, which finds the nearest places of that kind, instead of a text search that often came back empty and fell through to the busy free OpenStreetMap servers ("HTTP 429", "HTTP 502"). Along a route, when those servers are busy, Mapbox is asked instead, and errors are in plain words.
- 2D / 3D is remembered: the map no longer starts in 3D every time if you'd left it in 2D.
- Adding a stop on the way (fuel, coffee and so on) no longer hangs: along the route Mapbox is asked first, a few points at once, and a search that gets stuck on a slow server now gives up after 30 seconds as it should (it could spin for minutes).
- Head units that restart while Horizon Nav is open (reported on a built-in Android 9 unit): the app now notices when the whole unit restarted soon after opening it or while driving, and after two it switches to safer drawing one step at a time, starting with drawing the map in the app's own window, which is gentler on some built-in units. What happened just before goes in the feedback report. The background journey-time suggestion also no longer runs the offline router, which loads large files into memory.
- Updating with AAEnabler: if you installed Horizon Nav with AAEnabler, Update now saves the new version to your Downloads folder to install with AAEnabler, so it stays in Android Auto (updating inside the app used to take it out).
- Missing 3D buildings: the clear area kept round your arrow no longer cuts through buildings. With no route there's no longer a straight strip ahead (it went through buildings on bends), and on a route the strip is narrower, so buildings in narrow streets stay.
- The offline maps page now points to Settings > Keys & accounts for the Mapbox token (it still said "Search & keys").
- Road name under your arrow: it no longer jumps to a side road for a moment at junctions (a new name now has to be confirmed, unless you've clearly turned), it clears in car parks and when it's out of date instead of showing the last road, and after an unnamed slip road it no longer shows the exit sign's text as the road name.
- Trees on Android Auto: the car map is now drawn at close to the phone's zoom, so Mapbox's 3D trees and other close-up detail show there too (it used to sit so far out that they never appeared). Map text on the car screen is a little smaller as a result.
- The traffic view when stopped no longer kicks in while you wait at a turn, and "Your car" is only saved when the car has stopped (switching apps on the car screen mid-drive could save it).
- Closer to 3D lane guidance: where the real painted lanes are found, the lit-up lanes now start 150 m before the junction (was 110 m), the route line sits in your lane from there and bends through the junction, and a small lane bubble rides on the road just ahead of your arrow showing the lanes to use. Where the real lanes can't be matched, the overhead lane sign is shown instead.
- Mapbox's lane data on TomTom routes: Mapbox draws the lanes on the map, so its own lane list suits them best. With a Mapbox token, a TomTom route now gets Mapbox's lane list in the background, junction by junction, wherever Mapbox's route goes the same way (other junctions keep TomTom's). It uses one extra Mapbox request per route and can be switched off in Settings > Turn card. The feedback report says which lane list was used.
- Lane sign over the road: coming up to a junction with lane guidance, a sign now stands over the road at the junction, like a motorway gantry, with the lanes to use lit up. It replaces the lane strips painted across the road, which often sat a lane or two out from the real lanes. Where the map has real lane data that matches the lanes you need (on many motorways), the lanes are still painted on the road, exactly over the real ones, with the route line through them. With Demo mode on, a short message says which was used (also in the feedback report).
- Where lanes are painted on the road, the route line runs through the lit-up lanes right up to the junction and carries smoothly into the turn arrow, then eases back onto the road over the next 25 m, with no gap or step where the lane guidance ends.

### New
- Default zoom: set your own normal zoom for when you're not on a route. Zoom with + and − on the map and tap "Make this my default", or use the slider in Settings > Map look, where Reset puts it back. Auto zoom still pulls out at speed and moves in at junctions, round your setting.
- Traffic overview when stopped: after 30 seconds stopped at lights or in a queue, the map pulls back to a top-down view of the traffic round you, and goes back as soon as you move off. On by default; switch it off in Settings > On the map.
- Where you parked: when Android Auto disconnects, the phone remembers where the car was. A "Your car" card shows how long ago and how far, with a Walk button and a car pin on the map, for 2 days or until you're back in the car.
- Send places from your phone: pair the phone with the head unit once (Settings > Places & trips), then "Send to car" on the phone's route screen. The head unit offers it the next time it's on (within 12 hours) and starts it after a few seconds. Places go through a free message relay, scrambled with your pairing code.
- Donate: Horizon Nav is free with no ads, and you can now support it with a donation: Settings > About > Donate, with a QR code to scan on your phone or a button to open PayPal. A one-off card also asks, a day or so after installing (never while you're driving).
- 2D / 3D on Android Auto: a 2D / 3D button in Android Auto's own buttons, as in the app (while navigating it takes the Demo button's place unless Demo is switched on in Settings).

### Improved
- The arrow in the search bar that folds Saved & recent away is back, and Android Auto has it too.
- Android Auto map page matches the app: Home and Work tiles with the drive time, five round buttons for fuel, charging, food, coffee and parking, and Saved & recent with your first few places (as many as fit) and "All" for the rest, in place of the long scrolling row of chips. Your arrow also sits in the middle of the clear map there when you're not navigating.
- Android Auto speed sign: it sits right up against the edge of the screen when Android Auto's buttons hide, and just beside them when they show.
- Arrow centred in the clear map: when you're not navigating, your arrow sits in the middle of the map between the search panel and the buttons, instead of dead centre behind them.
- Settings reorganised: 15 smaller sections in four groups (Map, Driving, You, Device), each split into short headed lists, with Android Auto in a section of its own. Search still finds every setting.
- What's new card redesigned: each change on its own card with an icon, a headline and a short description, sorted under New, Improved and Fixed.
- What's new in landscape: the card now spreads across the screen, with the title and Let's go button on the left and the changes in two columns on the right.
- Tidier map page: Home and Work are two tiles showing the drive time, and fuel, charging, food, coffee and parking are five round buttons that always fit. Saved & recent shows three places with "All" for the rest, and your own saved places are in it too.
- Fewer buttons on the map: just the compass and map options stay up while you drive. Zoom appears when you touch the map or stop, voice and sound are on the trip card, 3D / 2D and sound are quick switches at the top of map options, and Settings is beside the buttons when you're not navigating (and in map options at any time).
- Car types for the real 3D model: the hatchback, estate, SUV, pickup, van and sports car now have their own 3D models too, not just the saloon, and the car type picker shows for the real 3D car.
- Map options redesigned: Standard or Satellite as two picture cards, a quick Auto / Day / Night switch, and the map layers as tiles that light up when on. Reports fit on one row, and in landscape the panel is wider, in two columns, so nothing needs scrolling.


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
