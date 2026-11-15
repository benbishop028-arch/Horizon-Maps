import CoreLocation
import MapKit

/// One manoeuvre on a route. The manoeuvre happens where the step starts.
struct RouteStep: Equatable, Codable {
    var instruction: String
    var notice: String?
    /// Distance driven after the manoeuvre, until the next one.
    var distance: CLLocationDistance
    /// Distance along the route geometry where the manoeuvre happens.
    var startProgress: CLLocationDistance
    /// Signed turn angle at the manoeuvre, positive to the right. Nil when the geometry cannot tell.
    var turnAngle: Double?
    var isArrival: Bool
    /// The provider's manoeuvre kind, with the Android app's names. Unknown for Apple Maps routes.
    var maneuver: Maneuver = .unknown
    /// The road the manoeuvre leads onto, when the provider names it.
    var street: String = ""
    var roundaboutExit: Int = 0
    var exitNumber: String = ""
    /// Signpost text ("towards Bristol"), when the provider gives it.
    var signpost: String = ""
    /// Mapbox's picture of the junction, when the account has junction views.
    var junctionImageURL: URL?
}

enum RouteSource: Equatable, Codable {
    case online(String)
    case offline(String)
}

/// A provider-neutral route, so the guidance engine and both UIs never depend on one routing backend.
struct RouteOption: Identifiable, Equatable {
    let id = UUID()
    var name: String
    var distance: CLLocationDistance
    var expectedTravelTime: TimeInterval
    var coordinates: [CLLocationCoordinate2D]
    /// Cumulative geometric distance at each coordinate.
    var cumulative: [CLLocationDistance]
    var steps: [RouteStep]
    var advisories: [String]
    var source: RouteSource
    /// Seconds of the travel time caused by traffic, when the provider reports it.
    var trafficDelay: TimeInterval = 0
    /// Lane layouts along the route, by distance along the geometry.
    var laneSections: [LaneSection] = []
    /// Posted speed limits along the route, by distance along the geometry.
    var speedLimits: [SpeedLimitSection] = []

    var length: CLLocationDistance { cumulative.last ?? 0 }

    static func == (lhs: RouteOption, rhs: RouteOption) -> Bool { lhs.id == rhs.id }

    init(name: String, distance: CLLocationDistance, expectedTravelTime: TimeInterval,
         coordinates: [CLLocationCoordinate2D], steps: [RouteStep], advisories: [String], source: RouteSource,
         trafficDelay: TimeInterval = 0, laneSections: [LaneSection] = [], speedLimits: [SpeedLimitSection] = []) {
        self.trafficDelay = trafficDelay
        self.laneSections = laneSections
        self.speedLimits = speedLimits
        self.name = name
        self.distance = distance
        self.expectedTravelTime = expectedTravelTime
        self.coordinates = coordinates
        self.cumulative = RouteGeometry.cumulativeDistances(coordinates)
        self.steps = steps
        self.advisories = advisories
        self.source = source
    }
}

struct RouteRequest {
    var origin: CLLocationCoordinate2D
    var destination: MKMapItem
    var heading: CLLocationDirection?
    var wantsAlternatives = true
}

/// The injectable routing boundary. Online it is TomTom, Mapbox or Apple Maps; offline it is the on-device
/// Valhalla router (`ValhallaRoutingProvider`) and the saved route (`CachedRouteProvider`).
protocol RoutingProvider {
    var name: String { get }
    func routes(for request: RouteRequest) async throws -> [RouteOption]
}

enum RoutingError: LocalizedError {
    case noRoute
    case offlineUnavailable

    var errorDescription: String? {
        switch self {
        case .noRoute: return String(localized: "No route was found.")
        case .offlineUnavailable: return String(localized: "No offline routing data is installed.")
        }
    }
}

/// Online routing with Apple MapKit. Travel times include current traffic where Apple has it.
struct MapKitRoutingProvider: RoutingProvider {
    let name = "Apple Maps"

    func routes(for request: RouteRequest) async throws -> [RouteOption] {
        let directions = MKDirections.Request()
        directions.source = MKMapItem(placemark: MKPlacemark(coordinate: request.origin))
        directions.destination = request.destination
        directions.transportType = .automobile
        directions.requestsAlternateRoutes = request.wantsAlternatives
        let routes = try await MKDirections(request: directions).calculate().routes
        guard !routes.isEmpty else { throw RoutingError.noRoute }
        return routes.map { Self.option(from: $0, source: .online(name)) }
    }

    static func option(from route: MKRoute, source: RouteSource) -> RouteOption {
        var coordinates: [CLLocationCoordinate2D] = []
        var stepStartIndices: [Int] = []
        for step in route.steps {
            let points = step.polyline.coordinates
            stepStartIndices.append(max(coordinates.count - 1, 0))
            // Consecutive step polylines share their joining point.
            let skipFirst = !coordinates.isEmpty && points.first.map { RouteGeometry.distance(coordinates[coordinates.count - 1], $0) < 1 } == true
            coordinates.append(contentsOf: skipFirst ? Array(points.dropFirst()) : points)
        }
        if coordinates.count < 2 { coordinates = route.polyline.coordinates }
        let cumulative = RouteGeometry.cumulativeDistances(coordinates)
        var steps: [RouteStep] = []
        for (i, step) in route.steps.enumerated() {
            let instruction = step.instructions.trimmingCharacters(in: .whitespacesAndNewlines)
            // MapKit opens with an empty "start here" step that is not a manoeuvre.
            if instruction.isEmpty && i == 0 { continue }
            let index = min(stepStartIndices[i], max(cumulative.count - 1, 0))
            steps.append(RouteStep(
                instruction: instruction,
                notice: step.notice,
                distance: step.distance,
                startProgress: cumulative.isEmpty ? 0 : cumulative[index],
                turnAngle: RouteGeometry.turnAngle(in: coordinates, at: index),
                isArrival: i == route.steps.count - 1
            ))
        }
        return RouteOption(name: route.name, distance: route.distance, expectedTravelTime: route.expectedTravelTime,
                           coordinates: coordinates, steps: steps, advisories: route.advisoryNotices, source: source)
    }
}

/// Tries the online provider first and falls back to an offline provider only for connectivity
/// failures, the same rule as the Android app. With no offline provider injected, connectivity
/// failures surface as `RoutingError.offlineUnavailable`.
struct FailoverRoutingProvider: RoutingProvider {
    var online: RoutingProvider
    var offline: RoutingProvider?
    /// With no connection at all, the offline provider is asked straight away.
    var isOnline: () -> Bool = { true }

    var name: String { online.name }

    func routes(for request: RouteRequest) async throws -> [RouteOption] {
        if !isOnline(), let offline { return try await offline.routes(for: request) }
        do {
            return try await online.routes(for: request)
        } catch {
            guard RoutingFallbackPolicy.shouldUseOffline(error) else { throw error }
            guard let offline else { throw RoutingError.offlineUnavailable }
            return try await offline.routes(for: request)
        }
    }
}

enum RouteGeometry {
    static func distance(_ a: CLLocationCoordinate2D, _ b: CLLocationCoordinate2D) -> CLLocationDistance {
        MKMapPoint(a).distance(to: MKMapPoint(b))
    }

    static func cumulativeDistances(_ coordinates: [CLLocationCoordinate2D]) -> [CLLocationDistance] {
        var result: [CLLocationDistance] = []
        result.reserveCapacity(coordinates.count)
        var total: CLLocationDistance = 0
        for (i, c) in coordinates.enumerated() {
            if i > 0 { total += distance(coordinates[i - 1], c) }
            result.append(total)
        }
        return result
    }

    /// Initial bearing from a to b in degrees clockwise from north.
    static func bearing(_ a: CLLocationCoordinate2D, _ b: CLLocationCoordinate2D) -> Double {
        let lat1 = a.latitude * .pi / 180, lat2 = b.latitude * .pi / 180
        let dLon = (b.longitude - a.longitude) * .pi / 180
        let y = sin(dLon) * cos(lat2)
        let x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (atan2(y, x) * 180 / .pi + 360).truncatingRemainder(dividingBy: 360)
    }

    /// Turn angle at a vertex, measured between points at least `reach` metres before and after it
    /// so tiny geometry segments do not produce false turns.
    static func turnAngle(in coordinates: [CLLocationCoordinate2D], at index: Int, reach: CLLocationDistance = 12) -> Double? {
        guard index > 0, index < coordinates.count - 1 else { return nil }
        let pivot = coordinates[index]
        var before = index - 1
        while before > 0 && distance(coordinates[before], pivot) < reach { before -= 1 }
        var after = index + 1
        while after < coordinates.count - 1 && distance(pivot, coordinates[after]) < reach { after += 1 }
        guard distance(coordinates[before], pivot) > 1, distance(pivot, coordinates[after]) > 1 else { return nil }
        return NavigationPolicy.angleDifference(from: bearing(coordinates[before], pivot), to: bearing(pivot, coordinates[after]))
    }

    /// Nearest point on the route to a location: distance from the route, progress along it, and segment index.
    struct Projection {
        var distance: CLLocationDistance
        var progress: CLLocationDistance
        var segmentIndex: Int
    }

    static func project(_ location: CLLocationCoordinate2D, onto route: RouteOption, segments range: Range<Int>? = nil) -> Projection? {
        let coords = route.coordinates
        guard coords.count >= 2 else { return nil }
        let all = 0..<(coords.count - 1)
        let scan = range.map { $0.clamped(to: all) } ?? all
        let p = MKMapPoint(location)
        var best: Projection?
        for i in scan {
            let a = MKMapPoint(coords[i]), b = MKMapPoint(coords[i + 1])
            let dx = b.x - a.x, dy = b.y - a.y
            let len2 = dx * dx + dy * dy
            let t = len2 > 0 ? min(max(((p.x - a.x) * dx + (p.y - a.y) * dy) / len2, 0), 1) : 0
            let q = MKMapPoint(x: a.x + t * dx, y: a.y + t * dy)
            let d = p.distance(to: q)
            if best == nil || d < best!.distance {
                let along = route.cumulative[i] + (route.cumulative[i + 1] - route.cumulative[i]) * t
                best = Projection(distance: d, progress: along, segmentIndex: i)
            }
        }
        return best
    }
}

extension MKMultiPoint {
    var coordinates: [CLLocationCoordinate2D] {
        var result = [CLLocationCoordinate2D](repeating: kCLLocationCoordinate2DInvalid, count: pointCount)
        getCoordinates(&result, range: NSRange(location: 0, length: pointCount))
        return result
    }
}

extension RouteStep {
    /// SF Symbol for the manoeuvre, from the turn geometry so it does not depend on instruction language.
    var symbolName: String {
        if isArrival { return "flag.checkered" }
        if let symbol = maneuver.symbolName { return symbol }
        guard let angle = turnAngle else { return "arrow.up" }
        switch abs(angle) {
        case ..<20: return "arrow.up"
        case ..<50: return angle > 0 ? "arrow.up.right" : "arrow.up.left"
        case ..<150: return angle > 0 ? "arrow.turn.up.right" : "arrow.turn.up.left"
        default: return angle > 0 ? "arrow.uturn.right" : "arrow.uturn.left"
        }
    }
}
