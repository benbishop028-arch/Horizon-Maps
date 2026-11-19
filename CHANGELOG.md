# Changelog

Everything that has changed in Horizon Nav, newest first. Each version lists what's new, what's improved and what's fixed. Changes that are ready but not published yet are under Unreleased.

## Unreleased

### New
- Send your arrival time: a new button on the trip card opens your phone's share menu with a message ready ("On my way to …, arriving about 10:42") and a map link, to send by WhatsApp, text or anything else. It shows while you're stopped, so nobody picks a contact while driving (thanks to Dotancom)
- Fuel prices in Spain: every petrol station's live prices from the Spanish Ministry for the Ecological Transition (free, no key), in the fuel list, Cheapest in Add a stop and the fuel stop suggestions. Kept on the device and refreshed every few hours, so prices still show if the service is down for a while (thanks to franciscoserranoadt)
- Place preview: tap a shop or business on the map, or a search result, and a card shows what it is, whether it's open now and until when, Go with the drive time, Add stop, and Save, Call, Website, Share and Start here, then the address, opening hours, a petrol station's price or a charger's plugs. The details come from OpenStreetMap; Call and Website hide while you're moving (thanks to Dry-Secretary2456)
- Learns your regular journeys: the app remembers how long your usual trips (the commute, the school run) really take at different times of day, and once it has seen a trip twice it uses that for the arrival time. Kept only on your phone
- Arrival times that know the usual traffic: routes planned from your downloaded map (the free version, or anywhere without signal) now allow for the hold-ups that are normal at that time of day, more in the weekday rush hours and in towns than on motorways. Worked out on your phone, with nothing sent anywhere
- Street names in other alphabets spoken: when a street name is in letters your voice can't read (a Hebrew name with an English voice, say), it's now said in the phone's voice for those letters instead of being skipped. Works for Hebrew, Arabic, Cyrillic, Greek, Thai, Chinese, Japanese, Korean and more, if the phone has that voice (thanks to Dotancom)
- Fuel prices in Add a stop: tap the fuel icon in Add a stop and each petrol station along your route shows the price of your fuel, with the cheapest in green and the detour time under it. A new Cheapest tab sits next to Least detour, and priced stations the map search missed are added to the list. Prices come from the same place as the fuel list (Fuel Finder in the UK, and the price services in France, Austria, Italy, Germany and NSW)
- Russian: the whole app in Russian (Русский), on the phone, head unit and Android Auto, with spoken directions ("Через 200 м поверните налево", "выполните второй съезд") and Russian voice commands. Phones set to Russian pick it up by themselves; others can choose it in Settings › Sound & voice › Language (thanks to Zerux)
- Big speed limit sign: a new switch in Settings › Speed display swaps them round, with the speed limit sign big where the speedometer was and your speed small beside it. Your speed still turns amber and red near and over the limit (thanks to ole1993)
- Fuel prices in Italy: the fuel list and fuel stop suggestions show prices across Italy, from the government's own daily price list (MIMIT), with no key needed. The list is fetched at most once a day and kept on the device (thanks to TheDMAI)
- LPG (Autogas): a new choice under Your fuel. Prices show in Italy, France and Australia (NSW); elsewhere the list shows stations mapped as selling LPG (thanks to TheDMAI)
- Automatic backup: Settings › About › Backup can now back up by itself once a day to a folder you choose, either replacing the last file or keeping the last 7 (thanks to bijindas)
- Clock on the map: a small clock with the speedometer, for head units whose status bar is covered (Settings › While driving › Clock on the map) (thanks to bijindas)
- Avoid car trains: a new switch next to Avoid ferries keeps your route off trains that carry cars, such as the Channel Tunnel shuttle and the Alpine tunnel trains. If a car train is the only way there, it is still used (thanks to Resul9704). Car trains are now also recognised from OpenStreetMap's list of car-train lines, as Mapbox gives some of them (such as Brig–Iselle) as ordinary driving. Mapbox's routes can't reliably leave a car train out, so with the switch on and a TomTom key, TomTom plans the routes (it can); without a key, Mapbox's other suggested routes are tried first
- Close Horizon Nav: a button at the bottom of Map options ends any route, stops the GPS and closes the app, so nothing keeps running in the background (thanks to pc4096)
- Help test Horizon Maps on Google Play: a one-off invitation to join the testers for the coming Google Play version, with a QR code for head units. It never shows while you are driving, and a banner at the top of Settings opens it any time
- Your places and Your tracks shortcuts: two new buttons for the row under the search bar (Settings › Places & trips › Shortcuts). One lists your saved places, the other your GPX tracks, nearest first, on the phone and on Android Auto (thanks to bijindas)
- Map lettering: choose a wider, bolder font for road and place names on the map (Settings › Map style › Map lettering): Roboto, Open Sans, Inter, Lato or Montserrat (thanks to karthikeyansr-lgtm)
- Stops on Android Auto: tap the trip card's figures while navigating for the stops on the way, each with when you get there. Move them earlier or later, take one off or add one, then Update route (thanks to yurividal)
- Search while navigating on Android Auto: the trip card has a search button (Settings › Android Auto › Buttons on the trip card). A place picked while driving is asked about: Add as a stop, or Go here instead (thanks to yurividal)
- "Add a stop" by voice: "Hey Google, add a stop at…" while navigating adds it as the next stop and keeps where you're going, on Android Auto and on the phone (thanks to yurividal)
- Arrival time for each stop: the car's trip card shows the next stop and when you get there, and the stops editor on the phone shows the time for each (thanks to yurividal)
- Groups for GPX tracks, and tracks in the search: file your tracks in groups that fold open (the folder button on each track, in Settings › Places & trips › GPX tracks), and type a track's name in the search bar to find it (thanks to bijindas)
- Road name and heading switch separately: "Heading E" after the road name when driving without a route now has its own switch, so you can keep just the road (Settings › Screen & sizes › While driving) (thanks to liamstears)
- GPX tracks: import a .gpx file (from Komoot, Strava, Garmin or your own recording) in Settings › Places & trips › GPX tracks. Show puts it on the map; Drive follows it to the end with the usual spoken turns, carrying on from where you join it, and a reroute keeps you on the track (thanks to bijindas)
- Search as a button: with no route, the search bar and its buttons can fold into one round search button in the corner, so the map has the whole screen and your arrow sits in the middle. Settings › Places & trips › Search bar (thanks to vishnauth-bit)
- Auto zoom can be turned off: the map then keeps one steady zoom instead of following your speed and the junctions, and + and − still change it. Settings › Map style, under Default zoom (thanks to vishnauth-bit)
- Cantonese voice for Hong Kong: a new 繁體中文（香港・粵語） choice in the language list speaks the directions in Cantonese, and Hong Kong and Macau phones pick it by themselves (thanks to benkkwok)
- Media controls: back, play / pause and next at the bottom of the driving screen while music or radio plays on the head unit, from any app. Give it notification access (Settings › Screen & sizes › While driving › Song and artwork) and it shows the song, artist and artwork too. It only appears while something is playing, sits in the free space beside the trip card, and can be turned off in the same place (thanks to mrEnmi)
- Norwegian: Horizon Nav now speaks Norsk bokmål: directions, screens, Android Auto and voice commands ("kjør hjem", "naviger til Bergen"). Pick it in Settings › Sound & voice › Language, or it follows a phone set to Norwegian (thanks to mthjensen)
- What's new, any time: missed the update notes? Settings › About & backup › Version now has a What's new button that brings the card back (thanks to leedank93)
- Marathi: Horizon Nav now speaks मराठी: directions, screens, Android Auto and voice commands ("पुण्याला जा", "घरी चल"). Pick it in Settings › Sound & voice › Language, or it follows a phone set to Marathi. Place names on the map and in search stay in English, as Mapbox and TomTom have no Marathi (thanks to samadhanraut)
- Trip card as a column in split screen: in Android Auto's split screen, the trip card can now be a narrow column on the left under the turn card instead of a bar along the bottom, leaving the road ahead of the car clear. Settings › Android Auto › Trip card in split screen (thanks to Simsam53)
- Argentine Spanish (Español rioplatense): directions and screens with "vos" and local words (doblá, mantenete, auto, estacionamiento, celular). Phones set to Spanish in Argentina or Uruguay pick it up by themselves, or choose Español (Argentina) in Settings › Sound & voice › Language (thanks to ParquePatricense)
- Bosnian and Kannada: Horizon Nav now speaks Bosanski and ಕನ್ನಡ: directions, screens, Android Auto and voice commands. Pick them in Settings › Sound & voice › Language, or they follow a phone set to either (thanks to enesvrabac-lab and srinidhisharma)
- Groups for your places: file your own places in groups (Family, Customers…) in Settings › Places & trips › Your places. A group shows as one row in the saved list that opens to its places (thanks to bijindas)
- Top speed for arrival times: in Settings › Routes › Vehicle, set the speed you actually drive at (for vans, lorries or towing) and routes are timed no faster than that (thanks to clayts450)
- Sun times on the car screen: Day and night on Android Auto can now follow sunrise and sunset, for cars that go dark whenever the headlights are on, even in daylight (thanks to SonicBjork and nickblears-rgb)
- Swedish: Horizon Nav now speaks Swedish (Svenska): directions, screens, Android Auto and voice commands. Pick it in Settings › Sound & voice › Language, or it follows a phone set to Swedish
- Search card on Android Auto: tapping the search bar on the car screen now opens a card over the map with Recent, Categories and Saved down the side, instead of Android Auto's full-screen search. Tap a place to go, drag to scroll, or tap the bar in the card to type on the car's keyboard, where your saved and recent places are now listed until you type (thanks to yurividal)
- Voice search on Android Auto listens straight away: the microphone button on the car screen now starts listening as soon as it's tapped (through the car's microphone where the phone allows it), and lists the results in the search card. Allow the microphone first in Settings › Android Auto › Voice search on the car screen; until then the button opens Android Auto's search screen, which has a microphone of its own (thanks to yurividal)
- Settings card on Android Auto: the settings button on the car screen now opens a card beside the map with three pages (Driving, Map, Display), switches, choices you can see all at once and − / + for sizes, instead of one long list to tap through. Cars that don't pass taps on to the app keep Android Auto's list (thanks to yurividal)
- Backups can include your API keys: Settings › Backup has a new switch, Include API keys. With it on, the backup file holds your TomTom and Mapbox keys and Restore brings them back; with it off (as it starts), neither key is in the file. Before, the TomTom key was never backed up while the Mapbox one always was (thanks to yurividal)
- Home and Work buttons can each be left off: Settings › Places & trips › Home and Work under the search bar (or the car's own settings). It applies to the app and Android Auto alike (thanks to yurividal)
- Compact speedometer on Android Auto: an optional small dark pill with the limit sign and your speed side by side (Settings › Android Auto › Speedometer style on the car screen, or Speedometer style in the car's own settings). It's small enough to sit top right even on a short car screen (thanks to yurividal)
- Reorder your shortcuts: Settings › Places & trips › Shortcuts under the search bar now has a "Your order" list with up and down arrows to move each one

### Improved
- Settings back button stays put: on phones held upright, the back arrow and the page name now sit in a bar at the top of each settings page instead of scrolling away, and the settings fade softly at the top and bottom edges (thanks to yurividal)
- Fuel estimate on Android Auto: the route choice on the car screen now shows roughly what each route costs in fuel or charging, as on the phone (thanks to pc4096)
- Battery now, by 1%: the − and + by the battery on the route screen now go up and down 1% at a time, and keep going while held, instead of jumping in tens (thanks to Dry-Secretary2456)
- Clearer Plus description: Settings now says Horizon Maps Plus adds detailed 3D buildings and landmarks (the free version already has simpler 3D buildings), satellite and live traffic
- Free 3D hills: in the free version, hills and 3D terrain now come from free open elevation data (Terrain Tiles on Amazon's open data programme) instead of Mapbox
- Free search, more reliable: without keys, search looks in your downloaded country pack and saved places first, and only asks OpenStreetMap's free online search when they don't have enough, and not on every letter you type. Those free services ask apps not to search as you type, and could otherwise start turning the app away
- Horizon Maps Plus uses its data more sparingly: with Plus, traffic on your route is checked every 6 minutes (4 in queues) and faster routes every 8, one route service is asked at a time, and nearby incidents and the suggested-destination card refresh less often. Routes still follow the traffic. The app with your own keys is unchanged
- Clearer map while you look around: dragging or zooming the map with no route tucks away everything under the search bar, so you can see the map. Tap the search bar or its arrow, or re-centre, to bring it back (thanks to TouristCreepy1884)
- Street lamps on Android Auto: the car screen now follows your Street lamps setting, like the trees and 3D details (thanks to ChrisSantosGarage)
- New turn arrows: the arrows on the turn card are bolder, bend in a smooth curve and have a notched head, with the road you're leaving shown faintly behind them. Roundabouts show the other exits faintly too, with the exit number in the middle on a soft disc. The big lane arrows are unchanged. Android Auto's turn card gets the same arrows, including the roundabout with its real exit and number (thanks to karthikeyansr-lgtm)
- Turn card corners: slightly rounder again
- Add a stop: the Fuel, Charger, Coffee, Food, Services and Toilets buttons are now the same round coloured icons as under the search bar, in one row
- Headlight beam: about half as wide, so it lights the road ahead rather than the buildings either side (thanks to liamstears)
- Add a stop: now a panel down the side with your route still on the map, instead of a box over everything. Tapping Fuel, Charger, Coffee, Food, Services or Toilets lists those places along your route in the same panel, each with the time it adds and shown on the map, rather than adding one you hadn't seen. The arrow goes back. Search sits at the top, clear of the keyboard, and the map only zooms out while places along the route are listed
- Phone held upright, on a route: the turn card takes the full width and the map buttons sit underneath it, instead of being squeezed in beside it
- Phone held sideways: the big lane picture is lower on short screens, so the turn card no longer reaches down behind the trip card and hides the "Then" line (thanks to fahadfadi)
- Search with both: with a TomTom key and a Mapbox token, search now asks both at the same time and puts their results together (the same place listed once, the nearest exact match first). Before, Mapbox was only asked when TomTom found fewer than four places, so a nearer street of the same name could be missing (thanks to jvricardo1)
- Choosing another route with the phone held sideways: the Routes panel sits beside the buttons (scrolling if it's long), the turn card goes to one line on the other side, the speed, Re-centre button and trip card step aside, and the map fits the routes in between. The panel no longer covers the screen or the turn card (thanks to fahadfadi)
- Other routes: tap a route once to see it (shown in yellow on the map), then tap it again to switch to it. This works in the Routes panel and on the map's route labels (thanks to fahadfadi)
- Start navigation on phones: it gets the bigger share of the row and stays on one line, and Fly over route may use two lines, so long wordings (like Dutch) are no longer cut off (thanks to smikkelvriend)
- Choosing another route on a phone: with the phone held upright, Other routes now opens as a panel along the bottom instead of in the middle of the map. The turn card goes to one line, the speed signs, speedometer, Re-centre button and trip card step aside, and the map zooms to fit the routes above the panel. Head units and Android Auto are unchanged (thanks to smikkelvriend)
- UK road signs on the "Then" line: the road numbers in the turn card's "Then" line now show as the same blue, green and white signs, in their place in the sentence (thanks to karthikeyansr)
- UK road numbers as signs on the turn card: motorways in blue with white, A roads in green with yellow and B roads in white with black, as on the real signs. The turn card's corners are also a little less rounded (thanks to karthikeyansr)
- GPX tracks in backups: Back up and Restore now include your GPX tracks, with their groups, as well as your settings and saved places (thanks to bijindas)
- "Navigate" for GPX tracks: the button that drives a track now says Navigate instead of "Drive this" (thanks to bijindas)
- The moon in its real place: with "Sun in its real place" on (Settings › Light, weather & 3D), the night light and shadows now come from where the moon really is, as bright as it is full, and there are no shadows once it has set. Before, night shadows always came from the same side (thanks to dubrava4ever)
- Tidier Settings: While driving has its own page (road name, arrow, tunnels, media controls, locking), so Screen & sizes is shorter. Weather now sits with the light (Light, weather & 3D). Everything about the search bar is in one group, Under the search bar, and Map lettering is part of Colours and lettering. Settings search finds everything in its new place
- Choose which of your places sit next to Home and Work: each place in Settings › Places & trips › Your places has a star. Starred ones show in the row under the search bar, and a switch hides them all. Every place is still in search and the Your places shortcut (thanks to bijindas)
- Folders in Your places and Your tracks: the shortcut lists show your folders first, then what's in no folder. Tap a folder to open it, and ‹ goes back. This works on Android Auto too (thanks to bijindas)
- Fuel cost on each route: the route options show roughly what each route costs in fuel (or charging) on its own card. It used to sit at the far end of the Stops row, out of sight (thanks to pc4096)
- Update card: now styled like the What's new card, showing your version and the new one, and how many changes are new, improved and fixed. "What's in it" folds open to the headlines of everything since your version. It fits a short landscape screen too
- Trip card progress bar: the coloured bar now has rounded ends
- Recent places under the search bar: all of them show in the list, which scrolls, instead of three with an "All" / "Less" switch
- Headlight under the 3D car: the beam starts under the 3D car and no longer shines over its roof, and it no longer flickers. On a route it comes on once you first move off, then stays on to the end, even when you stop at lights; with no route it shows while you are moving (thanks to liamstears)
- Road name at the bottom stays put: driving without a route, the road name at the bottom no longer swaps with the bubble under your arrow every time you slow down. It stays for a minute after you stop (thanks to liamstears)
- Clearer traffic note when stopped: the note now reads "Showing traffic while you’re stopped" (thanks to liamstears)
- Turn card: the progress bar now sits a little way inside the card, so its rounded corners no longer hide the last few seconds. At a roundabout, the exit number is written large inside a bigger ring, on the turn card and in the "then" line, so the "then" line only needs the road name (thanks to karthikeyansr-lgtm)
- Add a stop from the trip card: the trip card has an add-stop button again, next to End route. It opens your stops, where you can add one on the way
- Saved places all in one row: your own places, favourites and saved routes are now all in the scrolling row under the search bar, which always ends with an "Add a place" tile. The list below it is just recent places
- Getting ready for Google Play: the build can now also make a Google Play edition of the app, called Horizon Maps so it's easy to tell apart (installed beside this one, updated by Play, without the self-updater). Nothing changes in this version
- Quicker rerouting: a wrong turn is now noticed on the first GPS reading when you're clearly on another road, and the new route is asked for twice at once (the early request and a fresh one from where you are), using whichever answers first. An early request made from too far back is no longer used, as it gave a route starting behind you (thanks to karthikeyansr-lgtm)
- Faster rerouting after a missed turn: the new route is now asked for as soon as you start to leave the old one, before it is certain you have, and with both a TomTom key and a Mapbox token both are asked at once and the first answer is used. It used to take 6 to 8 seconds (thanks to karthikeyansr-lgtm)
- Dutch wording: "Glazen panelen" is now "Transparante panelen", and the Screen and sizes summary says "transparantie" (thanks to hvdwolf)
- Saved places while driving: your own places and favourites now follow Home and Work in the row under the search bar, which scrolls sideways, so they are one tap away even while you drive and the Saved & recent list is folded away (thanks to Seali7Aut)
- Average speed checks: looked up on their own with a longer wait for busy servers, tried again sooner, and kept on your device, so a road you have driven before shows its check straight away, even with no signal (thanks to magnazan)
- Route choice on a phone held sideways: the route panel is now a column down the left with the routes shown beside it, instead of covering nearly the whole map
- Fly over route: the route panel steps aside while the flight plays, so you can see it, with a small Stop button to end it
- Android Auto follows your settings straight away: the map theme and your own colours, map detail, your arrow or car (its kind, model, colour and size), the road name and camera sizes, the 3D extras and the light now change on the car screen as you change them, instead of after reconnecting (thanks to yurividal)
- Speed position on Android Auto: the choices are now Bottom corner, Under the turn card and Top right, each against a card or a corner instead of floating half-way up the side beside Android Auto's own buttons (thanks to yurividal)
- Short car screens look like the app: on a short Android Auto screen the trip strip now has the route bar over its figures, showing how far along you are, and the speed sits bottom right, as on a phone held sideways. Split screen keeps the bare strip (thanks to yurividal)
- A smaller trip card on Android Auto: by default it now has just the button that ends the route. Route overview and Other routes are round buttons among Android Auto's own in the top corner, which fold away when they're not needed (the sound button gives way to them: Voice guidance is the first thing in the car's settings). Settings › Android Auto › Buttons on the trip card puts any of them back (thanks to yurividal)
- Top right speed position on Android Auto: the speedometer is now right in the top right corner of the car screen instead of part-way down and in from the edge. It makes way when a touch brings Android Auto's own buttons up, and comes back when they fold away (thanks to yurividal)
- Speedometer ring in the top right on Android Auto: the next speed limit now sits under the ring and the limit sign on its bottom left, so the ring itself stays up in the corner (thanks to yurividal)
- Compact speedometer: the next speed limit on the route is now inside the pill, after a thin line, instead of a second chip stacked on top of it (thanks to yurividal)
- Route choice on Android Auto: on most car screens it's now a slim column down the left, with the routes one under the other, so the routes on the map can be seen beside it. Across the bottom it covered nearly the whole of a short screen (thanks to yurividal)
- Android Auto has the app's look: the cards on the car screen now use the same colours as the map screen on the phone, and are see-through with Glass panels on (in Settings, under Screen), instead of plain solid cards (thanks to yurividal)
- Trip card on Android Auto: under the turn card, it's now the same width as the turn card, so the two line up (thanks to yurividal)
- One setting for where the speedometer goes on Android Auto: Settings › Android Auto › Card positions › Speedometer is now Bottom left, Bottom right, Under the turn card or Top right. There used to be a second one, "Speed position on the car screen", further down, and the first did nothing unless the second was on Bottom (thanks to yurividal)
- Fewer buttons on the Android Auto map: the 2D / 3D button is gone from the top corner. The view is changed in the car's settings, under Map (thanks to yurividal)
- Android Auto split screen: while navigating, the trip strip is a bar along the bottom edge of the map's tile, centred (thanks to yurividal)
- Android Auto's Stop button: left out while the trip card's red button is on the screen, on cars that pass taps on to the app, since both ended the route. "End route" is also the first row of the car's own settings (thanks to yurividal)
- Search on Android Auto: the list shows a spinner while it's searching, and says when nothing matched instead of "Searching…" for good. (thanks to yurividal)
- Arrival card on Android Auto: Done, Save place, Route back and Nearest parking can be tapped on cars that pass taps on (thanks to yurividal)
- Button size and Card size on Android Auto now also cover the trip card's buttons, the round shortcut buttons and the route choice cards (thanks to yurividal)
- Ready for Android 16: Horizon Nav is now built for Android 16 (as Google Play requires). On newer phones it keeps clear of the camera notch and, with Full screen off, of the status and navigation bars, exactly as before
- Map preview in Settings on a phone: on the map pages (Map style, Light & 3D, On the map, Your arrow) the live preview now stays at the top while you scroll the settings, as it does in landscape, so you can see each change as you make it

### Fixed
- Fuel and charging costs in rupees, kroner and more: the cost on the route options is now shown in the currency of the country you're driving in, and outside pound, euro and dollar countries the price box takes the price as normally written (₹107.40 a litre), where it used to read it as paise and show the cost 100 times too small (thanks to bijindas)
- Still navigating after parking: switching the car off within 400 m of your destination now ends the trip, on Android Auto and on the phone, even if you parked too far from the door for the arrival to be noticed. Starting the app again right by the destination no longer picks the trip back up either (thanks to pc4096)
- Music or radio stopping after directions: when a second direction came while the first was still being spoken, the app asked to lower the music again and never gave the first request back, so on some head units the music or radio stopped for good. The music is now always handed back once the voice has finished (thanks to adernev)
- Satellite view blank for some people: since late October Mapbox has been turning away its satellite pictures for many people ("too many requests"), even well inside their limits. If you have a TomTom key, TomTom's satellite pictures now take their place automatically, with Mapbox's roads and names on top. Without a TomTom key, the app still explains what's happening (thanks to atnr, Matattack99)
- Android Auto not following you: with no route running and the phone locked, Android could hold back location updates, so the arrow froze or stayed where you started. Location now keeps running the whole time the map is on Android Auto (thanks to timblr1989)
- Roundabout arrow: the arrowhead now sits clear of the ring on a short stem, so it reads as an arrow again, especially in the small icon on the "Then" line
- Numbers off-centre: the exit number in the roundabout icon and the number on the round speed limit sign sat slightly high; both are now exactly in the middle (thanks to karthikeyansr-lgtm)
- Add a stop on a phone held upright: the panel and the list of places along the route now sit along the bottom, with the places shown on the map above them (the list covered them). The first screen now has the same look as the list (the same solid panel, round buttons, tiles and blue), and neither lets the map show through the text
- Headlight beam with your own 3D model: no more odd dark patch round the car. The beam now starts at the front of your model instead of having a car-shaped box cut out of it that didn't match your model's shape (thanks to liamstears)
- Offline mode: the downloaded country map now shows. With Offline mode on, Mapbox's map engine was kept off the network so strictly that it couldn't load the downloaded map from the unit itself either, leaving a blank map with only street names and traffic lights on it (thanks to Johnm76)
- Phone held upright: the turn card no longer goes off the right of the screen as a turn comes up (it now grows evenly from the middle), and the map buttons sit lower, between the turn card and the trip card, with or without the Settings button above them
- What's new: an update with no new notes no longer shows a card saying "No release notes were found"; the card only appears when there's something new to tell you
- Arriving: a route that went right past your destination and round to come back (to arrive on your side of the road) is now spotted, and the plain way is used unless it's slower. And stopping within about 40 m of your destination now counts as arriving, however much route is left (thanks to Nubber99)
- Backup: Restore no longer closes the app on a phone or unit where the usual file picker is missing. It tries the older picker, and says so if neither is there. Back up gets the same protection (thanks to franciscoserranoadt)
- Media bar: the song's name now shows with more music apps; some send it in a different place, which left the bar saying just "Media" (thanks to bijindas)
- TomTom's map: when TomTom won't show its map with your key (even if the key still plans routes), Mapbox's map now shows instead with a note saying why. Before, the refusal was mistaken for a Mapbox token problem and no map showed at all (thanks to zedje85)
- Feedback reports no longer include your TomTom key or Mapbox token: they're blanked out wherever they appear, including in the address of a map that failed to load (thanks to zedje85)
- Fuel cost on the route options: a price typed in euros or pounds per litre (2.15 or 2,15) is now understood as well as cents or pence (215); it was read as 2.15 cents. The same goes for the electricity price (thanks to smikkelvriend)
- Settings: the on-screen keyboard no longer covers the box you're typing in; the settings scroll above it (Android 11 and newer, with full screen on) (thanks to smikkelvriend)
- Roundabouts on the car's own displays: Android Auto is now told which way each roundabout exit goes, so a car's head-up display or dashboard draws the right exit instead of always a right turn (thanks to desko1973)
- Your own 3D model: its glow is now the same size round it as round the built-in car (with some models it covered nearly the whole screen), and the headlight beam and road name under it are placed to fit it too (thanks to Matattack99)
- UK road signs: roads like A1(M) now show as one blue motorway sign instead of a green A1 with "(M)" after it, and a road number written with a space ("M 25") now gets its sign too (thanks to karthikeyansr)
- Own 3D models that were invisible: a model whose file scales itself down (common in models from Sketchfab and other sites) came out far too small to see. Models are now measured the way the map draws them, and ones picked before are measured again once (thanks to Matattack99)
- Says why satellite or your own model didn't show: when Mapbox's servers turn down satellite pictures (a problem on Mapbox's side since late October), or can't load your 3D model, a message now says so and what to try, and the feedback report includes Mapbox's reason (thanks to Matattack99)
- Satellite at night: with Night chosen (or after dark), satellite photos were dimmed until they hardly showed, so it looked as though satellite wasn't on. They now show in daylight at night (the screen's own night dimming still applies) (thanks to Matattack99)
- Your own 3D model with satellite: switching satellite on swapped the real 3D model for the built-in car picture. The model now shows on satellite too. Feedback reports also say which map is really on screen and what's drawing your arrow (thanks to Matattack99)
- Satellite view on Android Auto: the car screen now shows satellite view when it's switched on, instead of always staying on the drawn map, which made it look as though satellite didn't work. Feedback reports now also say whether satellite is on and the last error the map gave
- Night map by day on phones: with the phone's dark mode switched on, Auto kept the map dark all day. On a phone, Auto now follows sunrise and sunset, and a new "Phone's dark mode" choice matches the phone's own light / dark setting for anyone who wants that (thanks to lukaszsakul)
- Average speed checks on Android Auto: the average speed card (your average so far, the limit, how far is left and what keeps you under) now shows under the turn card on the car screen too, not just on the phone. While it shows, it takes the place of the speed camera line, and its colours are deeper on a light card (thanks to magnazan)
- Stop signs on side roads: the "Stop sign ahead" warning no longer goes off for stop signs on the side streets you pass, only for ones on your own road facing your way (thanks to dubrava4ever)
- Squashed settings: in languages with long words, a setting's name could be squeezed to one word or even one letter per line when it had buttons beside it. The buttons now move onto their own line underneath when there isn't room (thanks to fjanakovic)
- Time left easier to read in daylight: on a light card the green, amber and red time left are now deeper shades, so they stand out against white (thanks to magnazan)
- Arriving in a big car park: stopping for a minute within 250 m of your destination now counts as arriving, so the route ends when you park, not only at the shop's front door (thanks to pc4096)
- Routes left running in the background: a route still going after half an hour parked, with the app put away, now ends by itself, stopping the GPS (thanks to pc4096)
- Driving the route the wrong way: turning round, or setting off the other way along the route, now finds a new route in about 2 seconds. Your arrow follows you instead of freezing, facing backwards, with the map standing still (thanks to jvricardo1)
- Map not appearing: if the map still hasn't loaded after 25 seconds with no error, the app now checks with Mapbox itself and says what's in the way: a blocked server (private DNS or an ad blocker), the head unit's clock, the token, or the connection
- Arrow running into the junction: stopping at a junction no longer leaves your arrow out in the middle of the road you are turning into. The arrow slows with you and moves back to where you really are (thanks to jvricardo1)
- Night sky behind the camera notch: the starry fade at the top of the map now reaches the top of the screen, instead of starting below the notch with a hard edge
- Voice "add a stop" replaced the destination: asking the Assistant to add a stop while navigating threw the trip away and went to the stop instead (thanks to yurividal)
- Stops set back from the road: a stop more than 80 m from the road (a pin in the middle of a car park) was never counted as reached, so the route went back to it the next time it was worked out (thanks to yurividal)
- Google Play Protect no longer blocks installing: the media bar's song and artwork needed notification access, which Play Protect now blocks in apps installed from a file. The GitHub edition's media bar keeps back, play / pause and next without the song (thanks to desko1973)
- One headlight beam, from under the car: the beam is now a single cone at about half the brightness (it looked like two beams on top of each other), starting under the car so it seems to come from the headlights rather than the windscreen (thanks to liamstears)
- Headlight beam behind the car: at night the beam started well behind the arrow or car and shone back over it, as if the light came from nowhere. It is now drawn from the car's own spot on screen, so it comes out of the front and lights the road ahead, flat on the road at any tilt (thanks to liamstears)
- Shortcuts under the search bar can all be turned off: taking off the last one used to bring all five standard ones straight back. Now the row just goes away, and Reset brings the standard ones back (thanks to rookie2009)
- Arrow on the wrong side of a divided road: with no route running, the arrow could jump to the other carriageway when the GPS drifted towards the middle, as if driving against the traffic. It now keeps to the side going your way, even before the speed-limit roads have loaded (thanks to leedank93)
- 3D buildings switch: turning 3D buildings off left landmarks and building fronts standing, and Android Auto's map kept all its 3D buildings and trees whatever the settings. Both now follow the switches (thanks to VITMAD1964)
- Missing translations: in 20 of the languages (including Swedish, Turkish, Arabic and Kannada), the newest settings such as Groups and Top speed for arrival times were still in English. They are now translated
- Favourites in settings: a favourite's name and address no longer squeeze into a one-letter column beside its buttons on a phone; the buttons sit on their own line under it (thanks to yurividal)
- Full screen round the camera: on phones with a camera notch (such as the Samsung S24 Ultra), full screen left a black band across the top. The map now runs right up round the camera, while the cards and buttons stay clear of it
- Day and night on a phone: Auto now simply follows the phone's own light or dark setting. For the map to change at sunrise and sunset instead, pick Sun times
- Rain on the car screen: a patch between the turn card and the trip card stayed free of rain and snow. The weather now covers the whole map
- Black map after Settings or the demo: now and then only the top part of the map showed and the rest stayed black until the app was restarted. The map now always fills the screen again
- Speed limit sign off the edge: with the speed set to the right on a left-hand-drive layout, the limit sign could sit off the edge of the screen. It now always sits on the side facing the middle
- Headlight beam: the beam now shines from the front of your arrow, rather than starting underneath it
- Route choice on a phone: with Fly over route and Demo both on, they now share a row of their own, so Start navigation is no longer squeezed into a column of broken words; the route labels stay on one line, and the "Starting from" pill no longer covers the buttons
- Your arrow in the camera strip: since the Android 16 update, part of the arrow could show in the black strip at the top of the screen on phones with a camera notch. It now stays on the map
- What's new after skipping an update: the card no longer shows stray text like "(567, ### 1.0.567" between the notes of each version
- Speed position on the car screen (Settings › Android Auto on a phone): the choices ran off the side of the screen, so Top right couldn't be seen without swiping the row. They now wrap onto a second line (thanks to yurividal)
- Alerts on Android Auto: an alert beside the turn card (a sharp bend, a camera) could overlap the turn card's edge. It now starts where the turn card ends (thanks to yurividal)
- Arrival card on Android Auto: it now comes up top left, where the turn card was, instead of the middle of the map (thanks to yurividal)
- Other routes on Android Auto: the button only zoomed the map out to the grey routes. It now also opens the routes box, as in the app, listing your route and each other way with its time, arrival and how it compares; tap one to switch. The other cards step aside while it's open, so the routes on the map can be seen (thanks to yurividal)
- Shortcuts under the search bar on Android Auto: the car screen always showed the same fixed buttons, whatever was picked and in whatever order in Settings › Places & trips. It now shows yours, in your order (the first four on a short car screen) (thanks to yurividal)
- Arrival card on a short car screen: it ran off the bottom, squashing "Nearest parking" and losing the buttons. It's now lower, and drawn smaller when it still wouldn't fit (thanks to yurividal)
- Leaving Android Auto's split screen: the cards and your arrow could stay in the tile's half of the screen until the next turn. They now go back as soon as Android Auto gives the map the whole screen (thanks to yurividal)
- Search on Android Auto: a search spoken or typed just as Android Auto's voice input came up could be dropped, leaving the words in the search box and an empty list, so it had to be said again (thanks to yurividal)
- Speed position on Android Auto did nothing on a short car screen, or with the trip card on the right: the speedometer stayed bottom left whatever was picked (thanks to yurividal)
- Shortcuts in Settings on a phone: the buttons now wrap onto as many lines as they need, instead of the last one being squashed into a column of single letters

## 1.0.559 — 15 Nov 2026

### Fixed
- Camera alerts in France, Germany and Switzerland: the message saying they're switched off there now shows only the first time in each country instead of every time the app opens, and names the switch that keeps them on ("Off where they're banned" in Settings › Alerts)

## 1.0.557 — 15 Nov 2026

### Fixed
- Signing key: Horizon Nav updates are now signed with a new key kept only in GitHub Secrets, with Android's key rotation so it updates over the current version as normal

## 1.0.554 — 15 Nov 2026

### New
- Arabic, Hebrew and Urdu: the whole app, the spoken directions and the voice commands, read right to left. The inside of the turn card, trip card, search and settings is laid out right to left, while where the cards sit on the map still follows your driving side

## 1.0.552 — 15 Nov 2026

### New
- Turkish and Serbian: the whole app, the spoken directions and the voice commands are now in Türkçe and Srpski (Latin script). Pick them in Settings › Sound & voice › Language, or leave it on Phone's language

## 1.0.550 — 15 Nov 2026

### Fixed
- Always day on Android Auto: the car screen's own Always day / Always night now wins. With the phone set to Night (or to follow a dark phone), picking Always day on the car still left the car's map dark.
- Average speed checks shown as plain speed cameras: the cameras at the start and end of a check are now announced as the average speed check, instead of a "Speed camera" alert that hid it, and the check is warned about as early as a camera would be. If the search along the route fails on a busy server, the checks are now looked up on their own so they aren't missed.

### New
- Landmarks in directions: when a turn is at traffic lights or a stop sign, the turn card says "At the lights" (with the distance and road below) and the voice says "At the traffic lights, turn right onto…", or "At the second set of lights" when there's one set before it. With more lights on the way it says the distance as before. Turn it off in Settings › Sound & voice › Landmarks in directions
- Your own 3D model: Settings › Your arrow › Real 3D model › Your model loads a .glb file of your own (up to 8 MB) as the arrow, sized to match the built-in car whatever scale it was made at, with Turn to point it the way you drive and the size slider to fine-tune it. If it can't be shown, the built-in car stands in. Works on Android Auto too.

### Improved
- Trip card: the voice, sound and stops buttons are gone from the trip card, leaving just End (and chargers in an electric car). Sound is now a big button at the top right while navigating, and Voice and Stops are in Map options › This trip

## 1.0.547 — 14 Nov 2026

### Fixed
- Average speed check limits outside the UK: a check's limit was always read as British, so one mapped as "60" (km/h) showed as 95 km/h. It's now read in the units of the country the check is in, and the card never shows a limit higher than the road's own signed limit.
- Disappearing from Android Auto after an update: updating from inside the app now asks Android's installer to record Google Play as the installer, which is what Android Auto checks before listing an app from outside the Play Store. Before, an in-app update could quietly take Horizon Nav out of Android Auto (found by marcocebrian). Problem reports now include this install record.

## 1.0.545 — 14 Nov 2026

### Fixed
- Disappearing from Android Auto after an update: updating from inside the app now asks Android's installer to record Google Play as the installer, which is what Android Auto checks before listing an app from outside the Play Store. Before, an in-app update could quietly take Horizon Nav out of Android Auto (found by marcocebrian). Problem reports now include this install record.

## 1.0.543 — 14 Nov 2026

### Fixed
- Speed cameras along the route: the cameras on a planned route now show along all of it while you choose the route or start a demo, not only within 10 km of where you are. A stretch of road whose cameras couldn't be looked up (a busy server) is now tried again, instead of being skipped for the rest of the trip.

### New
- Hungarian: Horizon Nav can now be used in Magyar, chosen in Settings or picked up from the phone, with spoken directions and voice commands.

## 1.0.541 — 14 Nov 2026

### New
- Malay: Horizon Nav can now be used in Bahasa Melayu, chosen in Settings or picked up from the phone, with spoken directions and voice commands.
- Croatian: Horizon Nav can now be used in Hrvatski, chosen in Settings or picked up from the phone, with spoken directions and voice commands.

## 1.0.539 — 14 Nov 2026

### Fixed
- Headlight beam: no longer flickers or vanishes for a moment, and no longer slows the map. It now moves with the arrow the way Mapbox moves its own location marker, instead of being redrawn up to 15 times a second.

## 1.0.537 — 14 Nov 2026

### Fixed
- Closing on start: 1.0.535 closed straight away on some Android 13 head units (Unisoc), because of the new spoken roundabout exits. It opens again.

## 1.0.535 — 14 Nov 2026

### New
- Simplified Chinese, Slovak and Danish: Horizon Nav can now be used in 简体中文, Slovenčina and Dansk, with spoken directions and voice commands in each. Chinese phones now pick Simplified or Traditional by themselves.

### Improved
- Removing a report: a report made by mistake can be taken back with Undo on the "saved here" message, and tapping one of your reports on the map (phone or Android Auto) offers Remove or Keep for just that one.

## 1.0.532 — 14 Nov 2026

### New
- Greek, Czech and Finnish: Horizon Nav can now be used in Ελληνικά, Čeština and Suomi, chosen in Settings or picked up from the phone, with spoken directions and voice commands in each.

## 1.0.530 — 14 Nov 2026

### New
- Romanian, Basque and Traditional Chinese: Horizon Nav can now be used in Română, Euskara and 繁體中文, chosen in Settings or picked up from the phone. Directions and voice commands work in all three, and on a Hong Kong or Macau phone the Chinese voice speaks Cantonese.

## 1.0.528 — 14 Nov 2026

### Fixed
- Streets missing from the map (GitHub #78, Reddit): in some countries Mapbox's HD roads hid the ordinary streets, leaving only main roads. HD roads (lane markings, turn arrows, 3D overpasses) are now a setting under Settings › Map style, on by themselves only in the UK; elsewhere switch them on if they show properly.
- Speed limit rules abroad: away from the UK, the app worked out the usual limits from the phone's language region, so an English (UK) phone in France got British rules. It now uses the country you're actually in (GitHub).

### New
- Card positions: Settings › Screen and sizes › Card positions puts the turn card top left or right, the trip card under it or bottom left, centre or right, and the speedometer bottom left or right, with a live picture as you choose. On Android Auto (Settings › Android Auto): the trip card and speedometer (GitHub).
- Start from here: press and hold the map, then Start from here, and routes are planned from that spot instead of your GPS position (a "Starting from …" pill shows until you tap it, or until you drive off with a GPS fix) (GitHub #88).

## 1.0.524 — 14 Nov 2026

### Fixed
- Searching by name or address with a TomTom key: if TomTom's classic search refuses the key (newer keys can come with only the newer "Orbis" APIs), the same search is now tried on TomTom's Orbis service, so shops, places and addresses are found again instead of only postcodes. If TomTom refuses both, the app now says why once, instead of quietly showing thin results.

### New
- Sharp bend warnings: bends that need slowing for are striped yellow with black chevrons on the route, and about 9 seconds before each a yellow chevron sign says "Sharp bend · take it at 25 mph" with a chime (only when you're going faster than that). Worked out from the route's shape; junction turns don't count. Settings › Warnings › Sharp bends.
- Headlight beam: after dark, a soft cone of light lies on the road ahead of your arrow while you're moving, longer at speed. Settings › Your arrow › Headlight beam at night.
- Destination lit up on arrival: the building stays lit while the arrival card is up, its entrance (the side nearest where you pulled up) gets a marker, and a dotted line shows the walk from the suggested parking. Settings › Arrival › Light up your destination.

## 1.0.522 — 13 Nov 2026

### Fixed
- Speedometer on short Android Auto screens: it now stays put in the bottom-left corner, with the one-line trip strip beside it while navigating. In the bottom right it slid with Android Auto's zoom buttons, covered the Mapbox logo and moved up the side to make room for the trip strip (GitHub #67).

## 1.0.520 — 13 Nov 2026

### Fixed
- Arrow back in the middle with the cards folded: on a wide screen, with Home, Work and Saved & recent folded away (just the search bar left), the arrow now goes back to the middle, as in Waze; it stayed off to the side because the search bar on its own still counted as a card beside it (GitHub #79).

## 1.0.518 — 13 Nov 2026

### Fixed
- Rain and snow on Android Auto: Weather effects now show on the car screen too (they were only ever drawn on the phone), along with the weather haze towards the horizon.
- Rain effect missing while it rained: the effect went by the hour's weather summary, which often says "cloudy" during showers. It now also checks the rain and snow actually falling in the current quarter hour, and looks every 10 minutes instead of 20.

### New
- Demo drive controls: while a demo plays, the map shows its speed (tap to switch 1×, 2× and 4× without restarting), a skip-to-the-next-turn button (jumps to about 250 m before the next junction) and a stop button. On Android Auto: End demo, the speed, Skip turn and Settings.
- Demo on the route choice: with the demo drive switched on, a Demo button sits beside Start; choose it and Start (or the countdown) begins the trip as a demo drive. On Android Auto too.

## 1.0.516 — 12 Nov 2026

### Fixed
- Lane arrows at back-to-back roundabouts: on the way into the first of two roundabouts close together, the lane picture showed the second one's lanes. Each turn now only shows its own lanes.

## 1.0.514 — 12 Nov 2026

### New
- Keep the arrow to the side: a new switch (Android Auto settings, and Settings › Screen & sizes › While driving) keeps the arrow off to the side on a wide screen when the cards fold away, instead of gliding back to the middle.

### Improved
- Chime volume: on the head unit the speeding, limit-change and camera chimes now play on the same channel as the spoken directions (they were on the music channel at full strength), so the head unit's volume turned during a direction turns them down too. Settings › Sound & voice › Chime volume sets them against the voice, with a sample beep.

## 1.0.512 — 12 Nov 2026

### Fixed
- Headlights mode on Teyes and FYT-type units: these units pass the lights on in their own setting instead of switching Android's night mode, so Headlights never went dark. It now reads that too, with a "Lights signal is reversed" switch for the few units that have it the other way round.

### New
- Slovenian: the whole app, spoken directions and voice commands are now in Slovenian (Settings › Sound & voice › Language, or it follows the phone) (GitHub #75).

## 1.0.510 — 11 Nov 2026

### New
- Trip bar on the driver's display: with a route, the map behind the steering wheel shows time left · distance · arrival in a small bar at the bottom centre, as Google Maps does.

## 1.0.508 — 11 Nov 2026

### Fixed
- Wrong-way warnings on divided roads: a GPS drifting towards the middle no longer puts the arrow on the other carriageway or warns that you're going the wrong way. The warning now needs a sharp fix, no road your way nearby, and is never given while you're following your route.
- "Going to …?" suggestion: switching off "Suggest where you're going" now hides the habit suggestion too (GitHub #73).

### New
- Android Auto matches the phone: Day and night on the car now has "Same as your phone", so the car screen goes light or dark with the phone (GitHub #69).

### Improved
- Driver's display map: the map behind the steering wheel is zoomed further out (and further still at speed), the arrow follows the car's Arrow size, and the car settings have a new "Driver's display zoom" (Closer to Furthest out).

## 1.0.506 — 11 Nov 2026

### Fixed
- Traffic pill position: the "Traffic round you" pill sits at the top between the turn card and the map buttons when there's room, and otherwise just above the arrival time card instead of over it.

## 1.0.504 — 11 Nov 2026

### Fixed
- Trip card showing through the add-a-stop panel: the time, arrival and distance card now steps aside while the add-a-stop (or all locations) panel is open, and comes back when it closes. Searching a shop also leaves out places that only mention it (a Western Union counter inside a Sainsbury's).
- Android Auto on small screens and split screen (#67): the car screen now picks its layout from the space Android Auto leaves for the map instead of shrinking everything. On a short screen (like 800 × 480): a smaller search bar, Home and Work as slim pills, one row of shortcuts plus ⋯, icon-only Report and Places buttons, a narrower turn card with the lanes inside it, a one-line trip card and a smaller speed sign, laid out side by side so nothing overlaps. In a split-screen tile: just the turn, a one-line trip strip and the speed.

## 1.0.500 — 11 Nov 2026

### New
- Add a stop, seen along your route: when adding a stop, "See them along your route" (or the keyboard's search key) shows every match on the map along the rest of the route, each labelled with the time it adds, in a panel sorted by Least detour, Soonest or Nearest, with how far ahead and how far off the route each one is. Tap one, then Add as a stop. On Android Auto it's a list sorted by detour.
- Bulgarian: the whole app, spoken directions and Android Auto, in Settings › Sound & voice › Language (or follows the phone). Voice commands work in Bulgarian too. Spoken directions need a Bulgarian text-to-speech voice installed (Google's has one).

## 1.0.498 — 11 Nov 2026

### Fixed
- "Traffic round you" over the search box: the stopped-in-traffic view now only comes on after you've been driving (not with the phone lying still, e.g. when sharing a place from another app), its note sits at the bottom where it can't cover the search box, and tapping it (✕) goes back to the normal view.

## 1.0.496 — 10 Nov 2026

### Fixed
- Mapbox logo halfway down the car screen: on Android Auto it was pushed down by a gap meant to clear the phone's top bar, made bigger by the car display's high density. It now sits in the corner.

## 1.0.494 — 10 Nov 2026

### Fixed
- Wrong side on arrival: "your destination is on the right" for a place on the left. The side is now worked out from where the place actually is against the way you're driving, and the route service's own left / right is only used when that's too close to call.
- Speed limit sign squashed on Android Auto: the US and Canadian board's words are sized from the sign itself, so a larger car-screen text size can't squash them; the board is a little bigger next to the speedometer; and the next-limit tag above no longer overlaps it, and shows the board instead of a round sign in the US and Canada.

## 1.0.492 — 10 Nov 2026

### Fixed
- Your own locator picture on Android Auto: the car screen now shows your picture too, instead of the arrow.

### New
- Delay labels on queues: each queue of a minute or more ahead on your route gets a small "+8 min" pill where it starts, red for heavy traffic and amber for slow. While choosing a route, the other routes' queues show too, dimmed. Each one goes once you're past it.
- Speed limit changes, your way: Settings › Alerts › Speed › How: chime and voice, voice only, or chime only. With both, the voice now waits for the chime to finish so it isn't lost under it.

### Improved
- Arriving on your side without going round the block: when arriving on the kerb side of your destination would mean an extra loop of turns at the end, the route is checked without it, and the simpler one is used when it's clearly quicker or shorter (e.g. straight on and turn across into the drive).
- Street lamps where they really are: the lamps on the map now come from OpenStreetMap online too (not only from an offline pack), so real lamp positions show wherever people have mapped them. Guessed lamps fill in only the gaps, and roads OpenStreetMap marks as unlit stay dark. In cities with every lamp mapped, guessed ones only go on roads marked lit. Fetched once per area, kept for a month, never in offline mode.

## 1.0.488 — 10 Nov 2026

### New
- Prefer simpler routes switch: Settings › Routes › Choosing a route. When a route is only a minute or so slower but has far fewer turns, it's picked first, and it's now labelled Simplest (it used to say Shortest, which made it look like the app defaulted to the shortest route). Switch it off to always start on the fastest.

## 1.0.486 — 10 Nov 2026

### Fixed
- Crash tapping a place on the map: tapping a place that has no name in Mapbox's data closed the app. Nameless places are now ignored, and anything else going wrong in a map tap can no longer close the app.

## 1.0.484 — 10 Nov 2026

### Fixed
- Lane picture under the turn card on Android Auto: on a short car screen it ran on underneath the trip card. It now shrinks to fit the gap, and is left out when even a small one wouldn't fit (the lanes still show in the turn card).

### New
- Choose the trip card buttons on Android Auto: Settings › Android Auto › Buttons on the trip card turns Overview, Other routes, Sound and End on or off, with a Normal, Large or Extra large button size. Fewer buttons make the card narrower, so more of the map stays clear.

## 1.0.480 — 10 Nov 2026

### Fixed
- Slip-road turns count down to the slip road: when the turn is at the far end of a slip road (common where a main road has no exit numbers), the turn card, the lit lane and the voice now count down to where the slip road leaves the main road ("take the exit on the left"), then to the turn at the end of it. They used to count down to the far end, past where you had to move over.

## 1.0.478 — 10 Nov 2026

### New
- Thai, Japanese and Indonesian: the whole app, spoken directions and Android Auto, in Settings › Sound & voice › Language (or follows the phone). Voice commands work in all three too. Directions are spoken with the phone's or head unit's own voice for that language, so it needs a Thai, Japanese or Indonesian text-to-speech voice installed.

## 1.0.476 — 9 Nov 2026

### Fixed
- Dashboard and head-up display in feet: on Android Auto, the car's own dashboard and head-up display now show feet close to a turn (then miles), the same as the app, instead of yards.

### New
- Save route: the bookmark on the route options is now Save route. It keeps the destination, your stops and the road you picked ("Croke Park via M9"). Saved routes show under the search bar (and in Places on Android Auto); tap one to plan the same way again with that day's traffic, swipe it away to delete.
- Red pin where you tap: tapping or holding the map drops a red pin on the exact spot while its card is open.

### Improved
- Send to car sends the whole trip: from the route options it now sends your stops and the road you picked too, not just the destination. The car's card says how many stops are coming.
- Headlights mode follows only the lights: with Day and night set to Headlights, the map goes dark in tunnels and back to day straight after, without the sunset and sunrise tint. Settings shows whether your head unit has been seen switching with the lights, and what the lights signal says right now. The Android Auto screen works the same way when it follows the car.

## 1.0.474 — 9 Nov 2026

### Fixed
- Tapped spot hidden behind route options: on a phone, the card for a spot you tap while choosing a route (Go / Add as a stop) now shows on top of the route options instead of behind them.

## 1.0.470 — 9 Nov 2026

### New
- See all locations: search for a shop or chain (Lidl, Sainsbury's, Costa…) and, when there are several nearby, "See all N locations" shows them all. A panel lists them by Quickest (drive time with today's traffic), Nearest or On my route, and the map zooms out to show each as its logo with the drive time. Tap a pin or a row, then Go or Add as a stop. "Search this area" looks again wherever you move the map. On Android Auto it's a list.
- Logos in search results: shops and brands show their own logo (taken from their website, or a built-in list of common chains). Logos are kept on the unit after the first time, and the usual pin shows without a signal or in offline mode.

## 1.0.464 — 9 Nov 2026

### New
- Your own shortcuts: choose which round "find nearby" buttons show under the search bar (up to 5, in your order) from Fuel, Charging, Food, Coffee, Parking, Toilets and Services, in Settings › Places & trips. Reset brings back the usual ones.
- Offline mode: a master switch in Settings › Offline maps. Nothing goes online and the app stops looking for a signal, so there is no more flickering and retrying with a weak or missing connection. Everything comes from the downloaded pack. Downloads you start yourself still work.
- Add a stop while planning: hold on the map on the route options screen, then tap "Add as a stop" on the card. It goes on the route being planned, and you can add as many as you like.

## 1.0.462 — 8 Nov 2026

### Fixed
- Traffic colours that never faded: a jam or incident painted on the route line could stay red after it cleared, as each refresh was built on the already-painted line. Each refresh now starts from the route service's own traffic and adds TomTom and incidents on top.
- Missed-turn detection: made safer against GPS drift in town. It only triggers beyond the GPS's own error and at a sharper angle, so approaching a turn never sets off a reroute.
- Postcode district search: a district on its own ("M25", "B1") is also how roads are named, so it now shows third, and a search for the M25 still shows the motorway first.
- Route overview on Android Auto: the route now fills the screen instead of sitting in the top third (it left room for a panel that isn't there on the car), so it's zoomed in further and the place names are easier to read.

### New
- Italian, Portuguese and Polish: the whole app (screens, Android Auto, spoken directions, alerts and spoken commands) is now also in Italiano, Português and Polski. Portuguese uses the Brazilian voice on a Brazilian phone.
- Request a language: Settings › Sound & voice › "Missing your language?" opens a GitHub issue filled in with the language wanted, whether the person can help check the wording, and their phone's language.
- Languages (part 3c, the last part): the whole app is now in Dutch, German, French and Spanish. This adds the first-time setup, the Offline maps page, the donate and What's new screens, and the settings search (search in your language or in English). Spoken commands work in each language, such as "naar huis", "nächste Tankstelle", "signaler accident" and "ir a Madrid", with voice search listening in that language. Lorry height and weight warnings are spoken in it too.
- Languages (part 3b): Settings are now in Dutch, German, French and Spanish too. That covers every page, option, description and button, the Custom theme and Your themes, and fuel, Android Auto, keys, feedback and backup. A setting found with the settings search is still highlighted in any language.
- Languages (part 3a): the phone's main screens are now in Dutch, German, French and Spanish too. That covers the map and its buttons, map options, search, Home and Work, saved and recent places, route choice, stops, plan-ahead, arrival, nearby lists, place cards, the alert banners, the update and Mapbox key screens, ETA sharing and the app's pop-up messages. Changing the language updates the screen straight away.
- Languages (part 2): the Android Auto screens are now in Dutch, German, French and Spanish too. That covers the map buttons, trip card, route choice, Places, Search, Report, Other routes, nearby lists and car settings. The alert banners (cameras, limits, incidents, zones and the rest) are translated on the phone and in the car.
- Languages (part 1): spoken directions, voice alerts and the turn card in Dutch, German, French and Spanish, as well as English. Set in Settings › Sound & voice › Language ("Phone's language" by default). Route instructions and search results come in the same language. The rest of the app follows in the next updates.

### Improved
- Custom theme live preview: every change shows on the preview at the top as you make it, even while a slider is still moving. Nothing is kept until you press Save. Leave without saving (or press Undo changes) and the map goes back to how it was. Giving it a name also keeps it under Your themes.

## 1.0.458 — 8 Nov 2026

### Fixed
- Missed turns noticed sooner: driving straight past a turn, the arrow no longer carries on round the turn you didn't take. It follows you straight away and a new route is asked for in about 2 seconds (it used to wait until you were 40 m or more off the route).

## 1.0.456 — 8 Nov 2026

### Improved
- Traffic from both services: on a Mapbox route with a TomTom key, TomTom also times the same road at every traffic refresh. Each stretch of the route line shows whichever service says it's worse, and the time left and arrival time use the longer of the two, so a jam either one knows about is counted.
- Time left in queues: traffic is refreshed every 2 minutes instead of 3 while you're crawling along, so the arrival time catches up with a jam sooner.
- Simpler routes: when planning, a route with clearly fewer turns is chosen over one that's only up to about a minute and a half quicker, so it keeps to the main roads instead of cutting through back streets. The quicker one is still offered to pick.

## 1.0.454 — 8 Nov 2026

### Fixed
- Speeding beep on Android Auto: now plays on the navigation channel (like the spoken directions), so it is heard over the radio or the car's own music too. Before, it only reached the speakers while Android Auto was the sound source.

## 1.0.452 — 8 Nov 2026

### Fixed
- Lanes at two slip roads close together: the second junction's lanes no longer light up at the first one, which made it look like the exit to take.
- Lanes lit too generously: where Mapbox picks the lanes to be in, only those are lit now, not every lane that could work.

### New
- Postcode search: typing a UK postcode now shows the postcode itself at the top of the results (not just addresses in it), on the phone and Android Auto. A half-typed one ("SO17 1") lists the postcodes it could be, and a district ("SO17") works too.

## 1.0.448 — 8 Nov 2026

### New
- Battery estimate toggle: electric car drivers can hide the battery % on the trip card (phone and Android Auto) in Settings › Fuel › Your electric car.

## 1.0.446 — 8 Nov 2026

### Fixed
- Next speed limit: no longer shows a strange "0 in 700 m" sign above the speed when the road ahead has no known limit.
- Car turn card: the "then" line for the turn after next now shows on small Android Auto screens too.
- Pinch zoom on Android Auto: zooms smoothly around the middle of the map instead of jumping about.

## 1.0.444 — 7 Nov 2026

### Improved
- Android Auto split screen: in the narrow part of the screen the app's cards and buttons now shrink further by themselves, so they fit beside the map. The Overall size setting (Settings > Android Auto, or the car's own Settings) still sets them bigger or smaller, 40% to 300%.

## 1.0.442 — 7 Nov 2026

### Fixed
- Small Android Auto screens (GitHub, Pioneer SPH-DA160): on 7-inch 800 x 480 units the turn card left out the lanes, the junction close-up and the driving advice, as if the screen were too small. Lanes now always show on the car's turn card, and the close-up and advice show unless the screen is really short.
- Services on the route bar with the services card off: switching off Next services card now takes them off the route bar too.

### Improved
- Android Auto: Route overview and Other routes buttons on the trip card (beside sound and end), and Route overview in the car's Settings too.
- Android Auto arrow size now goes down to 30% (was 60%), for small car screens where even 60% looked big.

## 1.0.438 — 7 Nov 2026

### New
- 3D flags: a tall red flag at where you're going, and smaller amber ones at your stops, standing above the buildings so you can see them from a distance. On by default; Settings > Your arrow & route line > Route line.
- Hill shading: soft shading on hillsides, so valleys and ridges show even on a flat map, with its own day and night shades. Off by default; Settings > Light & 3D > 3D.
- What's here: holding on the map now shows a card first: the road as its own sign (blue motorway, green A road, white B road), a mini speed limit sign, what the land is and the area with their own icons and colour, how far it is from you, and Go (or Add as a stop while driving) and a star to save it. On by default; switch it off in Settings > On the map > Holding on the map to go straight there as before.

## 1.0.436 — 7 Nov 2026

### New
- Arrive on your side of the road: routes bring you to the kerb side of where you're going, so you don't have to cross the traffic (Mapbox and offline routes). On by default; Settings > Routes > Choosing a route.
- Next speed limit ahead: a small sign above the limit shows the next limit on your route and how far it is, from about half a mile away. On by default; Settings > Alerts > Speed.
- What's here: holding on the map now shows the road, its speed limit and the area first, with Go (or Add as a stop while driving). On by default; switch it off in Settings > On the map > Holding on the map to go straight there as before.
- Junction pictures: Mapbox's pictures of motorway junctions, with the lanes to use, in the last kilometre before one. Off by default (Settings > Turn card & lanes > Lanes and junctions); they only show where Mapbox has them and for accounts it offers them to.

## 1.0.434 — 7 Nov 2026

### Improved
- Horizon colours: now a cool moonlight blue at night (ground, buildings and the whole map's tint), so it's clearly different from Twilight's purple.

## 1.0.432 — 6 Nov 2026

### New
- Custom theme: a Custom tile in Settings > Map style > Colours. Pick the theme it starts from, then change the ground, water, parks, roads, main roads and buildings, and the route line's colour, separately for day and night, plus the route line's width and the night brightness. The live preview at the top shows the Day or Night colours you're editing. Save it under a name (Your themes) to pick again later, and reset a part, a day or night, or everything. It starts as exactly the theme you had, so nothing changes until you change something.

## 1.0.430 — 6 Nov 2026

### Fixed
- Traffic jams not added to the route: a jam could be announced ("Traffic jam ahead") without the time left going up or the route line turning amber or red. TomTom's incident details were read wrongly (the kind of incident as a word when it's a number, and the delay under the wrong name), and a jam with no delay given was skipped. Now every jam ahead colours the route (amber, red when it's major), and its hold-up is added to the time left when the route's own traffic doesn't already have it.
- TomTom roadworks and closures not recognised: the same misreading meant TomTom's closures and roadworks weren't known as such, so they weren't avoided or shown round about. Fixed with the above.

## 1.0.428 — 6 Nov 2026

### Fixed
- Freezing head units not helped: when the map locked the screen up and Android closed the app (the unit itself kept running), it didn't count, so the map's drawing was never turned down for that unit. A lock-up now counts, and turns the drawing down a step straight away at the next start (Lite map at a lower sharpness, then Lightest), with a message saying so.

## 1.0.426 — 6 Nov 2026

### New
- More map colours: Ember (warm and dark), Mono Night (greys, with only the route and warnings in colour), Forest, Arctic, High Contrast, Sepia, Neon, Pastel, and Match my car (a hint of your car's interior lighting colour, with how strong). In Settings > Map style > Colours.
- Light and weather: a new group in Settings > Light & 3D, with Night brightness, Moonlight at night (moved here) and its Moon strength, Shadows on or off, Sun in its real place (by day, light and shadows from where the sun really is), Overcast light (soft daylight, no hard shadows) and Weather light (greyer light when it's raining). Everything starts as before.
- Moonlight at night: a switch in Settings > Light & 3D > Light and weather. Like dusk, but from the moon: at night the map gets a low, pale blue light from one side with long soft shadows, in place of Mapbox's flat night light. The night sky, street lamps and your colours stay. Off to start with.
- Twilight map colours: a new choice in Settings > Map style > Colours: the deep purple-blue night, more purple than Horizon (its ground and buildings lavender at night).
- Tilt settings: Settings > Light & 3D > 3D view sets how far the 3D map tilts, separately for a wide screen, a tall screen and Android Auto (30° to 65°, or Auto as before).
- Default zoom: set your own normal zoom for when you're not on a route. Zoom with + and − on the map and tap "Make this my default", or use the slider in Settings > Map style, where Reset puts it back. Auto zoom still pulls out at speed and moves in at junctions, round your setting.

### Improved
- Settings reorganised: Map look is split into Map style (colours, day and night, map detail) and Light & 3D (light and weather, 3D, tilt, sky), Glass panels and the Mapbox logo position move to Screen & sizes, and Feedback has its own group at the top of About & backup. Settings search finds everything in its new place, and settings open where you left them.
- Live map preview in settings: the map style, light, on-the-map and arrow pages show the real map at the top, so changes show as you make them, with Day / Night buttons to check the night look in daylight.
- Pictures in settings: the lane styles, where the speed display sits, and the arrow styles are shown as small pictures to pick from.
- Other routes: picking a route (or closing the Routes box) goes straight back to following you on the map.
- Behind the scenes: the light and weather settings only ever change the map from the right place in the app (a change from the car screen could have come from elsewhere and upset the map), and stop cleanly when the map closes; the driver's display, like the main car screen, now stays open when Android resizes it; the Routes box shows "Looking" while a check already under way finishes, rather than "No other routes".

## 1.0.424 — 6 Nov 2026

### New
- Other routes button: while navigating, a button on the map (under map options) folds out a Routes box, as in Google Maps: your route and each other way with its time, how it compares, arrival time, distance, the road it goes by and the traffic. Tap one in the box or on the map (where they're shown in grey) to switch. On Android Auto it's the first row in Settings, as a list you can pick from with a touch or the knob.

## 1.0.422 — 6 Nov 2026

### Fixed
- Routes through closed roads: a road closure on your route ahead now makes the route find a way round it, and known closures nearby are kept out of new routes (by TomTom, Mapbox and offline routes alike). A closure with no way round (the only road to where you're going) is still used.
- Live alternatives not always showing: they're now looked for every 2 minutes (was 3), in slow traffic too (they were skipped under about 7 mph), up to the last 5 minutes of the trip (was 10), half a minute after a reroute, and from both route services when you have keys for both.
- Android Auto split screen (GitHub #44): with Horizon Nav in the small part of the screen, only the right-hand part of the map showed, not centred, so the route and cards were out of view. The map now fits the part Android Auto gives it, and is laid out again when that part changes size.
- Arrow turning off the road at lights (GitHub #42): slowing down, the GPS's direction swings about and the arrow turned into a car park or plaza beside the road until you moved off. It now keeps your last real direction and stays on the road down to a crawl.

### New
- Other routes button: while navigating, a button on the map (under map options) shows the other ways to go any time, in grey with how they compare; tap one to switch. On Android Auto it's the first row in Settings, as a list you can pick from with a touch or the knob.
- Roadworks and road closures round about (GitHub #45): with a TomTom key, roadworks and closures within about 7 km are shown on the map whether or not you're on a route, as Google Maps and Waze show them (before, only those on your route showed).

## 1.0.420 — 6 Nov 2026

### New
- More map colours: Ember (warm and dark), Mono Night (greys, with only the route and warnings in colour), Forest, Arctic, High Contrast, Sepia, Neon, Pastel, and Match my car (a hint of your car's interior lighting colour, with how strong). In Settings > Map look > Colours.
- Light and weather: a new group in Settings > Map look, with Night brightness, Moonlight at night (moved here) and its Moon strength, Shadows on or off, Sun in its real place (by day, light and shadows from where the sun really is), Overcast light (soft daylight, no hard shadows) and Weather light (greyer light when it's raining). Everything starts as before.
- Moonlight at night: a switch in Settings > Map look > Light and weather. Like dusk, but from the moon: at night the map gets a low, pale blue light from one side with long soft shadows, in place of Mapbox's flat night light. The night sky, street lamps and your colours stay. Off to start with.
- Twilight map colours: a new choice in Settings > Map look > Colours: the deep purple-blue night, more purple than Horizon (its ground and buildings lavender at night).

### Improved
- Horizon colours: a little more lilac.

## 1.0.413 — 6 Nov 2026

### New
- Glowing buildings: a switch in Settings > Map look > 3D. At night the buildings glow blue from within, with a Glow slider to turn it up or down (it lights the buildings only). They keep the shape of Mapbox's own, with the same slightly rounded corners and edges. While they glow, the detailed building fronts are hidden; landmarks stay. Off to start with.

## 1.0.411 — 6 Nov 2026

### Improved
- Android Auto with a rotary knob or buttons: a Places button is now on every car's top row. It opens a list the knob can move through, with Search first, then the fuel, food and parking shortcuts and saved places (the search bar and chips drawn on the map can't be reached by a knob).
- Android Auto Settings while navigating: the Settings button now stays while you navigate (Stop, Sound, Report, Settings). 2D / 3D has moved to the first row of the car's Settings.

## 1.0.409 — 6 Nov 2026

### New
- Custom map colours: a Custom tile in Settings > Map look > Colours, with an editor over the live map. Day and night each get their own colours for the ground, water, parks, roads, main roads, buildings, coloured areas and labels, plus night brightness, an overall tint, glowing buildings and underglow. It starts as the theme you had, so nothing changes until you change something.

## 1.0.406 — 5 Nov 2026

### New
- Moonlight map colours: a new choice in Settings > Map look > Colours. At night it's Horizon's night, with the buildings a clearer blue and a soft blue glow on the ground round each one, as if light spills out of them. By day it's the same as Horizon.

## 1.0.402 — 5 Nov 2026

### New
- Moonlight map colours: a new choice in Settings > Map look > Colours. At night the map is a lighter blue, like Google Maps at night, and the buildings are a brighter blue that looks softly lit from inside (a softer, even night light with no hard shadows). By day it's the same as Horizon.

## 1.0.400 — 5 Nov 2026

### New
- Speed camera size: a slider in Settings > On the map > On your route, from 50% to 350%, to make the red speed camera badge on the map smaller or bigger (on the head unit, phone and Android Auto).

## 1.0.398 — 5 Nov 2026

### Fixed
- Crash with the driver's display: on cars that show the map behind the steering wheel (seen on a Jaguar), the app could crash as soon as Android Auto started, because that map had nothing to tell it when to start and stop. It now has.

### New
- Twilight map colours: a new choice in Settings > Map look > Colours. It's the Horizon colours as they were, with the deeper purple-blue night.

### Improved
- Horizon colours: the night map is now a dark slate blue with just a touch of purple (less obviously purple than before), with the buildings a lighter slate blue. Same brightness as before. The old purple look is now called Twilight.

## 1.0.396 — 5 Nov 2026

### Improved
- Horizon theme at night: buildings now stand out lighter than the ground instead of being the same shade, on the phone, head unit and Android Auto. Day keeps its own colours.

## 1.0.388 — 4 Nov 2026

### Improved
- Horizon theme at night: buildings are now a soft lavender-blue, a step lighter than the ground, instead of the same dark shade as it, so they stand out (walls and roofs still take the light and shadow). Day keeps its own building colours.

## 1.0.386 — 4 Nov 2026

### Fixed
- Fuel prices on Android Auto: euro and Australian prices showed as pence ("178.9p"); they now show the local way (€1.789, 189.9c).

### New
- Charger availability: the charging list shows how many chargers are free right now ("8 of 12 free", red when none are), with each charger's top power and plug types, and the suggested charging stop on a long electric trip says how many are free there. Live counts use your TomTom key, where the charger's operator shares them; others say "No live info".
- Lorry check: with Lorry chosen, the route choice checks the height, weight, width and length limits along the whole route in the background and says "no limits under your lorry" or which it found (e.g. "1 low bridge (3.9 m)").
- Lorry parks: a "+ Lorry park" stop on the route options, and Lorry parks in place of Parking on the map page, finding lorry parks and services that take lorries (from OpenStreetMap), with opening hours, spaces and showers where mapped.
- Speed sign position on Android Auto: bottom, middle or top of the side, separate from the phone's (Settings > Android Auto, or the car's own Settings > More settings).
- Send a crash report from Settings: the problem report now has a Send report button, which sends it with your unit's details like Feedback does.

### Improved
- Parking at your destination: the car parks offered near the end of a trip now show their price, opening hours and spaces where OpenStreetMap has them.
- Your car on the phone: the phone now remembers where you parked at the end of any drive (not only after Android Auto), and the "Your car" card offers Walk back once you've walked away from it.
- "Hey Google, navigate to …" on Android Auto: a place asked for by name or address now starts its route to the top result by itself (the route choice shows where to, with Cancel), instead of leaving a list to tap while driving.

## 1.0.384 — 4 Nov 2026

### Fixed
- Turn notification with Android Auto on: only the car screen updates it now, so it no longer flicks between two slightly different distances.
- Speed cameras on a route are matched again whenever a fresh search finds different cameras, not only when the number found changes.
- Feedback report: it now lists the lanes for the next turn exactly as the route data gives them (which way each lane goes, and the ones to use), so a junction with the wrong lane arrows can be traced.

### Improved
- Android Auto settings: on cars that only allow a few rows in a list, the rest now go on a "More settings" page (the last ones used to be cut off). The most used come first: voice, map direction, day and night, route options. "Locator arrow scale" is now "Arrow size" and only changes the car screen's arrow (it used to change the phone's arrow too), and "Map UI scale" is "Overall size", matching the app's Settings > Android Auto.
- Settings tidied: speed camera icons (On the map) and speed camera warnings (Alerts) now say which is which, and the speed sign size says it's for this screen only (Android Auto has its own).
- Smoother lanes: while the junction's road is still being looked for, the lanes are only redrawn once it's found, instead of every second.
- Problem report: crash reports now say which version and when, and one left over from before an update says so (it may already be fixed), so an old report isn't mistaken for a new problem.
- Lane guidance on the map: all the lanes are drawn on the road again, with the lane to use lit up and its arrows and the other lanes in a darker road colour with clear white lines (so they no longer fade into the map), as the view zooms in before the junction. They're laid out on the road itself (centred on a motorway or one-way road; on a two-way road all on your side of the middle), at a slimmer width, so they no longer spill over the verge; only where the map has no road there is just your lane drawn, under the route line. The route line moves into the lit lane as you come up to the junction, so the two always line up (it keeps looking for the junction's road as you get closer, and moves over even where the lane list only starts at the junction itself, so it no longer happens only some of the time). Settings > Turn card & lanes > Lanes shown as also offers a close-up picture of the junction's lanes under the turn card, or the sign over the road from the last few versions.

## 1.0.375 — 4 Nov 2026

### Fixed
- Day and night following the head unit: its night mode is now checked every 1.5 seconds (it was every 4) and straight away when the system's night mode setting changes. The feedback report now shows when each night signal last changed, to track down units that are still slow.
- Traffic in the time left: with Mapbox routes the time left could stay green with red traffic on the map, because the delay was measured against the usual time for that hour, so everyday rush-hour traffic counted as none. The delay is now the time the amber and red stretches take over their no-traffic time, so the time left turns amber or red and shows "+X min traffic" when there is traffic. (The arrival time already included the traffic.)
- Speed cameras on a route: cameras found by the search round you are now matched to the route too, so ones the route search missed are shown and warned about. A camera now has to be within 30 m of the route's line (it was 45 m from a point on it), and one mapped twice gives one alert, so cameras on a road alongside no longer set off alerts.
- Speed cameras without a route: a camera now has to be close to the line you're driving along (within 30 to 40 m to the side) rather than within an angle ahead, which took in cameras on other roads up to a few hundred metres away.
- Road name under the arrow: it now comes from the road the map draws under you, so it changes as soon as the road does (it used to stay on the old road until the next turn, or until a new route, and could vanish when the lookups failed). On the phone, head unit and Android Auto (GitHub #35, #36).

### New
- Tilt settings: Settings > Map look > 3D view sets how far the 3D map tilts, separately for a wide screen, a tall screen and Android Auto (30° to 65°, or Auto as before).
- Full screen switch: Settings > Screen & sizes > Screen. Off keeps the status and navigation bars showing, so a head unit's own bar (climate, home button) isn't covered by the map.
- North up on Android Auto: a "Map direction" row in Android Auto's Settings switches between North up and turning with you, remembered for the car (GitHub #39).
- Fuel prices in Australia: New South Wales and Tasmania prices from the NSW government's FuelCheck, with a free API key and secret from api.nsw.gov.au, in cents a litre.

## 1.0.373 — 4 Nov 2026

### New
- Lorry routes: Settings > Routes > Vehicle can now be set to Lorry, with its height, width, length, weight, axle weight and hazardous goods. Routes from TomTom and offline routes then keep off low bridges, weight limits and lorry bans for that size; Mapbox routes use the height, width and weight. A lorry never gets a car route as a fallback. Each route shows a lorry badge: blue when every measurement was used, amber when only height and weight could be (Mapbox).
- Low bridge and weight limit warnings: for a lorry on a route, height, weight, width and length limits on the road ahead show as signs on the map, and one the lorry is over gets a red card under the turn card and a spoken warning at about 1 km and again at 300 m. On the phone, head unit and Android Auto.

### Improved
- Settings easier to scan: long lists are split under more headings (3D, Sky and satellite; Speed, Cameras, Hazards, Zones and reminders; Directions, Voice and volume; Brightness, Sizes, Speed display; and on Android Auto, Sizes on the car screen and Setting it up). Nothing has moved section, and the settings search finds everything, now including "Show petrol stations too".

## 1.0.370 — 3 Nov 2026

### New
- Next turn in the notification: while you navigate, Horizon Nav's notification now shows the turn arrow, distance, road and arrival time, marked as a navigation notification. Car launchers, smartwatches and other screens that show navigation notifications can pick it up. Switch it off in Settings > Guidance > Turn card.

## 1.0.368 — 3 Nov 2026

### Fixed
- Trip card figures running together in portrait: with an electric car (a fourth figure and a fifth button), or any time they don't fit on one line, the buttons now go on a line under the figures.
- Miles in metric settings: with kilometres chosen, settings now say km, km per kWh, litres per 100 km and cents, and the route's fuel or charging cost shows in your own currency. Saved figures stay the same.

### New
- Speed position: Settings > Screen & sizes > Speed position moves the speed limit and speedometer to the middle or top of its side (away from the cards), for when your hand covers it at the bottom.
- Map on the driver's display: on cars whose Android Auto offers it, the screen behind the steering wheel now shows the map following you, with your route (it used to show only the app's icon). Switch it off in Android Auto's Settings > Map on the driver's display.

### Improved
- Electric cars: with Electric chosen as your fuel, the map page shows Charging first and Toilets instead of the petrol Fuel button (on the phone and Android Auto). Settings > Fuel > Show petrol stations too brings it back.
- Android Auto settings: the 12 / 24-hour clock can be set from the car too.
- Search while navigating on Android Auto: the Report screen now has a Search tile, for finding a stop on the way.

## 1.0.363 — 3 Nov 2026

### Fixed
- Android Auto cards white on a dark map: around sunset the map's light dims to dusk before the car switches to night, and the cards stayed white. They now go dark with the map (unless "Always day" is picked).
- Arrow lagging behind in stop-start traffic: after stopping, the arrow now moves off again as soon as you do. It used to wait until you were going about 9 mph, or had moved up to 20 m.

### New
- Number-plate cameras on the map: Flock and other automatic number-plate (ALPR) cameras mapped on OpenStreetMap now show as a purple eye badge, within about 10 km of you as you drive. Tap one to see what it is. Shown only, no alerts; switch them off in Settings > Map.
- Smaller offline downloads: big countries now open up to show their regions in Choose an area (Germany's states, France's regions, England's counties, and regions of Italy, Spain, Poland, the Netherlands, Russia, India, Japan, Indonesia and Brazil), each a much smaller download than the whole country.
- Near you: Choose an area now starts with the area you're in (your region or state, or all of the country).
- Clock setting: Settings > Screen & sizes > Units can now set 12 or 24-hour times by hand, as well as following the device.
- Smaller offline areas: Singapore and Brunei can now be downloaded on their own, instead of together with Malaysia.

### Improved
- Cooler, longer-lasting phone: when the phone gets warm or battery saver is on, the map draws fewer frames a second (30, or 20 when hot), whatever frame rate is chosen. The feedback report now says how warm the phone was.
- Zoom out further: − now goes two steps further out than before, for an overview of the traffic around you, and that zoom is remembered too.
- North up remembered: if you leave the map north up, it opens that way next time, on the phone and on Android Auto separately.

## 1.0.361 — 2 Nov 2026

### Fixed
- Offline routes failing with "Exceeded maximum circumference for exclude_polygons": with areas to avoid switched on (such as clean air zones), offline routes failed when an area was too big for the offline router. Big areas are now left out of offline routes, and if the router still refuses, the route is planned without them instead of failing.
- Route stopping at a border: with no signal and a destination outside your downloaded maps, the route used to end at the edge of the map as if that were the destination. It now says the map ends there, doesn't announce "you have arrived" at the border, and plans the rest of the way as soon as there's signal.
- "No signal" with signal: when the head unit's date and time are wrong, every secure connection fails and looked like no signal. The app now spots this and tells you to check the date and time.
- Place names for shared offline areas: the towns and villages download now covers every country in the area, for example Singapore and Brunei as well as Malaysia, and the Gulf States pack's countries.
- Android Auto speed sign jumping: on some screens it kept jumping between beside the cards and above them as their text changed. It now stays put unless there's clearly room.

## 1.0.358 — 31 Oct 2026

### Fixed
- Update failing with "No such file or directory": trying an update again (or after an earlier try) no longer deletes the file it has just downloaded, so Update works again, including saving to Downloads for AAEnabler.
- Android Auto arrow under the cards: on cars that report their screen oddly (seen on a Uconnect 8.4), your arrow was pushed left under the turn and trip cards. It now always moves clear of them, and the feedback report includes the car screen's layout.

### Improved
- Better routes with TomTom and Mapbox together: with a TomTom key and a Mapbox token (and routes left on Auto), a new route is now asked of both at once. The quicker one comes first, and a different road from the other is offered as another choice.
- Clock times follow your device: arrival and leave-by times now use the phone's or head unit's own 12 or 24-hour setting (for example 4:52 pm instead of 16:52).

## 1.0.355 — 31 Oct 2026

### Fixed
- Mapbox key "401" errors: a refusal no longer switches the map off straight away. Horizon Nav now asks Mapbox what's wrong with the token and says so plainly (mistyped or cut short, deleted or replaced, or out of date), and if Mapbox says the token is fine the map just loads again. A token that looks cut short or mistyped is flagged as you paste it, and the feedback report says what Mapbox made of the token.

## 1.0.353 — 31 Oct 2026

### Fixed
- Map shaking when stopped: standing still at lights or parked, the map no longer drifts and turns with the GPS's small wobbles (it was worst on Android Auto, where it shook all the time). It now stays put until you actually move off.

### New
- Report on Android Auto: a Report button (where the Search button was) opens Police, Crash, Hazard, Broken down and Mobile camera; one tap saves the report where you are, as on the phone. Search is still in the search bar on the map, or at the top of Places on cars that don't pass taps on to the map.
- Zoom remembered: the zoom you set with + and − when you're not on a route is kept for next time, on the phone and on Android Auto separately. Tap Re-centre while the map is following you to go back to automatic zoom.

### Improved
- Fold away everything on Android Auto: the arrow in the search bar now folds away Home and Work and the fuel, charging, food, coffee and parking buttons as well as Saved & recent.
- Your side of divided roads: on dual carriageways and other divided roads, your arrow now sits on the side you're driving on, not whichever side the GPS happened to be nearer.

## 1.0.350 — 31 Oct 2026

### Fixed
- Closer to 3D lane guidance: where the real painted lanes are found, the lit-up lanes now start 150 m before the junction (was 110 m), the route line sits in your lane from there and bends through the junction, and a small lane bubble rides on the road just ahead of your arrow showing the lanes to use. Where the real lanes can't be matched, the overhead lane sign is shown instead.

## 1.0.348 — 31 Oct 2026

### Fixed
- The traffic view when stopped no longer kicks in while you wait at a turn, and "Your car" is only saved when the car has stopped (switching apps on the car screen mid-drive could save it).
- Mapbox's lane data on TomTom routes: Mapbox draws the lanes on the map, so its own lane list suits them best. With a Mapbox token, a TomTom route now gets Mapbox's lane list in the background, junction by junction, wherever Mapbox's route goes the same way (other junctions keep TomTom's). It uses one extra Mapbox request per route and can be switched off in Settings > Turn card. The feedback report says which lane list was used.
- Lane sign over the road: coming up to a junction with lane guidance, a sign now stands over the road at the junction, like a motorway gantry, with the lanes to use lit up. It replaces the lane strips painted across the road, which often sat a lane or two out from the real lanes. Where the map has real lane data that matches the lanes you need (on many motorways), the lanes are still painted on the road, exactly over the real ones, with the route line through them. With Demo mode on, a short message says which was used (also in the feedback report).
- Where lanes are painted on the road, the route line runs through the lit-up lanes right up to the junction and carries smoothly into the turn arrow, then eases back onto the road over the next 25 m, with no gap or step where the lane guidance ends.

### New
- Default zoom: set your own normal zoom for when you're not on a route. Zoom with + and − on the map and tap "Make this my default", or use the slider in Settings > Map look, where Reset puts it back. Auto zoom still pulls out at speed and moves in at junctions, round your setting.
- Traffic overview when stopped: after 30 seconds stopped at lights or in a queue, the map pulls back to a top-down view of the traffic round you, and goes back as soon as you move off. On by default; switch it off in Settings > On the map.
- Where you parked: when Android Auto disconnects, the phone remembers where the car was. A "Your car" card shows how long ago and how far, with a Walk button and a car pin on the map, for 2 days or until you're back in the car.
- Send places from your phone: pair the phone with the head unit once (Settings > Places & trips), then "Send to car" on the phone's route screen. The head unit offers it the next time it's on (within 12 hours) and starts it after a few seconds. Places go through a free message relay, scrambled with your pairing code.

## 1.0.341 — 30 Oct 2026

### Fixed
- Road name under your arrow: it no longer jumps to a side road for a moment at junctions (a new name now has to be confirmed, unless you've clearly turned), it clears in car parks and when it's out of date instead of showing the last road, and after an unnamed slip road it no longer shows the exit sign's text as the road name.
- Trees on Android Auto: the car map is now drawn at close to the phone's zoom, so Mapbox's 3D trees and other close-up detail show there too (it used to sit so far out that they never appeared). Map text on the car screen is a little smaller as a result.

## 1.0.339 — 30 Oct 2026

### Fixed
- Head units that restart while Horizon Nav is open (reported on a built-in Android 9 unit): the app now notices when the whole unit restarted soon after opening it or while driving, and after two it switches to safer drawing one step at a time, starting with drawing the map in the app's own window, which is gentler on some built-in units. What happened just before goes in the feedback report. The background journey-time suggestion also no longer runs the offline router, which loads large files into memory.
- Updating with AAEnabler: if you installed Horizon Nav with AAEnabler, Update now saves the new version to your Downloads folder to install with AAEnabler, so it stays in Android Auto (updating inside the app used to take it out).
- Missing 3D buildings: the clear area kept round your arrow no longer cuts through buildings. With no route there's no longer a straight strip ahead (it went through buildings on bends), and on a route the strip is narrower, so buildings in narrow streets stay.
- The offline maps page now points to Settings > Keys & accounts for the Mapbox token (it still said "Search & keys").

## 1.0.337 — 29 Oct 2026

### New
- Donate: Horizon Nav is free with no ads, and you can now support it with a donation: Settings > About > Donate, with a QR code to scan on your phone or a button to open PayPal. A one-off card also asks, a day or so after installing (never while you're driving).

## 1.0.335 — 29 Oct 2026

### Fixed
- Adding a stop on the way (fuel, coffee and so on) no longer hangs: along the route Mapbox is asked first, a few points at once, and a search that gets stuck on a slow server now gives up after 30 seconds as it should (it could spin for minutes).

### Improved
- The arrow in the search bar that folds Saved & recent away is back, and Android Auto has it too.

## 1.0.331 — 29 Oct 2026

### Improved
- Android Auto map page matches the app: Home and Work tiles with the drive time, five round buttons for fuel, charging, food, coffee and parking, and Saved & recent with your first few places (as many as fit) and "All" for the rest, in place of the long scrolling row of chips. Your arrow also sits in the middle of the clear map there when you're not navigating.

## 1.0.327 — 29 Oct 2026

### Fixed
- Fuel, parking and other nearby searches: they now use Mapbox's category search, which finds the nearest places of that kind, instead of a text search that often came back empty and fell through to the busy free OpenStreetMap servers ("HTTP 429", "HTTP 502"). Along a route, when those servers are busy, Mapbox is asked instead, and errors are in plain words.
- 2D / 3D is remembered: the map no longer starts in 3D every time if you'd left it in 2D.

### New
- 2D / 3D on Android Auto: a 2D / 3D button in Android Auto's own buttons, as in the app (while navigating it takes the Demo button's place unless Demo is switched on in Settings).

### Improved
- Android Auto speed sign: it sits right up against the edge of the screen when Android Auto's buttons hide, and just beside them when they show.

## 1.0.325 — 29 Oct 2026

### Fixed
- Android Auto trip card on wide screens: it no longer stretches right across a very wide car screen with its sound and end-route buttons hidden under Android Auto's own buttons; it's now just as wide as its figures and buttons.

## 1.0.323 — 29 Oct 2026

### Fixed
- Road name overlap: driving with no route, the road name under your arrow no longer sits on top of the "Heading NW" pill at the bottom (the pill already shows the road, so the one under the arrow steps aside while it's up).
- Junctions: the view now flattens towards 2D as you reach turns and roundabouts as intended, instead of swinging low enough to fill the screen with horizon. The junction close-up only applies at slip roads, exits and forks, where it moves closer and a little flatter.

### Improved
- Arrow centred in the clear map: when you're not navigating, your arrow sits in the middle of the map between the search panel and the buttons, instead of dead centre behind them.
- Fewer buttons on the map: just the compass and map options stay up while you drive. Zoom appears when you touch the map or stop, voice and sound are on the trip card, 3D / 2D and sound are quick switches at the top of map options, and Settings is beside the buttons when you're not navigating (and in map options at any time).

## 1.0.321 — 29 Oct 2026

### Improved
- Map buttons grouped: compass, map options, 3D and sound sit together in one panel at the top right with the zoom buttons straight under it, and Settings just to its left. If zoom would touch the speedometer on a short screen, it moves beside the panel instead.

## 1.0.319 — 29 Oct 2026

### Improved
- Map buttons grouped: compass, map options, 3D and sound sit together in one panel under the Settings button. Zoom stays halfway down the right-hand edge, and only moves up beside the panel if it would touch it or the speedometer.
- Car types for the real 3D model: the hatchback, estate, SUV, pickup, van and sports car now have their own 3D models too, not just the saloon, and the car type picker shows for the real 3D car.
- Map options redesigned: Standard or Satellite as two picture cards, a quick Auto / Day / Night switch, and the map layers as tiles that light up when on. Reports fit on one row, and in landscape the panel is wider, in two columns, so nothing needs scrolling.

## 1.0.317 — 29 Oct 2026

### Improved
- What's new in landscape: the card now spreads across the screen, with the title and Let's go button on the left and the changes in two columns on the right.
- Tidier map page: Home and Work are two tiles showing the drive time, and fuel, charging, food, coffee and parking are five round buttons that always fit. Saved & recent shows three places with "All" for the rest, and your own saved places are in it too.
- Map buttons grouped: compass, map options, 3D and sound sit together in one panel with zoom under it, and Settings is in the search bar. On shorter screens zoom moves beside the panel so it stays clear of the speedometer.

## 1.0.315 — 29 Oct 2026

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

### Improved
- Settings reorganised: 15 smaller sections in four groups (Map, Driving, You, Device), each split into short headed lists, with Android Auto in a section of its own. Search still finds every setting.
- What's new card redesigned: each change on its own card with an icon, a headline and a short description, sorted under New, Improved and Fixed.

## 1.0.311 — 29 Oct 2026

### Changes
- The What's new card after an update now shows the changes straight away, even with no connection: the notes are built into the app instead of fetched from GitHub, which often refused.

## 1.0.308 — 29 Oct 2026

### Changes
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

## 1.0.305 — 29 Oct 2026

### Changes
- Left-hand drive: the speedometer moves back to the bottom left (the cards are on the right), so the turn card no longer covers it; the speed limit sign stays on the screen side of it. The exit banner no longer repeats what the road sign on the turn card already says.

## 1.0.303 — 29 Oct 2026

### Changes
- Android Auto: the speedometer sits down in the bottom-right corner again, level with the trip card, instead of floating halfway up the screen.
- Android Auto, tidier screen: hazards ahead now sit along the bottom of the turn card, the sound button has moved onto the trip card, the "Use the right lane" line is gone where the lane arrows already show it, the trip card has a slimmer progress bar and bigger labels, and while the next turn is far away the turn card shrinks to one line.
- Android Auto: the car arrow and route line are a little bigger, so they stand out from the buildings.

## 1.0.301 — 29 Oct 2026

### Changes
- Android Auto with left-hand drive: your arrow no longer hides under the turn and trip cards, and the speed limit sign no longer runs off the right-hand edge of the screen.

## 1.0.299 — 29 Oct 2026

### Changes
- Fix lane guidance pushing the route line and lane strips off the road at ordinary junctions. Lanes are now lined up with the road only on motorway and trunk carriageways; elsewhere the lane to be in is drawn under the route line, and lane strips are only painted on the last stretch before the junction.
- Add alternative-route line colour customisation, with a pale blue-grey default for better contrast against grey roads.
- Fix the map zooming in and out far too slowly while driving (it could take 10 seconds or more to close in for a turn), and the map starting zoomed out twice as far as intended.
- Fix roundabout arrows pointing the wrong way: they now show the direction the real exit goes, rather than guessing from the exit number.
- The "Look for updates by itself" switch works again, and automatic update and map-data checks run at most every half hour instead of every time the app opens or the connection comes back.
- Changing the alternative-route colour now applies straight away.
- The car screen's "Navigation arrow size" now sizes the arrow on the map (as on the phone) instead of the turn card.
- Stop the speedometer covering the trip card on left-hand-drive layouts, and stop the exit banner repeating what the road sign on the turn card already says.
- Smoother map drawing: the map is no longer capped at 30 frames a second on top of the camera's own pacing, which made it judder.
- No TomTom key is built into the app any more (anyone could pull it out of a shared APK); paste your own in Settings if you use TomTom search.

## 1.0.292 — 28 Oct 2026

### Changes
- Move the road-and-heading pill to the top of the map while driving without a route, preventing it from overlapping the road-name pill beneath the locator.
- Improve Android package-installer access to downloaded update APKs by explicitly granting the content URI through ClipData as well as intent flags.

## 1.0.290 — 28 Oct 2026

### Changes
- Improve Android package-installer access to downloaded update APKs by explicitly granting the content URI through ClipData as well as intent flags.

## 1.0.286 — 28 Oct 2026

### Changes
- Fetch the installed release notes directly by tag as a fallback if GitHub's releases-list API fails, while retaining notes for skipped releases on multi-version upgrades.
- Automatically fit the Android Auto speedometer and speed-limit sign to the available display area to prevent edge clipping, including split-screen layouts.
- Add a dedicated Android Auto speedometer/speed-limit sign size slider.

## 1.0.284 — 28 Oct 2026

### Changes
- Automatically fit the Android Auto speedometer and speed-limit sign to the available display area to prevent edge clipping, including split-screen layouts.
- Add a dedicated Android Auto speedometer/speed-limit sign size slider.

## 1.0.282 — 28 Oct 2026

### Changes
- Automatically fit the Android Auto speedometer and speed-limit sign to the available display area to prevent edge clipping, including split-screen layouts.
- Add a dedicated Android Auto speedometer/speed-limit sign size slider.

## 1.0.280 — 28 Oct 2026

### Changes
- Show all published release notes newer than the installed version, including releases skipped during an update.

_Add only changes made since the previous published release. This section is cleared automatically after a successful publish._

## 1.0.278 — 28 Oct 2026

### Added
- Added settings to enable or disable automatic route starting and configure its countdown from 3 to 30 seconds.

### Improved
- Increased the Android Auto locator arrow lateral clearance from overlay cards and widened the collision band so it adapts more reliably to full-screen and split-screen map layouts.

### Improved
- Expanded Android Auto map UI scaling range from 60–160% to 40–300%.
- Added Android Auto map settings controls for adjusting overlay UI scale and locator arrow scale. The Android Auto template interface uses tap-to-cycle scale levels rather than a continuous slider.

### Improved
- Increased the Android Auto locator arrow scale to almost twice its previous size.
- Fixed the What's New card to use the app's resolved day/night theme rather than a default dark palette.

## 1.0.276 — 28 Oct 2026

### Improved
- Increased the Android Auto locator arrow lateral clearance from overlay cards and widened the collision band so it adapts more reliably to full-screen and split-screen map layouts.

### Improved
- Expanded Android Auto map UI scaling range from 60–160% to 40–300%.
- Added Android Auto map settings controls for adjusting overlay UI scale and locator arrow scale. The Android Auto template interface uses tap-to-cycle scale levels rather than a continuous slider.

### Improved
- Increased the Android Auto locator arrow scale to almost twice its previous size.
- Fixed the What's New card to use the app's resolved day/night theme rather than a default dark palette.

## 1.0.274 — 28 Oct 2026

### Improved
- Expanded Android Auto map UI scaling range from 60–160% to 40–300%.
- Added Android Auto map settings controls for adjusting overlay UI scale and locator arrow scale. The Android Auto template interface uses tap-to-cycle scale levels rather than a continuous slider.

### Improved
- Increased the Android Auto locator arrow scale to almost twice its previous size.
- Fixed the What's New card to use the app's resolved day/night theme rather than a default dark palette.

## 1.0.272 — 28 Oct 2026

### Improved
- Added Android Auto map settings controls for adjusting overlay UI scale and locator arrow scale. The Android Auto template interface uses tap-to-cycle scale levels rather than a continuous slider.

### Improved
- Increased the Android Auto locator arrow scale to almost twice its previous size.
- Fixed the What's New card to use the app's resolved day/night theme rather than a default dark palette.

## 1.0.270 — 28 Oct 2026

### Improved
- Increased the Android Auto locator arrow scale to almost twice its previous size.
- Fixed the What's New card to use the app's resolved day/night theme rather than a default dark palette.

## 1.0.268 — 28 Oct 2026

### Improved
- Increased the map locator arrow size by approximately 22% to make it easier to see.
- Fixed the What's New card to use the app's resolved day/night theme rather than a default dark palette.

## 1.0.266 — 28 Oct 2026

### Improved What's New appearance
- Added a light/day-mode colour scheme for the What's New card, using the app's active theme so text and release notes remain readable in both day and night modes.

### Fixed initial map zoom
- Apply the initial two-step zoom-out after the lane zoom floor, so the first-open overview adjustment is not overridden while stationary.
- Keep the existing automatic zoom behaviour once movement is detected.

## 1.0.264 — 28 Oct 2026

### Fixed initial map zoom
- Apply the initial two-step zoom-out after the lane zoom floor, so the first-open overview adjustment is not overridden while stationary.
- Keep the existing automatic zoom behaviour once movement is detected.

## 1.0.262 — 28 Oct 2026

### Improved initial map zoom
- Start with the map approximately two zoom-out button presses farther out when the app is first opened.
- Once movement is detected, automatically return to the existing speed-based zoom behaviour.

### Improved Android Auto display scaling
- Adjusted turn-guidance arrow scaling so it is not unintentionally multiplied by the navigation card scale.
- Made the interface scale respond to the available display's short edge and aspect ratio.
- Made map rendering scale adapt to the Android Auto viewport to help reduce excessive zoom on some displays.

## 1.0.260 — 28 Oct 2026

### Improved Android Auto display scaling
- Adjusted turn-guidance arrow scaling so it is not unintentionally multiplied by the navigation card scale.
- Made the interface scale respond to the available display's short edge and aspect ratio.
- Made map rendering scale adapt to the Android Auto viewport to help reduce excessive zoom on some displays.

## 1.0.258 — 28 Oct 2026

<!-- Add entries here before the next publish, for example:

### Added
- Describe a new app feature.

### Improved
- Describe an improvement to existing app behaviour.

### Fixed
- Describe a bug fix.
-->

## 1.0.256 — 28 Oct 2026

<!-- Add entries here before the next publish, for example:

### Added
- Describe a new app feature.

### Improved
- Describe an improvement to existing app behaviour.

### Fixed
- Describe a bug fix.
-->

## 1.0.254 — 28 Oct 2026

<!-- Add entries here before the next publish, for example:

### Added
- Describe a new app feature.

### Improved
- Describe an improvement to existing app behaviour.

### Fixed
- Describe a bug fix.
-->

## 1.0.252 — 28 Oct 2026

<!-- Add entries here before the next publish, for example:

### Added
- Describe a new app feature.

### Improved
- Describe an improvement to existing app behaviour.

### Fixed
- Describe a bug fix.
-->

## 1.0.250 — 28 Oct 2026

<!-- Add entries here before the next publish, for example:

### Added
- Describe a new app feature.

### Improved
- Describe an improvement to existing app behaviour.

### Fixed
- Describe a bug fix.
-->

## 1.0.246 — 28 Oct 2026

### Improved in-app updates
- Show the installed and available app versions in the update prompt.
- Display release notes in the update prompt when the release provides them.
- Show download progress as a percentage and downloaded/total megabytes.
- Improve update failure messages, including checksum verification failures, timeouts, and cancellations.
- Only open the APK in a browser when the device has no download service, avoiding a browser fallback that could bypass checksum verification after other download failures.
- Keep update detection working if `version.json` is missing or malformed by falling back to the version number in the release title or notes.

## 1.0.244 — 28 Oct 2026

### Changes
- Preserve a separate, versioned GitHub Release for every published build, with generated release notes.
- Continue updating the fixed `app` release assets so existing in-app update checks remain compatible.
- Validate the APK, checksum, and version metadata during the publish workflow.

## 1.0.239 — 27 Oct 2026

## What's changed in Horizon Nav 1.0.239

### Changes
- Sync Horizon Nav source from uploaded ZIP [skip ci] (b85964e)
- Add files via upload (d234816)

## 1.0.237 — 27 Oct 2026

## What's changed in Horizon Nav 1.0.237

### Changes
- Sync Horizon Nav source from uploaded ZIP [skip ci] (68d1bf8)
- Add files via upload (94c3908)
- Add files via upload (bbf9a65)

## Earlier versions

Builds before 1.0.234 replaced the single download each time, so their notes weren't kept.

<!-- For whoever prepares a build: add each change under "## Unreleased" (### New, ### Improved or ### Fixed), written as "Headline: what it does"; the app's What's new card shows the headline in bold. Only app changes, not build or GitHub maintenance. After a version is published, move its entries into a "## 1.0.N — date" section below. -->
