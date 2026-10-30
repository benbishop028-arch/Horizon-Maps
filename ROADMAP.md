# Horizon Nav: to-do list

Rebuilt on 29 October 2026 from the Reddit threads (r/Androidheadunits and r/AndroidAuto), GitHub issues and messages passed on directly, and checked against the code. The numbers are new; they don't match the older list.

Move items to **Done** as they ship, and add new requests at the bottom of the right list.

## Bugs still open

1. **The whole head unit freezes and restarts on a built-in Android 9 unit (Snapdragon 8155).** *Pending.* The app now spots the unit restarting and steps down to safer drawing. The first step draws the map in the app's own window instead of straight onto the screen. The next feedback report from that unit will show memory and what happened just before the restart. The user has also been asked to try turning off Fast map drawing.
2. **The in-app update download stops at full size and never finishes.** Two reports from before the update download was reworked. Needs confirming.
3. **Turn-by-turn voice silent for one user while the speed limit alerts still spoke.** A voice channel setting and a message when the voice fails have been added since. Needs confirming.

## Features requested

1. **Custom default zoom.** Set how close the map sits when you're not on a route, and have auto zoom treat that as its normal level. It would still zoom in for junctions and out at speed, but around your setting. Plus a way to reset it to the standard zoom. Possible approach: "Use the current zoom as default" (zoom where you like it, then tap) and a slider in Settings > Map look. One driver also found it a bit too zoomed in on their commute.
2. **Change the app's language.** Asked on r/Androidheadunits. Parked for now.
3. **Remember where you parked.** Mainly for the phone / Android Auto version: save the spot when Android Auto disconnects and show it on the phone. It was removed from the head-unit app, which lives in the car.
4. **Send a destination from your phone so the head unit starts navigating when it turns on.** Today this person does it through Google Maps history syncing.
5. **Zoom out to a top-down view when you've been stopped a while** (at long lights or in a queue), so you can see the traffic round you, as TomTom does.
6. **Import saved places from Google Maps** (from a Google Takeout file).
7. **Copy offline map packs from another device.** For example, download on a phone or PC at home and copy them across by USB, for when the car can't reach home Wi-Fi.
8. **Smaller offline packs**, such as Singapore on its own instead of Singapore, Malaysia and Brunei together, and town names for every country in a shared pack.
9. **Swap the search bar for a search icon**, for people who want even more of the map on show.
10. **Choose where your arrow sits on screen.** Requested on Android Auto, for one car where the centre felt too far left. The automatic centring added since may cover this. Check before building.
11. **Radarbot integration** for speed cameras on the route. Radarbot doesn't offer a public way in, so this may not be possible. The app's own camera data is used instead.
12. **Map options on Android Auto.** Layers such as traffic and satellite, and reports. Android Auto only allows a few buttons of its own, so this needs a different approach there.
13. **Android Auto voice search.** Pressing Android Auto's own mic and asking for a place says "Sorry, I didn't understand". "Navigate to …" with the app named may already work. Needs testing.
14. **Clearer search message with no keys.** When the free map servers are busy and there's no Mapbox key, the message could add "Add a Mapbox key in Settings > Keys & accounts for more reliable searches".

## Can't be fixed in the app

- **The app not showing up in Android Auto on some phones**, especially GrapheneOS and LineageOS, or without installing through AAEnabler. Android Auto only lists unofficial apps under certain conditions; the app shows a note about AAEnabler.
- **Some speed limits are wrong.** They come from the map data (OpenStreetMap / Mapbox). Wrong limits can be corrected on openstreetmap.org.
- **Waze-style live police alerts.** These need lots of users reporting nearby. The app's own reports (police, crash, hazard) only show on your own map.

## Done (from these requests)

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
