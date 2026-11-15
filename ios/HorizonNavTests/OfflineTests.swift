import CoreLocation
import Foundation
import MapKit
import Testing
@testable import HorizonNav

private func item(_ lat: Double, _ lon: Double) -> MKMapItem {
    MKMapItem(placemark: MKPlacemark(coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon)))
}

/// The real engine on a real pack. Runs when `HN_VALHALLA_PACK` points at a downloaded pack folder (for example the
/// Singapore pack's valhalla.json, admins.sqlite and tiles.tar), so CI without the 90 MB pack skips it.
struct ValhallaPackTests {
    static let packPath = ProcessInfo.processInfo.environment["HN_VALHALLA_PACK"]

    @Test(.enabled(if: packPath != nil))
    func routesWithNoNetworkOnTheDevice() async throws {
        let folder = URL(fileURLWithPath: try #require(Self.packPath))
        let header = try? Data(contentsOf: folder.appendingPathComponent("header.bin"))
        let bounds = header.flatMap(OfflinePackStorage.bounds(fromPMTilesHeader:))
        let pack = InstalledPack(id: "test-pack", name: "Test pack", folder: folder, bounds: bounds, bytes: 0)
        // Marina Bay Sands to Changi Airport, Singapore.
        let request = RouteRequest(origin: .init(latitude: 1.28340, longitude: 103.86070), destination: item(1.35540, 103.98670))
        let routes = try await ValhallaRoutingProvider(packs: { [pack] }).routes(for: request)
        let route = try #require(routes.first)
        #expect(route.source == .offline(String(localized: "Offline map")))
        #expect(route.length > 15_000 && route.length < 40_000)
        #expect(route.steps.count > 3)
        #expect(route.steps.last?.isArrival == true)
        #expect(route.steps.allSatisfy { $0.maneuver != .unknown })
        if let out = ProcessInfo.processInfo.environment["HN_VALHALLA_FIXTURE_OUT"] {
            let raw = try ValhallaEngines.shared.route(rawRequest: ValhallaRoutingProvider.requestJSON(for: request, language: "en-GB"), pack: pack)
            try raw.write(toFile: out, atomically: true, encoding: .utf8)
        }
    }
}

struct ValhallaParsingTests {
    @Test func realEngineAnswerParses() throws {
        let route = try ValhallaRoutingProvider.parse(try fixture("valhalla-route"), packName: "Singapore")
        #expect(route.coordinates.count > 100)
        #expect(route.steps.count > 3)
        #expect(route.steps.first?.startProgress ?? 0 > 0)
        #expect(route.steps.last?.isArrival == true)
        #expect(route.expectedTravelTime > 600)
        // Steps run forwards along the geometry.
        #expect(zip(route.steps, route.steps.dropFirst()).allSatisfy { $0.startProgress <= $1.startProgress })
    }

    @Test func maneuverTypesMatchAndroid() {
        #expect(ValhallaRoutingProvider.maneuver(type: 10, roundaboutExits: 0) == .turnRight)
        #expect(ValhallaRoutingProvider.maneuver(type: 26, roundaboutExits: 2) == .roundaboutCross)
        #expect(ValhallaRoutingProvider.maneuver(type: 26, roundaboutExits: 5) == .roundaboutBack)
        #expect(ValhallaRoutingProvider.maneuver(type: 27, roundaboutExits: 0) == nil)
    }

    @Test func requestCarriesHeadingAndCosting() throws {
        let request = RouteRequest(origin: .init(latitude: 1.3, longitude: 103.8), destination: item(1.31, 103.81), heading: 370)
        let json = try JSONSerialization.jsonObject(with: Data(ValhallaRoutingProvider.requestJSON(for: request, language: "en-GB").utf8)) as! [String: Any]
        #expect(json["costing"] as? String == "auto")
        let origin = (json["locations"] as! [[String: Any]])[0]
        #expect(origin["heading"] as? Int == 10)
        #expect(origin["heading_tolerance"] as? Int == 60)
    }

    @Test func packHeaderBoundsAndChoice() {
        var header = Data("PMTiles".utf8) + Data([3]) + Data(count: 119)
        func put(_ value: Double, at offset: Int) {
            let v = UInt32(bitPattern: Int32(value * 1e7))
            for i in 0..<4 { header[offset + i] = UInt8((v >> (8 * UInt32(i))) & 0xFF) }
        }
        put(103.6, at: 102); put(1.15, at: 106); put(104.1, at: 110); put(1.48, at: 114)
        let bounds = OfflinePackStorage.bounds(fromPMTilesHeader: header)
        #expect(bounds.map { $0.map { ($0 * 100).rounded() / 100 } } == [1.15, 103.6, 1.48, 104.1])
        let small = InstalledPack(id: "sg", name: "SG", folder: URL(fileURLWithPath: "/"), bounds: bounds, bytes: 0)
        let big = InstalledPack(id: "my", name: "MY", folder: URL(fileURLWithPath: "/"), bounds: [-1, 99, 8, 120], bytes: 0)
        let inside = CLLocationCoordinate2D(latitude: 1.3, longitude: 103.8)
        #expect(OfflinePackStorage.packFor(origin: inside, destination: inside, among: [big, small])?.id == "sg")
        #expect(OfflinePackStorage.packFor(origin: inside, destination: .init(latitude: 3.1, longitude: 101.7), among: [big, small])?.id == "my")
        #expect(OfflinePackStorage.packFor(origin: .init(latitude: 51, longitude: 0), destination: .init(latitude: 51, longitude: 0.1), among: [big, small]) == nil)
    }
}

/// The saved route: guidance continues along the last planned route with no network.
struct CachedRouteGuidanceTests {
    private func savedTrip() throws -> (SavedTrip, RouteOption) {
        let route = try TomTomRoutingProvider.parse(try fixture("tomtom-route"))[0]
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("saved-trip-\(UUID().uuidString).json")
        let destination = item(route.coordinates.last!.latitude, route.coordinates.last!.longitude)
        destination.name = "Horse Guards Avenue"
        RouteCache.save(destination: destination, routes: [route], to: url)
        defer { RouteCache.clear(at: url) }
        return (try #require(RouteCache.load(from: url)), route)
    }

    @Test func savedTripKeepsGeometryManeuversLanesAndLimits() throws {
        let (trip, route) = try savedTrip()
        #expect(trip.destinationName == "Horse Guards Avenue")
        let restored = trip.routes[0].option(source: .offline("Saved route"))
        #expect(restored.coordinates.count == route.coordinates.count)
        #expect(restored.steps == route.steps)
        #expect(restored.laneSections == route.laneSections)
        #expect(restored.speedLimits == route.speedLimits)
    }

    @Test func rejoinsTheSavedRouteFromTheCarsPosition() async throws {
        let (trip, route) = try savedTrip()
        let provider = CachedRouteProvider(load: { trip })
        // Halfway along the first street, 10 m off the line.
        let origin = CLLocationCoordinate2D(latitude: route.coordinates[2].latitude + 0.00009, longitude: route.coordinates[2].longitude)
        let rest = try #require(try await provider.routes(for: RouteRequest(origin: origin, destination: trip.destinationItem)).first)
        #expect(rest.source == .offline(String(localized: "Saved route")))
        #expect(abs(rest.length - (route.length - route.cumulative[2])) < 3)
        #expect(rest.steps.first?.maneuver == .turnRight)
        #expect(abs(rest.steps[0].startProgress - (route.steps[0].startProgress - route.cumulative[2])) < 3)

        // The engine guides along it like any other route.
        let guidance = RouteGuidance(route: rest)
        let update = guidance.update(with: CLLocation(coordinate: rest.coordinates[1], altitude: 0, horizontalAccuracy: 5, verticalAccuracy: 5,
                                                      course: 90, speed: 10, timestamp: Date()))
        #expect(update.status == .onRoute)
        #expect(update.upcomingStepIndex == 0)
        #expect(rest.speedLimit(at: 10) != nil)
    }

    @Test func offTheSavedRouteOrElsewhereIsUnavailable() async throws {
        let (trip, route) = try savedTrip()
        let provider = CachedRouteProvider(load: { trip })
        let far = CLLocationCoordinate2D(latitude: route.coordinates[2].latitude + 0.01, longitude: route.coordinates[2].longitude)
        await #expect(throws: RoutingError.self) { try await provider.routes(for: RouteRequest(origin: far, destination: trip.destinationItem)) }
        await #expect(throws: RoutingError.self) {
            try await provider.routes(for: RouteRequest(origin: route.coordinates[1], destination: item(52, 0)))
        }
    }

    @Test func noSignalGoesStraightToOffline() async throws {
        let (trip, route) = try savedTrip()
        struct Online: RoutingProvider {
            let name = "online"
            func routes(for request: RouteRequest) async throws -> [RouteOption] { Issue.record("online asked with no signal"); return [] }
        }
        let failover = FailoverRoutingProvider(online: Online(), offline: OfflineRoutingChain(providers: [
            ValhallaRoutingProvider(packs: { [] }), CachedRouteProvider(load: { trip }),
        ]), isOnline: { false })
        let routes = try await failover.routes(for: RouteRequest(origin: route.coordinates[1], destination: trip.destinationItem))
        #expect(routes.first?.source == .offline(String(localized: "Saved route")))
    }
}
