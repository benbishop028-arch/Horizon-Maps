import CoreLocation
import Foundation
import MapKit
import Testing
@testable import HorizonNav

private final class FixtureBundle {}

func fixture(_ name: String) throws -> Data {
    let url = try #require(Bundle(for: FixtureBundle.self).url(forResource: name, withExtension: "json"))
    return try Data(contentsOf: url)
}

struct TomTomParsingTests {
    @Test func routeGeometryStepsAndSummary() throws {
        let routes = try TomTomRoutingProvider.parse(try fixture("tomtom-route"))
        #expect(routes.count == 2)
        let route = routes[0]
        #expect(route.coordinates.count == 10)
        #expect(route.distance == 613)
        #expect(route.expectedTravelTime == 214)
        #expect(route.trafficDelay == 95)
        #expect(route.source == .online("TomTom"))
        // The departure instruction is not a manoeuvre.
        #expect(route.steps.count == 2)
        let turn = route.steps[0]
        #expect(turn.maneuver == .turnRight)
        #expect(turn.street == "Horse Guards Avenue")
        #expect(turn.instruction == "Turn right onto Horse Guards Avenue/A3212")
        #expect(abs(turn.startProgress - 346) < 1)
        #expect(abs(turn.distance - 267) < 1)
        #expect(!turn.isArrival)
        #expect(route.steps[1].maneuver == .arriveLeft)
        #expect(route.steps[1].isArrival)
    }

    @Test func lanesAndSpeedLimits() throws {
        let route = try TomTomRoutingProvider.parse(try fixture("tomtom-route"))[0]
        #expect(route.laneSections.count == 1)
        let lanes = route.laneSections[0].lanes
        #expect(lanes.map(\.directions) == [["STRAIGHT"], ["STRAIGHT", "RIGHT"], ["RIGHT"]])
        #expect(lanes.map(\.follow) == [nil, "RIGHT", "RIGHT"])
        #expect(route.speedLimits.count == 2)
        #expect(abs(route.speedLimit(at: 100)! - 48 / 3.6) < 0.001)
        #expect(abs(route.speedLimit(at: 400)! - 32 / 3.6) < 0.001)
    }

    @Test func roundaboutJunctionBecomesRoundabout() throws {
        let alternative = try TomTomRoutingProvider.parse(try fixture("tomtom-route"))[1]
        #expect(alternative.steps[0].maneuver == .roundaboutRight)
        #expect(alternative.steps[0].roundaboutExit == 2)
    }

    @Test func requestVariantsDropExtrasInTurn() {
        let request = RouteRequest(origin: .init(latitude: 51.5, longitude: -0.12),
                                   destination: MKMapItem(placemark: MKPlacemark(coordinate: .init(latitude: 51.51, longitude: -0.1))),
                                   heading: 92)
        let urls = TomTomRoutingProvider.attemptURLs(for: request, key: "KEY")
        #expect(urls.count == 5)
        #expect(urls[0].absoluteString.contains("sectionType=speedLimit"))
        #expect(urls[0].absoluteString.contains("vehicleHeading=92"))
        #expect(urls[0].absoluteString.contains("maxAlternatives=2"))
        #expect(!urls[4].absoluteString.contains("sectionType"))
    }

    @Test func searchResults() throws {
        let places = try TomTomSearchProvider.parse(try fixture("tomtom-search"), query: "tate modern")
        #expect(places.count == 2)
        #expect(places[0].name == "Tate Modern")
        #expect(places[0].subtitle == "Bankside, London, SE1 9TG")
        #expect(places[1].name == "Tate Street, London")
        #expect(places[1].subtitle == "United Kingdom")
    }
}

struct MapboxParsingTests {
    @Test func routeStepsLanesLimitsAndJunctionView() throws {
        let routes = try MapboxRoutingProvider.parse(try fixture("mapbox-directions"))
        #expect(routes.count == 1)
        let route = routes[0]
        #expect(route.coordinates.count == 10)
        #expect(route.name == "Victoria Embankment, Horse Guards Avenue")
        #expect(route.trafficDelay == 230.4 - 180)
        #expect(route.steps.count == 2)
        let turn = route.steps[0]
        #expect(turn.maneuver == .turnRight)
        #expect(turn.instruction == "Turn right onto Horse Guards Avenue.")
        #expect(abs(turn.startProgress - 346) < 1)
        #expect(turn.junctionImageURL?.host == "api.mapbox.com")
        #expect(route.steps[1].maneuver == .arriveLeft)
        #expect(route.steps[1].isArrival)

        #expect(route.laneSections.count == 1)
        let lanes = route.laneSections[0]
        #expect(lanes.lanes.map(\.follow) == [nil, nil, "RIGHT"])
        #expect(abs(lanes.end - 346) < 1)
        #expect(abs(lanes.start - 46) < 1)

        // 30 mph for the first five segments, 20 mph after, merged into two sections.
        #expect(route.speedLimits.count == 2)
        #expect(abs(route.speedLimit(at: 10)! - 30 * 0.44704) < 0.001)
        #expect(abs(route.speedLimit(at: 400)! - 20 * 0.44704) < 0.001)
    }

    @Test func maneuverMappingMatchesAndroid() {
        #expect(MapboxRoutingProvider.maneuver(type: "off ramp", modifier: "slight left") == .motorwayExitLeft)
        #expect(MapboxRoutingProvider.maneuver(type: "roundabout", modifier: "straight") == .roundaboutCross)
        #expect(MapboxRoutingProvider.maneuver(type: "fork", modifier: "right") == .keepRight)
        #expect(MapboxRoutingProvider.maneuver(type: "continue", modifier: "uturn") == .uTurn)
        #expect(MapboxRoutingProvider.laneDirection("uturn", leftHand: true) == "RIGHT_U_TURN")
    }

    @Test func noRouteIsNotAConnectivityFailure() {
        let data = Data(#"{"code":"NoRoute","message":"No route found","routes":[]}"#.utf8)
        #expect(throws: ProviderError.noRoute(provider: "Mapbox")) { try MapboxRoutingProvider.parse(data) }
        #expect(!RoutingFallbackPolicy.shouldUseOffline(ProviderError.noRoute(provider: "Mapbox")))
    }

    @Test func searchBoxAndGeocoder() throws {
        let box = try MapboxSearchProvider.parse(try fixture("mapbox-searchbox"), query: "tate")
        // The second feature is the same place a few metres away and is merged.
        #expect(box.count == 1)
        #expect(box[0].name == "Tate Modern")
        #expect(box[0].subtitle == "Bankside, London, SE1 9TG, United Kingdom")
        let geo = try MapboxSearchProvider.parse(try fixture("mapbox-geocode"), query: "10 downing")
        #expect(geo.first?.name == "10 Downing Street")
        #expect(geo.first?.subtitle == "London SW1A 2AA, United Kingdom")
    }
}

struct ProviderSelectionTests {
    @Test func orderFollowsAndroidProviderChoice() {
        let both = ProviderKeys(mapbox: "pk", tomtom: "tt")
        #expect(KeyedRoutingProvider.order(choice: "auto", keys: both) == ["tomtom", "mapbox"])
        #expect(KeyedRoutingProvider.order(choice: "mapbox", keys: both) == ["mapbox", "tomtom"])
        #expect(KeyedRoutingProvider.order(choice: "tomtom", keys: ProviderKeys(mapbox: "pk", tomtom: nil)) == ["mapbox"])
        #expect(KeyedRoutingProvider.order(choice: "auto", keys: ProviderKeys()).isEmpty)
    }

    @Test func noKeysFallsBackToApple() async throws {
        struct Fake: RoutingProvider {
            let name = "Fake"
            func routes(for request: RouteRequest) async throws -> [RouteOption] {
                [RouteOption(name: "apple", distance: 1, expectedTravelTime: 1, coordinates: [.init(latitude: 0, longitude: 0), .init(latitude: 0, longitude: 0.001)],
                             steps: [], advisories: [], source: .online("Apple Maps"))]
            }
        }
        let provider = KeyedRoutingProvider(keys: { ProviderKeys() }, choice: { "auto" }, apple: Fake())
        let routes = try await provider.routes(for: RouteRequest(origin: .init(latitude: 0, longitude: 0),
                                                                 destination: MKMapItem(placemark: MKPlacemark(coordinate: .init(latitude: 0, longitude: 0.001)))))
        #expect(routes.first?.name == "apple")
    }

    @Test func quickerOtherServiceGoesFirst() throws {
        let tomtom = try TomTomRoutingProvider.parse(try fixture("tomtom-route"))
        var other = try MapboxRoutingProvider.parse(try fixture("mapbox-directions"))[0]
        // The same road is dropped.
        #expect(RouteComparison.combine(mine: tomtom, theirs: [other]).count == 2)
        // A different, much quicker road goes first.
        other.coordinates = other.coordinates.map { CLLocationCoordinate2D(latitude: $0.latitude + 0.01, longitude: $0.longitude) }
        other.expectedTravelTime = 10
        let merged = RouteComparison.combine(mine: tomtom, theirs: [other])
        #expect(merged.first?.source == .online("Mapbox"))
    }
}
