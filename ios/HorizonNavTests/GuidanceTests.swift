import CoreLocation
import MapKit
import Testing
@testable import HorizonNav

/// A straight east-bound road with a right turn south at 500 m, then 300 m to the destination.
private func lShapedRoute() -> RouteOption {
    let start = CLLocationCoordinate2D(latitude: 51.5, longitude: -0.1)
    let metersPerDegreeLon = 111_320 * cos(start.latitude * .pi / 180)
    let corner = CLLocationCoordinate2D(latitude: start.latitude, longitude: start.longitude + 500 / metersPerDegreeLon)
    var coords: [CLLocationCoordinate2D] = []
    for i in 0...10 { coords.append(CLLocationCoordinate2D(latitude: start.latitude, longitude: start.longitude + Double(i) * 50 / metersPerDegreeLon)) }
    for i in 1...6 { coords.append(CLLocationCoordinate2D(latitude: corner.latitude - Double(i) * 50 / 111_320, longitude: corner.longitude)) }
    let cumulative = RouteGeometry.cumulativeDistances(coords)
    let steps = [
        RouteStep(instruction: "Turn right onto South Road", notice: nil, distance: 300, startProgress: cumulative[10],
                  turnAngle: RouteGeometry.turnAngle(in: coords, at: 10), isArrival: false),
        RouteStep(instruction: "Arrive at destination", notice: nil, distance: 0, startProgress: cumulative[16],
                  turnAngle: nil, isArrival: true),
    ]
    return RouteOption(name: "Test", distance: 800, expectedTravelTime: 80, coordinates: coords, steps: steps,
                       advisories: [], source: .online("test"))
}

private func fix(_ c: CLLocationCoordinate2D, accuracy: Double = 5, speed: Double = 10, course: Double = -1, t: TimeInterval) -> CLLocation {
    CLLocation(coordinate: c, altitude: 0, horizontalAccuracy: accuracy, verticalAccuracy: 5, course: course, speed: speed,
               timestamp: Date(timeIntervalSince1970: t))
}

struct NavigationPolicyTests {
    @Test func offRouteLimitMatchesAndroid() {
        #expect(NavigationPolicy.offRouteLimit(accuracyMeters: .nan) == 30)
        #expect(NavigationPolicy.offRouteLimit(accuracyMeters: 20) == 42)
    }

    @Test func poorGpsNeverReroutes() {
        #expect(NavigationPolicy.gpsReliableForReroute(accuracyMeters: 45))
        #expect(!NavigationPolicy.gpsReliableForReroute(accuracyMeters: 46))
    }

    @Test func farDepartureCountsDouble() {
        #expect(NavigationPolicy.offRouteStrikeCount(progressJump: false, distanceMeters: 61, limitMeters: 30) == 2)
        #expect(NavigationPolicy.offRouteStrikeCount(progressJump: false, distanceMeters: 40, limitMeters: 30) == 1)
    }

    @Test func connectivityFailuresUseOffline() {
        let underlying = NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet)
        let wrapped = NSError(domain: "MKErrorDomain", code: 2, userInfo: [NSUnderlyingErrorKey: underlying])
        #expect(RoutingFallbackPolicy.shouldUseOffline(wrapped))
        #expect(!RoutingFallbackPolicy.shouldUseOffline(RoutingError.noRoute))
    }
}

struct RouteGuidanceTests {
    @Test func rightTurnGeometryIsDetected() {
        let route = lShapedRoute()
        let angle = try! #require(route.steps[0].turnAngle)
        #expect(angle > 80 && angle < 100)
        #expect(route.steps[0].symbolName == "arrow.turn.up.right")
        #expect(route.steps[1].symbolName == "flag.checkered")
    }

    @Test func progressAdvancesToNextManeuver() {
        let route = lShapedRoute()
        let guidance = RouteGuidance(route: route)
        let first = guidance.update(with: fix(route.coordinates[2], course: 90, t: 0))
        #expect(first.status == .onRoute)
        #expect(first.upcomingStepIndex == 0)
        #expect(abs(first.distanceToManeuver - 400) < 5)
        let afterTurn = guidance.update(with: fix(route.coordinates[12], course: 180, t: 30))
        #expect(afterTurn.upcomingStepIndex == 1)
        #expect(abs(afterTurn.distanceToManeuver - 200) < 5)
    }

    @Test func arrivalIsReported() {
        let route = lShapedRoute()
        let guidance = RouteGuidance(route: route)
        _ = guidance.update(with: fix(route.coordinates[14], t: 0))
        let end = guidance.update(with: fix(route.coordinates[16], t: 10))
        #expect(end.status == .arrived)
        #expect(end.upcomingStepIndex == nil)
    }

    @Test func leavingTheRouteTriggersOneReroute() {
        let route = lShapedRoute()
        let guidance = RouteGuidance(route: route)
        _ = guidance.update(with: fix(route.coordinates[3], course: 90, t: 0))
        // 100 m north of the road: more than twice the 30 m limit, two strikes per fix.
        let off = CLLocationCoordinate2D(latitude: route.coordinates[4].latitude + 100 / 111_320, longitude: route.coordinates[4].longitude)
        let one = guidance.update(with: fix(off, t: 5))
        #expect(one.status == .offRoute(strikes: 2))
        let two = guidance.update(with: fix(off, t: 6))
        #expect(two.status == .rerouteNeeded)
        // Progress stays where the car left the route.
        #expect(abs(two.progress - route.cumulative[3]) < 5)
    }

    @Test func poorAccuracyDoesNotReroute() {
        let route = lShapedRoute()
        let guidance = RouteGuidance(route: route)
        let off = CLLocationCoordinate2D(latitude: route.coordinates[4].latitude + 100 / 111_320, longitude: route.coordinates[4].longitude)
        for t in 0..<5 {
            let update = guidance.update(with: fix(off, accuracy: 80, t: TimeInterval(t)))
            #expect(update.status == .onRoute)
        }
    }

    @Test func failoverWithoutOfflineProviderReportsIt() async {
        struct Offline: RoutingProvider {
            let name = "down"
            func routes(for request: RouteRequest) async throws -> [RouteOption] { throw URLError(.notConnectedToInternet) }
        }
        let provider = FailoverRoutingProvider(online: Offline(), offline: nil)
        await #expect(throws: RoutingError.self) {
            _ = try await provider.routes(for: RouteRequest(origin: .init(latitude: 0, longitude: 0), destination: .init(placemark: .init(coordinate: .init(latitude: 1, longitude: 1)))))
        }
    }
}
