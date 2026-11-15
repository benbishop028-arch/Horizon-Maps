# Horizon Nav for iOS and CarPlay

This is a native iOS port of Horizon Nav, not a binary conversion of the Android app. It provides routes and search from TomTom, Mapbox or Apple Maps, offline maps and offline routing, spoken guidance, and CarPlay navigation with lanes, speed limits and camera warnings.

## Open and run

1. Install XcodeGen: `brew install xcodegen`.
2. From this directory, run `xcodegen generate`.
3. Open `HorizonNav.xcodeproj`, select a development team, then run on a physical iPhone.

## CarPlay requirements

Apple must grant the Maps entitlement to the team before the app can be installed as a CarPlay navigation app. The checked-in entitlement records the intended capability, but it cannot grant the permission itself. Use a CarPlay-enabled provisioning profile and test with the CarPlay Simulator or a vehicle.

Set your own team in `project.yml` (`DEVELOPMENT_TEAM`) or in Xcode's Signing tab. Until Apple grants the CarPlay Maps entitlement (request at developer.apple.com/contact/carplay), automatic signing fails with "Entitlement com.apple.developer.carplay-maps not found". To install the phone app meanwhile, build with a development profile for your team and without the entitlement:

```
xcodebuild -project HorizonNav.xcodeproj -scheme HorizonNav -destination 'id=<device udid>' build \
  CODE_SIGN_STYLE=Manual PROVISIONING_PROFILE_SPECIFIER="<your development profile>" CODE_SIGN_ENTITLEMENTS=""
```

After the grant, enable "CarPlay Navigation" on the App ID under Additional Capabilities, then build normally with `-allowProvisioningUpdates`.

## Provider keys

The app has no keys built in. Without any, Apple Maps plans routes and searches. To use TomTom or Mapbox, as the Android app does:

- **At build time:** copy `Config/Secrets.example.xcconfig` to `Config/Secrets.xcconfig` and fill in `HN_MAPBOX_ACCESS_TOKEN` (the Mapbox default public token, `pk.`) and `HN_TOMTOM_API_KEY` (a TomTom key with Routing and Search). `Secrets.xcconfig` is ignored by git; `Config/HorizonNav.xcconfig` includes it only when it exists, and Info.plist passes the values to the app.
- **In the app:** Settings (the gear button) has a field for each. A key typed there is stored in the Keychain and wins over the build's.

The service is chosen the way the Android app chooses: Settings > Routes from (Automatic, TomTom or Mapbox) picks the first, the other keyed service is the backup when one refuses or is down, and Apple Maps is the keyless fallback. With both keys on Automatic, a new route is asked of both at once and the other service's route goes first when it is clearly quicker (2 minutes, or 5% on longer trips), or is offered as another choice. Search uses TomTom with a key (topped up with Mapbox when it finds little), Mapbox with only a token, and Apple's search otherwise.

## Navigation

- `Routing.swift`: `RoutingProvider` is the routing boundary. `FailoverRoutingProvider` uses the offline providers for connectivity failures only (`RoutingFallbackPolicy`, ported from Android), and straight away when the phone has no connection.
- `TomTomProvider.swift`, `MapboxProvider.swift`: TomTom Routing (`calculateRoute`, with alternatives, traffic, lane and speed limit sections) and Search, Mapbox Directions (`driving-traffic`, with lanes, `maxspeed`, traffic delay and junction views), Search Box and Geocoding v6. `ProviderRouting.swift` chooses between them.
- `RouteGuidance.swift` and `NavigationPolicy.swift`: turn-by-turn progress, arrival, and off-route strikes using the Android thresholds.
- `NavigationStore.swift`: shared trip state for the phone and CarPlay, including route preview, alternative selection, the reroute lifecycle, speed limit, current road and camera warnings.
- `CarPlaySceneDelegate.swift`, `CarPlayManeuvers.swift` and `CarPlayMapViewController.swift`: CarPlay search, trip previews with one route choice per alternative, `CPNavigationSession` manoeuvres and estimates, and pause for rerouting.
- Debug builds accept `-HNCarPlayAutoTest "<query>"`, which runs search, preview, alternative selection and trip start once the CarPlay scene connects.

## Offline

Offline maps are under the download button on the map.

- **Map areas** (`OfflineMaps.swift`, `OpenMapView.swift`): MapLibre Native (Swift Package Manager) draws OpenFreeMap's OpenStreetMap tiles, the same tile source as the Android app's free map. Save the area shown (a bounding box from a few zoom levels out to zoom 14) or the strip along the current route, for the day and night styles; areas are listed with progress and size and swiped away to delete. The map switches to this map automatically with no signal, or always when Settings > Map is OpenStreetMap.
- **Offline routing** (`OfflineRouting.swift`, `PackCatalog.swift`): real on-device routing. It downloads the Android app's Valhalla packs (`valhalla.json`, `admins.sqlite`, `tiles.tar` whole or in parts, and `cameras.tsv`) from the same GitHub releases, for the same list of countries and regions, and routes on them with the same engine library (valhalla-mobile 0.6.4). With no signal, new routes and reroutes inside a downloaded area are planned on the phone. A trip whose destination is outside every pack stops at the pack's edge, as on Android. Trips across two packs are not joined at the border yet.
- **Saved route** (`RouteCache.swift`): the last planned routes are kept on disk with their geometry, manoeuvres, lanes and limits. With no signal and no pack, guidance carries on along them and a reroute rejoins them from where the car is (within 250 m), and after a restart the map offers to resume the trip. This cannot find a new road: off the saved routes without a pack, there is no route until the signal returns.
- Live traffic, online search and the camera lookup need a connection. Pack downloads run while the app is open.

## Spoken guidance

`VoiceGuidance.swift` speaks with `AVSpeechSynthesizer` at the Android distances: a heads-up between 1.7 and 1.1 km above 18 m/s, an early lane call about 1.5 km (a mile) out on fast roads, the main call between 520 and 200 m with the lane advice, and the final call within 120 m. Each is said once per manoeuvre. The audio session uses the playback category, the voice prompt mode, and the duck-others and interrupt-spoken-audio options, so music dips and podcasts pause on the phone and in CarPlay. The voice follows the app's language and the region's units. The speaker button (phone and CarPlay) mutes; Settings also has alerts only.

## CarPlay and head-unit features

Ported from Android Auto (`car/CarNav.kt`, `car/Cluster.kt`):

- Lane guidance: the Android lane picture is drawn as a symbol-only row under the turn card, and on iOS 17.4 and later the lanes are also sent as `CPLaneGuidance` for the cluster and head-up display.
- Manoeuvres carry instruction variants from long to short, the roundabout exit or motorway exit number, and on iOS 17.4 and later the manoeuvre type, junction angle, traffic side, road name and manoeuvre state. Mapbox junction views are shown as the junction image when the account has them.
- Speed camera warnings (OpenStreetMap through Overpass, and the pack's `cameras.tsv` offline) appear as a `CPNavigationAlert` and are spoken, 500 m ahead or about 18 seconds at speed; off in France, Germany and Switzerland, and in Settings.
- The posted limit (from TomTom or Mapbox routes) and the current speed are drawn on the car map and the phone, turning red over the limit.
- `CPTravelEstimates` for the next manoeuvre and the whole trip.
- Day and night: follow the car, the phone, or always day or night (Settings > CarPlay).
- The instrument cluster scene shows the map behind the wheel on cars that offer it, honouring the car's speed limit setting.

Not possible in CarPlay: Android's own turn and trip cards drawn over the map (CarPlay draws its own), a speed limit field in CarPlay's templates (there is none, so the app draws it on its map), sound alerts other than speech (no chime API besides audio playback), and custom map action buttons beyond four. Android's report buttons, fuel and charger searches, weather and lorry routes are not part of this port.
