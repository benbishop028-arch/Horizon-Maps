import Combine
import CoreLocation
import MapKit
import os

/// Trip state shared by the phone UI and the CarPlay scene: planning, alternatives,
/// turn-by-turn guidance and the reroute lifecycle.
@MainActor
final class NavigationStore: NSObject, ObservableObject {
    static let shared = NavigationStore()

    enum Phase: Equatable {
        case idle
        case planning
        case previewing
        case guiding
        case rerouting
        case arrived
    }

    enum TripEvent {
        case guidanceStarted(RouteOption)
        case rerouteStarted
        case rerouteFinished(RouteOption)
        case rerouteFailed
        case arrived
        case ended
        case alert(NavigationAlert)
    }

    /// A warning for the driver (Android `AlertInfo`): a speed camera ahead.
    struct NavigationAlert: Equatable {
        var kind: String
        var title: String
        var detail: String
        var symbol: String
    }

    @Published private(set) var phase: Phase = .idle
    @Published private(set) var destination: MKMapItem?
    @Published private(set) var routeOptions: [RouteOption] = []
    @Published private(set) var selectedRouteIndex = 0
    @Published private(set) var guidance: GuidanceUpdate?
    @Published private(set) var lastError: String?
    /// The posted limit where the car is, in metres per second, when the route carries limits.
    @Published private(set) var speedLimit: Double?
    @Published private(set) var currentSpeed: Double = 0
    /// The road the car is on: the road of the last manoeuvre passed.
    @Published private(set) var currentRoad = ""
    @Published private(set) var activeAlert: NavigationAlert?
    @Published var searchText = ""

    let events = PassthroughSubject<TripEvent, Never>()
    let locationManager = CLLocationManager()
    var routing: RoutingProvider

    private var engine: RouteGuidance?
    private var voicePlanner = VoicePromptPlanner()
    private var cameras: [PlacedCamera] = []
    private var camerasRouteID: UUID?
    private var alertedCameras: Set<SpeedCamera> = []
    private var countryCode: String?
    private let log = Logger(subsystem: "com.horizonnav.ios", category: "guidance")

    static let fallbackCoordinate = CLLocationCoordinate2D(latitude: 51.5072, longitude: -0.1276)

    var selectedRoute: RouteOption? {
        routeOptions.indices.contains(selectedRouteIndex) ? routeOptions[selectedRouteIndex] : nil
    }

    var currentCoordinate: CLLocationCoordinate2D {
        locationManager.location?.coordinate ?? Self.fallbackCoordinate
    }

    var placeSearch = PlaceSearchService()

    /// The trip kept on the phone for guidance with no network, offered again after a restart.
    @Published private(set) var savedTrip: SavedTrip? = RouteCache.load()

    init(routing: RoutingProvider = FailoverRoutingProvider(
        online: KeyedRoutingProvider(),
        offline: OfflineRoutingChain(providers: [ValhallaRoutingProvider(), CachedRouteProvider()]),
        isOnline: { NetworkMonitor.shared.isOnlineNow })) {
        self.routing = routing
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        locationManager.activityType = .automotiveNavigation
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation()
    }

    func search(_ query: String) async -> [MKMapItem] {
        guard !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return [] }
        return await placeSearch.search(query, near: currentCoordinate)
    }

    /// Plans routes to a destination and enters route preview with the alternatives.
    func navigate(to item: MKMapItem) async {
        stopGuidance(notify: phase == .guiding || phase == .rerouting)
        phase = .planning
        lastError = nil
        do {
            let options = try await routing.routes(for: RouteRequest(origin: currentCoordinate, destination: item, heading: currentHeading))
            destination = item
            routeOptions = options
            selectedRouteIndex = 0
            phase = .previewing
            saveTrip(options)
            log.info("Planned \(options.count, privacy: .public) route option(s)")
        } catch {
            lastError = error.localizedDescription
            routeOptions = []
            destination = nil
            phase = .idle
            log.error("Route planning failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    func selectRoute(_ index: Int) {
        guard routeOptions.indices.contains(index) else { return }
        selectedRouteIndex = index
        log.info("Selected route option \(index, privacy: .public)")
    }

    func startGuidance() {
        guard let route = selectedRoute, phase == .previewing || phase == .arrived else { return }
        engine = RouteGuidance(route: route)
        voicePlanner.reset()
        alertedCameras.removeAll()
        loadCameras(for: route)
        phase = .guiding
        locationManager.allowsBackgroundLocationUpdates = true
        if let location = locationManager.location { process(location) } else { guidance = engine?.update(with: CLLocation(latitude: route.coordinates[0].latitude, longitude: route.coordinates[0].longitude)) }
        events.send(.guidanceStarted(route))
        log.info("Guidance started with \(route.steps.count, privacy: .public) manoeuvres")
    }

    /// Keeps freshly planned routes for offline guidance. A route rebuilt from the saved trip is not saved over it.
    private func saveTrip(_ options: [RouteOption]) {
        guard let destination, let first = options.first else { return }
        if case .offline(let label) = first.source, label == String(localized: "Saved route") { return }
        RouteCache.save(destination: destination, routes: options)
        savedTrip = RouteCache.load()
    }

    /// Plans the saved trip again: online when there is signal, otherwise from the offline pack or the saved route itself.
    func resumeSavedTrip() async {
        guard let trip = savedTrip else { return }
        await navigate(to: trip.destinationItem)
    }

    func forgetSavedTrip() {
        RouteCache.clear()
        savedTrip = nil
    }

    func stopNavigating() {
        stopGuidance(notify: true)
        routeOptions = []
        destination = nil
        phase = .idle
    }

    /// Speaks the call-outs due for the next manoeuvre.
    private func announce(_ update: GuidanceUpdate, speed: Double) {
        guard let route = selectedRoute,
              let index = update.upcomingStepIndex, route.steps.indices.contains(index) else { return }
        let step = route.steps[index]
        let fast = Self.isFastRoad(step: step, route: route, progress: update.progress)
        let lanes = route.laneSection(forStep: index, progress: update.progress, fastRoad: fast)
        for prompt in voicePlanner.prompts(stepIndex: index, step: step, distance: update.distanceToManeuver, speed: speed,
                                           lanes: lanes, fastRoad: fast, imperial: AppLocale.usesImperialDistances) {
            VoiceGuidance.shared.speak(prompt)
        }
    }

    /// Motorways and dual carriageways, where lanes are called much earlier: a numbered exit, or a limit of 80 km/h or more.
    nonisolated static func isFastRoad(step: RouteStep, route: RouteOption, progress: CLLocationDistance) -> Bool {
        !step.exitNumber.isEmpty || (route.speedLimit(at: progress) ?? 0) >= 80 / 3.6
    }

    /// Speed limit, current speed, current road and camera warnings for one fix.
    private func trackRoadState(_ update: GuidanceUpdate, location: CLLocation) {
        guard let route = selectedRoute else { return }
        currentSpeed = max(location.speed, 0)
        speedLimit = route.speedLimit(at: update.progress)
        if let index = update.upcomingStepIndex, index > 0 { currentRoad = route.steps[index - 1].street } else if update.upcomingStepIndex == 0 { currentRoad = "" }
        guard camerasEnabled, let warning = SpeedCameras.warning(cameras, progress: update.progress, speed: currentSpeed,
                                                                 heading: location.course >= 0 ? location.course : nil),
              alertedCameras.insert(warning.0.camera).inserted else { return }
        let ahead = warning.1
        let distance = Measurement(value: ahead, unit: UnitLength.meters).formatted(.measurement(width: .abbreviated, usage: .road))
        let alert = NavigationAlert(kind: "camera", title: String(localized: "Speed camera ahead"), detail: distance, symbol: "camera.fill")
        activeAlert = alert
        events.send(.alert(alert))
        VoiceGuidance.shared.speak(VoicePrompt(key: "camera", text: alert.title, isAlert: true))
        log.info("Speed camera warning at \(Int(ahead), privacy: .public) m")
        Task { [weak self] in
            try? await Task.sleep(for: .seconds(8))
            if self?.activeAlert == alert { self?.activeAlert = nil }
        }
    }

    /// Camera warnings follow Settings, and stay off where they are against the law.
    private var camerasEnabled: Bool {
        AppSettings.cameraWarnings && !SpeedCameras.bannedCountries.contains(countryCode ?? "")
    }

    /// Cameras along a new route: online from OpenStreetMap, and from the offline packs that cover it.
    private func loadCameras(for route: RouteOption) {
        guard camerasRouteID != route.id else { return }
        camerasRouteID = route.id
        cameras = []
        if let here = locationManager.location {
            CLGeocoder().reverseGeocodeLocation(here) { [weak self] placemarks, _ in
                let code = placemarks?.first?.isoCountryCode
                Task { @MainActor in if let code { self?.countryCode = code } }
            }
        }
        let lats = route.coordinates.map(\.latitude), lons = route.coordinates.map(\.longitude)
        guard let south = lats.min(), let north = lats.max(), let west = lons.min(), let east = lons.max() else { return }
        Task { [weak self] in
            var found: [SpeedCamera] = []
            for pack in OfflinePackStorage.installedPacks() {
                if let text = try? String(contentsOf: pack.camerasURL, encoding: .utf8) {
                    found += SpeedCameras.parsePackFile(text, south: south, west: west, north: north, east: east)
                }
            }
            if NetworkMonitor.shared.isOnlineNow, let online = try? await SpeedCameras.fetch(along: route) { found += online }
            let placed = SpeedCameras.place(found, on: route)
            guard let self, self.camerasRouteID == route.id else { return }
            self.cameras = placed
            self.log.info("\(placed.count, privacy: .public) speed camera(s) on the route")
        }
    }

    private func stopGuidance(notify: Bool) {
        VoiceGuidance.shared.stop()
        speedLimit = nil
        currentRoad = ""
        activeAlert = nil
        cameras = []
        camerasRouteID = nil
        engine = nil
        guidance = nil
        locationManager.allowsBackgroundLocationUpdates = false
        if notify { events.send(.ended) }
    }

    private var currentHeading: CLLocationDirection? {
        guard let course = locationManager.location?.course, course >= 0 else { return nil }
        return course
    }

    func process(_ location: CLLocation) {
        guard phase == .guiding, let engine else { return }
        let update = engine.update(with: location)
        guidance = update
        announce(update, speed: max(location.speed, 0))
        trackRoadState(update, location: location)
        switch update.status {
        case .arrived:
            phase = .arrived
            locationManager.allowsBackgroundLocationUpdates = false
            forgetSavedTrip()
            log.info("Arrived")
            events.send(.arrived)
        case .rerouteNeeded:
            log.info("Off route, rerouting")
            Task { await reroute(from: location) }
        case .onRoute, .offRoute:
            break
        }
    }

    /// Requests a fresh route from the current position, keeping the old one if that fails.
    func reroute(from location: CLLocation) async {
        guard phase == .guiding, let destination else { return }
        phase = .rerouting
        events.send(.rerouteStarted)
        let heading = location.course >= 0 ? location.course : nil
        do {
            let options = try await routing.routes(for: RouteRequest(origin: location.coordinate, destination: destination, heading: heading, wantsAlternatives: false))
            guard phase == .rerouting, let route = options.first else { return }
            routeOptions = [route]
            selectedRouteIndex = 0
            saveTrip([route])
            engine = RouteGuidance(route: route)
            voicePlanner.reset()
            loadCameras(for: route)
            phase = .guiding
            guidance = engine?.update(with: location)
            log.info("Reroute finished, \(route.steps.count, privacy: .public) manoeuvres")
            events.send(.rerouteFinished(route))
        } catch {
            guard phase == .rerouting else { return }
            phase = .guiding
            lastError = error.localizedDescription
            log.error("Reroute failed: \(error.localizedDescription, privacy: .public)")
            events.send(.rerouteFailed)
        }
    }
}

extension NavigationStore: CLLocationManagerDelegate {
    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        MainActor.assumeIsolated { self.process(location) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) { }
}
