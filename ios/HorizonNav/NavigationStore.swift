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
    }

    @Published private(set) var phase: Phase = .idle
    @Published private(set) var destination: MKMapItem?
    @Published private(set) var routeOptions: [RouteOption] = []
    @Published private(set) var selectedRouteIndex = 0
    @Published private(set) var guidance: GuidanceUpdate?
    @Published private(set) var lastError: String?
    @Published var searchText = ""

    let events = PassthroughSubject<TripEvent, Never>()
    let locationManager = CLLocationManager()
    var routing: RoutingProvider

    private var engine: RouteGuidance?
    private let log = Logger(subsystem: "com.horizonnav.ios", category: "guidance")

    static let fallbackCoordinate = CLLocationCoordinate2D(latitude: 51.5072, longitude: -0.1276)

    var selectedRoute: RouteOption? {
        routeOptions.indices.contains(selectedRouteIndex) ? routeOptions[selectedRouteIndex] : nil
    }

    var currentCoordinate: CLLocationCoordinate2D {
        locationManager.location?.coordinate ?? Self.fallbackCoordinate
    }

    init(routing: RoutingProvider = FailoverRoutingProvider(online: MapKitRoutingProvider(), offline: nil)) {
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
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = query
        request.region = MKCoordinateRegion(center: currentCoordinate, span: MKCoordinateSpan(latitudeDelta: 0.35, longitudeDelta: 0.35))
        do { return try await MKLocalSearch(request: request).start().mapItems } catch { return [] }
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
        phase = .guiding
        locationManager.allowsBackgroundLocationUpdates = true
        if let location = locationManager.location { process(location) } else { guidance = engine?.update(with: CLLocation(latitude: route.coordinates[0].latitude, longitude: route.coordinates[0].longitude)) }
        events.send(.guidanceStarted(route))
        log.info("Guidance started with \(route.steps.count, privacy: .public) manoeuvres")
    }

    func stopNavigating() {
        stopGuidance(notify: true)
        routeOptions = []
        destination = nil
        phase = .idle
    }

    private func stopGuidance(notify: Bool) {
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
        switch update.status {
        case .arrived:
            phase = .arrived
            locationManager.allowsBackgroundLocationUpdates = false
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
            engine = RouteGuidance(route: route)
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
