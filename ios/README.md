# Horizon Nav for iOS and CarPlay

This is a native iOS foundation for Horizon Nav, not a binary conversion of the Android app. It provides a MapKit map, on-device place search, driving route calculation, location permission, and a CarPlay map scene.

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

## Navigation

- `Routing.swift`: `RoutingProvider` is the routing boundary. `MapKitRoutingProvider` asks Apple Maps for the route and its alternatives. `FailoverRoutingProvider` takes an optional offline provider and uses it only for connectivity failures (`RoutingFallbackPolicy`, ported from Android). No offline provider ships yet, so offline routing is not available.
- `RouteGuidance.swift` and `NavigationPolicy.swift`: turn-by-turn progress, arrival, and off-route strikes using the Android thresholds. Covered by `HorizonNavTests`.
- `NavigationStore.swift`: shared trip state for the phone and CarPlay, including route preview, alternative selection and the reroute lifecycle.
- `CarPlaySceneDelegate.swift` and `CarPlayMapViewController.swift`: CarPlay search, trip previews with one route choice per alternative, `CPNavigationSession` manoeuvres and estimates, and pause for rerouting. The car map shows Apple Maps traffic.
- Debug builds accept `-HNCarPlayAutoTest "<query>"`, which runs search, preview, alternative selection and trip start once the CarPlay scene connects.

Mapbox or TomTom keys, offline packs, spoken guidance and the Android-only head-unit features are not ported.
