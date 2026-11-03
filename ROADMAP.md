# Horizon Nav: to-do list

Rebuilt on 29 October 2026 (bugs updated 2 November 2026) from the Reddit threads (r/Androidheadunits and r/AndroidAuto), GitHub issues and messages passed on directly, and checked against the code. The numbers are new; they don't match the older list.

Move items to **Done** as they ship, and add new requests at the bottom of the right list.

## Bugs still open

- **A notice when installing an offline map** on a 2 GB head unit (Head-Whole5462, r/Androidheadunits). Screenshot not seen yet: ask for it again.
- **"Well, what now?"** (Opposite-Working727, r/Androidheadunits). Screenshot not seen yet: ask what it shows.

## Features requested

2. **Change the app's language.** Asked on r/Androidheadunits. Parked for now.
6. **Import saved places from Google Maps** (from a Google Takeout file).
7. **Copy offline map packs from another device.** For example, download on a phone or PC at home and copy them across by USB, for when the car can't reach home Wi-Fi.
9. **Swap the search bar for a search icon**, for people who want even more of the map on show.
10. **Choose where your arrow sits on screen.** Requested on Android Auto, for one car where the centre felt too far left. The automatic centring added since may cover this. Check before building.
11. **Radarbot integration** for speed cameras on the route. Radarbot doesn't offer a public way in, so this may not be possible. The app's own camera data is used instead.
12. **Map options on Android Auto.** Layers such as traffic and satellite, and reports. Android Auto only allows a few buttons of its own, so this needs a different approach there.
13. **Android Auto voice search.** Pressing Android Auto's own mic and asking for a place says "Sorry, I didn't understand". "Navigate to …" with the app named may already work. Needs testing.

## Can't be fixed in the app

- **The app not showing up in Android Auto on some phones**, especially GrapheneOS and LineageOS, or without installing through AAEnabler. Android Auto only lists unofficial apps under certain conditions; the app shows a note about AAEnabler.
- **Some speed limits are wrong.** They come from the map data (OpenStreetMap / Mapbox). Wrong limits can be corrected on openstreetmap.org.
- **Waze-style live police alerts.** These need lots of users reporting nearby. The app's own reports (police, crash, hazard) only show on your own map.

## Done (from these requests)

- Custom default zoom, with reset.
- Traffic overview when stopped (with a setting).
- Remember where you parked (phone, after Android Auto).
- Send a destination from your phone to the head unit.
- Search tip to add a Mapbox key when the free servers are busy.
- Trees missing on Android Auto (GitHub #19): the car map is now drawn at close to the phone's zoom.
- Wrong road name under the arrow (GitHub #20).
- AAEnabler not finding the update file (GitHub #19): updates are now saved to Downloads for AAEnabler installs.
- The in-app update download stalling at full size.
- Turn-by-turn voice silent for one user.
- Updating no longer takes the app out of Android Auto for AAEnabler installs: the new version is saved to Downloads to install with AAEnabler.
- Missing 3D buildings: the "keep the road ahead clear" area no longer cuts through buildings. There's no straight line ahead without a route, and the strip along a route is narrower.
- The old "Search & keys" name on the offline maps page.
- Crash on start on a Teyes CC3 2K (Android 10).
- Black screen on Android Auto.
- Satellite view not working.
- Android Auto support, and the Android Auto layout fixes: the trip card no longer covers the arrow, the speed sign sits in the corner, the card is sized for wide screens, and the map page matches the app.
- 2D / 3D on Android Auto, and the map remembers 2D or 3D between drives (GitHub #17).
- Car types for the arrow (saloon, hatchback, estate, SUV, pickup, van, sports), including the real 3D model.
- Your own picture as the locator.
- Bigger / smaller speedometer and speed limit sign.
- Road name size.
- Voice without road names.
- "Exit" rather than "junction" outside the UK.
- Stop signs on the map with a chime.
- km/h and kilometres.
- Colour for the other route lines.
- Light traffic shown in green (optional, main roads only).
- E85 / bioethanol.
- Live fuel prices in France, Austria and Germany.
- Avoid tolls only in chosen countries.
- Electric car planning: battery, charge level at arrival, and charging stops along the route.
- Easier stops: one-tap fuel, coffee and so on along the route.
- Change Home and Work, and rename favourites.
- Report police, crashes and hazards without a route.
- The Saved & recent list can be folded away or swiped off.
- Offline maps for many more countries, and several areas at once.
- Mapbox key working on a second device (stray spaces in pasted keys are removed).
- The fuel and parking search errors (HTTP 429 / 502) fixed.
- Adding a stop no longer hangs.
- A donate link.
- The whole head unit restarting on a built-in Android 9 unit (Snapdragon 8155).
- Crashes on a Teyes CC3 (Android 10), and freezes on a Pixel 10 Pro XL.
- Black screens on Android Auto and in the app.
- In-app update failing with "No such file or directory" (GitHub #29).
- Android Auto only working when parked.
- Lane guidance: the lane sign over the road, tested on the M27.
- Android Auto: the arrow under the cards (GitHub #28), the map shaking when stopped, the speed sign jumping, and a Report button.
- Zoom remembered between drives (GitHub #24), on the phone and Android Auto separately.
- Your arrow on your side of divided roads.
- Lorry (HGV) routes with the lorry's height, width, length, weight and hazardous goods, and warnings for low bridges and weight limits ahead (luk34dd0, r/coolgithubprojects).
- The next turn in the notification, for car launchers and watches that read navigation notifications (Nubber99, Agama launcher). Agama's own list of supported apps is up to its developer.
- Mapbox 401 errors: Mapbox is asked what's wrong with the token, and the map isn't switched off when it's fine.
- Routes from TomTom and Mapbox compared, quicker one first.
- Clock times follow the device's 12 / 24-hour setting.
- Routes stopping at a border with no signal.
- "No signal" when the head unit's date and time are wrong.
- Town names for every country in a shared offline pack (Singapore and Brunei with Malaysia).
- Offline routes failing with "Exceeded maximum circumference for exclude_polygons".
- README: Android Auto install guide (KingInstaller), keys, privacy and troubleshooting.
- Smaller offline packs: Singapore and Brunei on their own, regions inside big countries, and "Near you" in Choose an area (GitHub #31). Region packs still need building with the Build country packs workflow.
- Flock and other number-plate (ALPR) cameras on the map.
- Clock setting (12 / 24-hour), North Up remembered (GitHub #27), zoom out further (GitHub #33).
- Android Auto cards white on a dark map at dusk; arrow lag after stopping and battery / heat (GitHub #34).
- Map on the driver's display (Android Auto instrument cluster, on cars that offer it). Untested on a real car yet.
- Clock row in Android Auto's settings; Search tile on the Android Auto Report screen.
- Speed position: bottom, middle or top (Nubber99).
- Electric cars: petrol Fuel button hidden unless wanted; trip card fits in portrait; metric units in settings (vastila).
