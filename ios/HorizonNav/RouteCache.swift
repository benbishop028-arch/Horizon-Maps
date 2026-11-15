import CoreLocation
import Foundation
import MapKit

/// The last planned routes with their geometry, manoeuvres, lanes and limits, kept on disk so guidance can carry on
/// with no network: after a restart mid-trip (Android "resumes your trip"), and when a reroute finds no signal and no
/// offline pack, the car rejoins the saved route.
struct SavedTrip: Codable, Equatable {
    var destinationName: String
    var destinationLatitude: Double
    var destinationLongitude: Double
    var savedAt: Date
    var routes: [StoredRoute]

    var destination: CLLocationCoordinate2D { CLLocationCoordinate2D(latitude: destinationLatitude, longitude: destinationLongitude) }

    var destinationItem: MKMapItem {
        let item = MKMapItem(placemark: MKPlacemark(coordinate: destination))
        item.name = destinationName
        return item
    }
}

struct StoredRoute: Codable, Equatable {
    var name: String
    var distance: Double
    var expectedTravelTime: Double
    var coordinates: [[Double]]
    var steps: [RouteStep]
    var advisories: [String]
    var trafficDelay: Double
    var laneSections: [LaneSection]
    var speedLimits: [SpeedLimitSection]

    init(_ route: RouteOption) {
        name = route.name
        distance = route.distance
        expectedTravelTime = route.expectedTravelTime
        coordinates = route.coordinates.map { [$0.latitude, $0.longitude] }
        steps = route.steps
        advisories = route.advisories
        trafficDelay = route.trafficDelay
        laneSections = route.laneSections
        speedLimits = route.speedLimits
    }

    func option(source: RouteSource) -> RouteOption {
        RouteOption(name: name, distance: distance, expectedTravelTime: expectedTravelTime,
                    coordinates: coordinates.compactMap { $0.count == 2 ? CLLocationCoordinate2D(latitude: $0[0], longitude: $0[1]) : nil },
                    steps: steps, advisories: advisories, source: source, trafficDelay: trafficDelay,
                    laneSections: laneSections, speedLimits: speedLimits)
    }
}

enum RouteCache {
    static var fileURL: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("saved-trip.json")
    }

    static func save(destination: MKMapItem, routes: [RouteOption], to url: URL = fileURL) {
        let coordinate = destination.placemark.coordinate
        let trip = SavedTrip(destinationName: destination.name ?? String(localized: "Destination"),
                             destinationLatitude: coordinate.latitude, destinationLongitude: coordinate.longitude,
                             savedAt: Date(), routes: routes.map(StoredRoute.init))
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? JSONEncoder().encode(trip).write(to: url, options: .atomic)
    }

    static func load(from url: URL = fileURL) -> SavedTrip? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(SavedTrip.self, from: data)
    }

    static func clear(at url: URL = fileURL) { try? FileManager.default.removeItem(at: url) }
}

/// Offline guidance from the saved routes: when the destination matches and the car is on or near one of them, the rest of
/// that route is returned from the car's position, so turn-by-turn guidance continues with no network. It cannot plan a
/// new road: off every saved route, it reports that no offline routing is available.
struct CachedRouteProvider: RoutingProvider {
    let name = "Saved route"
    var load: () -> SavedTrip? = { RouteCache.load() }
    /// How far the car may be from a saved route to rejoin it.
    static let rejoinDistance: CLLocationDistance = 250
    /// How close the requested destination must be to the saved one.
    static let destinationMatch: CLLocationDistance = 150

    func routes(for request: RouteRequest) async throws -> [RouteOption] {
        guard let trip = load(),
              RouteGeometry.distance(trip.destination, request.destination.placemark.coordinate) <= Self.destinationMatch else {
            throw RoutingError.offlineUnavailable
        }
        let source = RouteSource.offline(String(localized: "Saved route"))
        let candidates = trip.routes.map { $0.option(source: source) }.compactMap { route -> (RouteOption, RouteGeometry.Projection)? in
            guard let p = RouteGeometry.project(request.origin, onto: route), p.distance <= Self.rejoinDistance else { return nil }
            return (route, p)
        }
        guard let (route, projection) = candidates.min(by: { $0.1.distance < $1.1.distance }) else { throw RoutingError.offlineUnavailable }
        return [route.trimmed(from: projection)]
    }
}

extension RouteOption {
    /// The rest of the route from a point on it: the geometry from there, the manoeuvres still ahead, and lanes, limits,
    /// distance and time scaled to what is left.
    func trimmed(from projection: RouteGeometry.Projection) -> RouteOption {
        let i = min(max(projection.segmentIndex, 0), max(coordinates.count - 2, 0))
        let progress = projection.progress
        let a = coordinates[i], b = coordinates[min(i + 1, coordinates.count - 1)]
        let span = cumulative[min(i + 1, cumulative.count - 1)] - cumulative[i]
        let t = span > 0 ? min(max((progress - cumulative[i]) / span, 0), 1) : 0
        let start = CLLocationCoordinate2D(latitude: a.latitude + (b.latitude - a.latitude) * t, longitude: a.longitude + (b.longitude - a.longitude) * t)
        var points = [start] + coordinates[(i + 1)...]
        if points.count < 2 { points.append(b) }
        let remaining = length > 0 ? max(length - progress, 0) / length : 0
        let steps = steps.filter { $0.startProgress > progress }.map { step -> RouteStep in
            var s = step
            s.startProgress -= progress
            return s
        }
        let lanes = laneSections.filter { $0.end > progress }.map { LaneSection(start: max($0.start - progress, 0), end: $0.end - progress, lanes: $0.lanes) }
        let limits = speedLimits.filter { $0.end > progress }.map { SpeedLimitSection(start: max($0.start - progress, 0), end: $0.end - progress, metersPerSecond: $0.metersPerSecond) }
        return RouteOption(name: name, distance: distance * remaining, expectedTravelTime: expectedTravelTime * remaining,
                           coordinates: points, steps: steps, advisories: advisories, source: source,
                           trafficDelay: 0, laneSections: lanes, speedLimits: limits)
    }
}
