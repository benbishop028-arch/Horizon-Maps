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
- Fuel prices on Android Auto: euro and Australian prices showed as pence ("178.9p"); they now show the local way (€1.789, 189.9c).
- Turn notification with Android Auto on: only the car screen updates it now, so it no longer flicks between two slightly different distances.
- Speed cameras on a route are matched again whenever a fresh search finds different cameras, not only when the number found changes.
- Feedback report: it now lists the lanes for the next turn exactly as the route data gives them (which way each lane goes, and the ones to use), so a junction with the wrong lane arrows can be traced.
- Day and night following the head unit: its night mode is now checked every 1.5 seconds (it was every 4) and straight away when the system's night mode setting changes. The feedback report now shows when each night signal last changed, to track down units that are still slow.
- Traffic in the time left: with Mapbox routes the time left could stay green with red traffic on the map, because the delay was measured against the usual time for that hour, so everyday rush-hour traffic counted as none. The delay is now the time the amber and red stretches take over their no-traffic time, so the time left turns amber or red and shows "+X min traffic" when there is traffic. (The arrival time already included the traffic.)
- Speed cameras on a route: cameras found by the search round you are now matched to the route too, so ones the route search missed are shown and warned about. A camera now has to be within 30 m of the route's line (it was 45 m from a point on it), and one mapped twice gives one alert, so cameras on a road alongside no longer set off alerts.
- Speed cameras without a route: a camera now has to be close to the line you're driving along (within 30 to 40 m to the side) rather than within an angle ahead, which took in cameras on other roads up to a few hundred metres away.
- Road name under the arrow: it now comes from the road the map draws under you, so it changes as soon as the road does (it used to stay on the old road until the next turn, or until a new route, and could vanish when the lookups failed). On the phone, head unit and Android Auto (GitHub #35, #36).
- Trip card figures running together in portrait: with an electric car (a fourth figure and a fifth button), or any time they don't fit on one line, the buttons now go on a line under the figures.
- Miles in metric settings: with kilometres chosen, settings now say km, km per kWh, litres per 100 km and cents, and the route's fuel or charging cost shows in your own currency. Saved figures stay the same.
- Android Auto cards white on a dark map: around sunset the map's light dims to dusk before the car switches to night, and the cards stayed white. They now go dark with the map (unless "Always day" is picked).
- Arrow lagging behind in stop-start traffic: after stopping, the arrow now moves off again as soon as you do. It used to wait until you were going about 9 mph, or had moved up to 20 m.
- Offline routes failing with "Exceeded maximum circumference for exclude_polygons": with areas to avoid switched on (such as clean air zones), offline routes failed when an area was too big for the offline router. Big areas are now left out of offline routes, and if the router still refuses, the route is planned without them instead of failing.
- Route stopping at a border: with no signal and a destination outside your downloaded maps, the route used to end at the edge of the map as if that were the destination. It now says the map ends there, doesn't announce "you have arrived" at the border, and plans the rest of the way as soon as there's signal.
- "No signal" with signal: when the head unit's date and time are wrong, every secure connection fails and looked like no signal. The app now spots this and tells you to check the date and time.
- Place names for shared offline areas: the towns and villages download now covers every country in the area, for example Singapore and Brunei as well as Malaysia, and the Gulf States pack's countries.
- Android Auto speed sign jumping: on some screens it kept jumping between beside the cards and above them as their text changed. It now stays put unless there's clearly room.
- Update failing with "No such file or directory": trying an update again (or after an earlier try) no longer deletes the file it has just downloaded, so Update works again, including saving to Downloads for AAEnabler.
- Android Auto arrow under the cards: on cars that report their screen oddly (seen on a Uconnect 8.4), your arrow was pushed left under the turn and trip cards. It now always moves clear of them, and the feedback report includes the car screen's layout.
- Mapbox key "401" errors: a refusal no longer switches the map off straight away. Horizon Nav now asks Mapbox what's wrong with the token and says so plainly (mistyped or cut short, deleted or replaced, or out of date), and if Mapbox says the token is fine the map just loads again. A token that looks cut short or mistyped is flagged as you paste it, and the feedback report says what Mapbox made of the token.
- Map shaking when stopped: standing still at lights or parked, the map no longer drifts and turns with the GPS's small wobbles (it was worst on Android Auto, where it shook all the time). It now stays put until you actually move off.
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
- Lane sign over the road: coming up to a junction with lane guidance, a sign now stands over the road at the junction, like a motorway gantry, with the lanes to use lit up. It replaces the lane strips painted across the road, which often sat a lane or two out from the real lanes. Where the map has real lane data that matches the lanes you need (on many motorways), the lanes are still painted on the road, exactly over the real ones, with the route line through them. With Demo mode on, a short message says which was used (also in the feedback report).
- Where lanes are painted on the road, the route line runs through the lit-up lanes right up to the junction and carries smoothly into the turn arrow, then eases back onto the road over the next 25 m, with no gap or step where the lane guidance ends.

### New
- Charger availability: the charging list shows how many chargers are free right now ("8 of 12 free", red when none are), with each charger's top power and plug types, and the suggested charging stop on a long electric trip says how many are free there. Live counts use your TomTom key, where the charger's operator shares them; others say "No live info".
- Lorry check: with Lorry chosen, the route choice checks the height, weight, width and length limits along the whole route in the background and says "no limits under your lorry" or which it found (e.g. "1 low bridge (3.9 m)").
- Lorry parks: a "+ Lorry park" stop on the route options, and Lorry parks in place of Parking on the map page, finding lorry parks and services that take lorries (from OpenStreetMap), with opening hours, spaces and showers where mapped.
- Speed sign position on Android Auto: bottom, middle or top of the side, separate from the phone's (Settings > Android Auto, or the car's own Settings > More settings).
- Send a crash report from Settings: the problem report now has a Send report button, which sends it with your unit's details like Feedback does.
- Tilt settings: Settings > Map look > 3D view sets how far the 3D map tilts, separately for a wide screen, a tall screen and Android Auto (30° to 65°, or Auto as before).
- Full screen switch: Settings > Screen & sizes > Screen. Off keeps the status and navigation bars showing, so a head unit's own bar (climate, home button) isn't covered by the map.
- North up on Android Auto: a "Map direction" row in Android Auto's Settings switches between North up and turning with you, remembered for the car (GitHub #39).
- Fuel prices in Australia: New South Wales and Tasmania prices from the NSW government's FuelCheck, with a free API key and secret from api.nsw.gov.au, in cents a litre.
- Lorry routes: Settings > Routes > Vehicle can now be set to Lorry, with its height, width, length, weight, axle weight and hazardous goods. Routes from TomTom and offline routes then keep off low bridges, weight limits and lorry bans for that size; Mapbox routes use the height, width and weight. A lorry never gets a car route as a fallback. Each route shows a lorry badge: blue when every measurement was used, amber when only height and weight could be (Mapbox).
- Low bridge and weight limit warnings: for a lorry on a route, height, weight, width and length limits on the road ahead show as signs on the map, and one the lorry is over gets a red card under the turn card and a spoken warning at about 1 km and again at 300 m. On the phone, head unit and Android Auto.
- Next turn in the notification: while you navigate, Horizon Nav's notification now shows the turn arrow, distance, road and arrival time, marked as a navigation notification. Car launchers, smartwatches and other screens that show navigation notifications can pick it up. Switch it off in Settings > Guidance > Turn card.
- Speed position: Settings > Screen & sizes > Speed position moves the speed limit and speedometer to the middle or top of its side (away from the cards), for when your hand covers it at the bottom.
- Map on the driver's display: on cars whose Android Auto offers it, the screen behind the steering wheel now shows the map following you, with your route (it used to show only the app's icon). Switch it off in Android Auto's Settings > Map on the driver's display.
- Number-plate cameras on the map: Flock and other automatic number-plate (ALPR) cameras mapped on OpenStreetMap now show as a purple eye badge, within about 10 km of you as you drive. Tap one to see what it is. Shown only, no alerts; switch them off in Settings > Map.
- Smaller offline downloads: big countries now open up to show their regions in Choose an area (Germany's states, France's regions, England's counties, and regions of Italy, Spain, Poland, the Netherlands, Russia, India, Japan, Indonesia and Brazil), each a much smaller download than the whole country.
- Near you: Choose an area now starts with the area you're in (your region or state, or all of the country).
- Clock setting: Settings > Screen & sizes > Units can now set 12 or 24-hour times by hand, as well as following the device.
- Smaller offline areas: Singapore and Brunei can now be downloaded on their own, instead of together with Malaysia.
- Report on Android Auto: a Report button (where the Search button was) opens Police, Crash, Hazard, Broken down and Mobile camera; one tap saves the report where you are, as on the phone. Search is still in the search bar on the map, or at the top of Places on cars that don't pass taps on to the map.
- Zoom remembered: the zoom you set with + and − when you're not on a route is kept for next time, on the phone and on Android Auto separately. Tap Re-centre while the map is following you to go back to automatic zoom.
- Default zoom: set your own normal zoom for when you're not on a route. Zoom with + and − on the map and tap "Make this my default", or use the slider in Settings > Map look, where Reset puts it back. Auto zoom still pulls out at speed and moves in at junctions, round your setting.
- Traffic overview when stopped: after 30 seconds stopped at lights or in a queue, the map pulls back to a top-down view of the traffic round you, and goes back as soon as you move off. On by default; switch it off in Settings > On the map.
- Where you parked: when Android Auto disconnects, the phone remembers where the car was. A "Your car" card shows how long ago and how far, with a Walk button and a car pin on the map, for 2 days or until you're back in the car.
- Send places from your phone: pair the phone with the head unit once (Settings > Places & trips), then "Send to car" on the phone's route screen. The head unit offers it the next time it's on (within 12 hours) and starts it after a few seconds. Places go through a free message relay, scrambled with your pairing code.
- Donate: Horizon Nav is free with no ads, and you can now support it with a donation: Settings > About > Donate, with a QR code to scan on your phone or a button to open PayPal. A one-off card also asks, a day or so after installing (never while you're driving).
- 2D / 3D on Android Auto: a 2D / 3D button in Android Auto's own buttons, as in the app (while navigating it takes the Demo button's place unless Demo is switched on in Settings).

### Improved
- Horizon theme at night: a softer, brighter "dusk" light with a gentle glow, and buildings a step lighter than the ground instead of the same shade. Settings > Map look > Horizon at night sets the light (Dusk or Night), the map's brightness and how much the buildings stand out, and the map changes as you move them. Day keeps its own colours.
- Parking at your destination: the car parks offered near the end of a trip now show their price, opening hours and spaces where OpenStreetMap has them.
- Your car on the phone: the phone now remembers where you parked at the end of any drive (not only after Android Auto), and the "Your car" card offers Walk back once you've walked away from it.
- "Hey Google, navigate to …" on Android Auto: a place asked for by name or address now starts its route to the top result by itself (the route choice shows where to, with Cancel), instead of leaving a list to tap while driving.
- Android Auto settings: on cars that only allow a few rows in a list, the rest now go on a "More settings" page (the last ones used to be cut off). The most used come first: voice, map direction, day and night, route options. "Locator arrow scale" is now "Arrow size" and only changes the car screen's arrow (it used to change the phone's arrow too), and "Map UI scale" is "Overall size", matching the app's Settings > Android Auto.
- Settings tidied: speed camera icons (On the map) and speed camera warnings (Alerts) now say which is which, and the speed sign size says it's for this screen only (Android Auto has its own).
- Smoother lanes: while the junction's road is still being looked for, the lanes are only redrawn once it's found, instead of every second.
- Problem report: crash reports now say which version and when, and one left over from before an update says so (it may already be fixed), so an old report isn't mistaken for a new problem.
- Lane guidance on the map: all the lanes are drawn on the road again, with the lane to use lit up and its arrows and the other lanes in a darker road colour with clear white lines (so they no longer fade into the map), as the view zooms in before the junction. They're laid out on the road itself (centred on a motorway or one-way road; on a two-way road all on your side of the middle), at a slimmer width, so they no longer spill over the verge; only where the map has no road there is just your lane drawn, under the route line. The route line moves into the lit lane as you come up to the junction, so the two always line up (it keeps looking for the junction's road as you get closer, and moves over even where the lane list only starts at the junction itself, so it no longer happens only some of the time). Settings > Turn card & lanes > Lanes shown as also offers a close-up picture of the junction's lanes under the turn card, or the sign over the road from the last few versions.
- Settings easier to scan: long lists are split under more headings (3D, Sky and satellite; Speed, Cameras, Hazards, Zones and reminders; Directions, Voice and volume; Brightness, Sizes, Speed display; and on Android Auto, Sizes on the car screen and Setting it up). Nothing has moved section, and the settings search finds everything, now including "Show petrol stations too".
- Electric cars: with Electric chosen as your fuel, the map page shows Charging first and Toilets instead of the petrol Fuel button (on the phone and Android Auto). Settings > Fuel > Show petrol stations too brings it back.
- Android Auto settings: the 12 / 24-hour clock can be set from the car too.
- Search while navigating on Android Auto: the Report screen now has a Search tile, for finding a stop on the way.
- Cooler, longer-lasting phone: when the phone gets warm or battery saver is on, the map draws fewer frames a second (30, or 20 when hot), whatever frame rate is chosen. The feedback report now says how warm the phone was.
- Zoom out further: − now goes two steps further out than before, for an overview of the traffic around you, and that zoom is remembered too.
- North up remembered: if you leave the map north up, it opens that way next time, on the phone and on Android Auto separately.
- Better routes with TomTom and Mapbox together: with a TomTom key and a Mapbox token (and routes left on Auto), a new route is now asked of both at once. The quicker one comes first, and a different road from the other is offered as another choice.
- Clock times follow your device: arrival and leave-by times now use the phone's or head unit's own 12 or 24-hour setting (for example 4:52 pm instead of 16:52).
- Fold away everything on Android Auto: the arrow in the search bar now folds away Home and Work and the fuel, charging, food, coffee and parking buttons as well as Saved & recent.
- Your side of divided roads: on dual carriageways and other divided roads, your arrow now sits on the side you're driving on, not whichever side the GPS happened to be nearer.
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
