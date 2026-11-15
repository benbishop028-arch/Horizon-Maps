import CoreLocation
import Foundation

/// TomTom Routing API (classic `calculateRoute`, which returns turn-by-turn instructions, lanes, traffic and speed limit
/// sections) and Search API, ported from the Android `TomTomRest`.
struct TomTomRoutingProvider: RoutingProvider {
    let name = "TomTom"
    var key: String

    static let routingBase = "https://api.tomtom.com/routing/1/calculateRoute"

    func routes(for request: RouteRequest) async throws -> [RouteOption] {
        var last: Error = ProviderError.noRoute(provider: name)
        for url in Self.attemptURLs(for: request, key: key) {
            do {
                let data = try await ProviderHTTP.data(url, provider: name, timeout: request.wantsAlternatives ? 25 : 12)
                let routes = try Self.parse(data)
                guard !routes.isEmpty else { throw ProviderError.noRoute(provider: name) }
                return routes
            } catch let error as ProviderError {
                last = error
                // A refused key or the daily limit: other variants only use up the quota.
                if error.isRefusal { break }
            } catch {
                // No signal: the next variant cannot help either.
                throw error
            }
        }
        throw last
    }

    /// The request variants Android tries in turn: with lanes and speed limits first, dropping extras when refused.
    static func attemptURLs(for request: RouteRequest, key: String) -> [URL] {
        let from = request.origin, to = request.destination.placemark.coordinate
        let locations = String(format: "%.6f,%.6f:%.6f,%.6f", from.latitude, from.longitude, to.latitude, to.longitude)
        var components = URLComponents(string: "\(routingBase)/\(locations)/json")!
        var base = [
            URLQueryItem(name: "key", value: key),
            URLQueryItem(name: "maxAlternatives", value: request.wantsAlternatives ? "2" : "0"),
            URLQueryItem(name: "traffic", value: "true"),
            URLQueryItem(name: "routeRepresentation", value: "polyline"),
            URLQueryItem(name: "instructionsType", value: "text"),
        ]
        // The direction of travel stops a reroute sending the car the wrong way down the road it is on.
        if let heading = request.heading { base.append(URLQueryItem(name: "vehicleHeading", value: "\(Int(heading) % 360)")) }
        let language = URLQueryItem(name: "language", value: languageTag)
        let traffic = URLQueryItem(name: "sectionType", value: "traffic")
        let lanes = URLQueryItem(name: "sectionType", value: "lanes")
        let limits = URLQueryItem(name: "sectionType", value: "speedLimit")
        let variants: [[URLQueryItem]] = [
            base + [language, traffic, lanes, limits],
            base + [language, traffic, lanes],
            base + [traffic, lanes],
            base + [traffic],
            base,
        ]
        return variants.compactMap { items in
            components.queryItems = items
            return components.url
        }
    }

    /// TomTom has no Japanese or Urdu guidance, and Traditional Chinese is Taiwan's (Android `TomTomRest.routesWith`).
    static var languageTag: String {
        let tag = AppLocale.tag
        if tag.hasPrefix("ja") || tag.hasPrefix("ur") { return "en-GB" }
        if tag.hasPrefix("zh-Hant") { return "zh-TW" }
        if tag.hasPrefix("zh") { return "zh-CN" }
        return tag
    }

    /// Parses a `calculateRoute` answer into provider-neutral routes (Android `TomTomRest.parse`).
    static func parse(_ data: Data) throws -> [RouteOption] {
        let root = try JSON.object(data, provider: "TomTom")
        return root.array("routes").compactMap(parseRoute)
    }

    private static func coordinate(_ o: [String: Any]) -> CLLocationCoordinate2D? {
        guard let lat = o.double("latitude") ?? o.double("lat"), let lon = o.double("longitude") ?? o.double("lon") else { return nil }
        return CLLocationCoordinate2D(latitude: lat, longitude: lon)
    }

    private static func parseRoute(_ r: [String: Any]) -> RouteOption? {
        var points: [CLLocationCoordinate2D] = []
        for leg in r.array("legs") {
            for p in leg.array("points") { if let c = coordinate(p) { points.append(c) } }
        }
        guard points.count >= 2 else { return nil }
        let cumulative = RouteGeometry.cumulativeDistances(points)
        let instructions = r.dict("guidance")?.array("instructions") ?? r.array("instructions")
        var raw: [(offset: Double, maneuver: Maneuver, street: String, message: String, exit: Int, exitNumber: String, sign: String)] = []
        for i in instructions {
            var street = i.string("street")
            if street.isEmpty { street = i.dict("nextRoadInformation")?.dict("streetName")?.string("text") ?? "" }
            if street.isEmpty { street = (i["roadNumbers"] as? [String])?.first ?? "" }
            var maneuver = Maneuver(providerName: i.string("maneuver").isEmpty ? "STRAIGHT" : i.string("maneuver"))
            if i.string("junctionType").uppercased() == "ROUNDABOUT" && !maneuver.isRoundabout && !maneuver.isArrival {
                // Drawn as a roundabout, never a plain turn: the angle only says which way the exit points.
                let angle = i.double("turnAngleInDecimalDegrees") ?? 0
                maneuver = angle > 135 || angle < -135 ? .roundaboutBack : angle > 25 ? .roundaboutRight : angle < -25 ? .roundaboutLeft : .roundaboutCross
            }
            let message = i.string("message").isEmpty ? i.string("instructionMessage") : i.string("message")
            raw.append((i.double("routeOffsetInMeters") ?? i.double("offsetInMeters") ?? 0, maneuver, street, message,
                        i.int("roundaboutExitNumber") ?? 0, i.string("exitNumber"), i.string("signpostText")))
        }
        let total = cumulative.last ?? 0
        var steps: [RouteStep] = []
        for (n, x) in raw.enumerated() where x.maneuver != .depart {
            let nextOffset = n + 1 < raw.count ? raw[n + 1].offset : total
            let index = RouteGeometry.index(atProgress: x.offset, cumulative: cumulative)
            steps.append(RouteStep(
                instruction: x.message.isEmpty ? (x.maneuver.phrase(exit: x.exit)?.capitalizedFirst ?? "") : x.message,
                notice: nil, distance: max(nextOffset - x.offset, 0), startProgress: min(x.offset, total),
                turnAngle: RouteGeometry.turnAngle(in: points, at: index) ?? x.maneuver.angle,
                isArrival: x.maneuver.isArrival || n == raw.count - 1,
                maneuver: x.maneuver, street: x.street, roundaboutExit: x.exit, exitNumber: x.exitNumber, signpost: x.sign))
        }

        var lanes: [LaneSection] = []
        var limits: [SpeedLimitSection] = []
        var delayTotal = 0.0
        let lastIndex = points.count - 1
        for section in r.array("sections") {
            let type = section.string("sectionType").replacingOccurrences(of: "_", with: "").uppercased()
            let a = min(max(section.int("startPointIndex") ?? 0, 0), lastIndex)
            let b = min(max(section.int("endPointIndex") ?? a, 0), lastIndex)
            switch type {
            case "LANES":
                let parsed = section.array("lanes").map { lane in
                    Lane(directions: (lane["directions"] as? [String] ?? []).map { Maneuver.snake($0) },
                         follow: (lane["follow"] as? String).flatMap { $0.isEmpty ? nil : Maneuver.snake($0) })
                }
                if !parsed.isEmpty { lanes.append(LaneSection(start: cumulative[a], end: cumulative[b], lanes: parsed)) }
            case "SPEEDLIMIT":
                let kmh = section.double("maxSpeedLimitInKmh") ?? section.double("speedLimitInKmh") ?? -1
                if kmh > 0, kmh <= 200, b > a { limits.append(SpeedLimitSection(start: cumulative[a], end: cumulative[b], metersPerSecond: kmh / 3.6)) }
            case "TRAFFIC":
                delayTotal += section.double("delayInSeconds") ?? 0
            default:
                break
            }
        }
        let summary = r.dict("summary") ?? [:]
        let length = summary.double("lengthInMeters").flatMap { $0 > 0 ? $0 : nil } ?? total
        return RouteOption(name: "", distance: length, expectedTravelTime: summary.double("travelTimeInSeconds") ?? 0,
                           coordinates: points, steps: steps, advisories: [], source: .online("TomTom"),
                           trafficDelay: summary.double("trafficDelayInSeconds") ?? delayTotal,
                           laneSections: lanes, speedLimits: limits.sorted { $0.start < $1.start })
    }
}

/// TomTom fuzzy search: businesses, streets, towns and postcodes, biased towards the car (Android `TomTomRest.geocode`).
struct TomTomSearchProvider: PlaceSearchProvider {
    let name = "TomTom"
    var key: String

    func search(_ query: String, near: CLLocationCoordinate2D?) async throws -> [FoundPlace] {
        guard let url = Self.searchURL(query, near: near, key: key) else { return [] }
        return try Self.parse(try await ProviderHTTP.data(url, provider: name, timeout: 12), query: query)
    }

    static func searchURL(_ query: String, near: CLLocationCoordinate2D?, key: String) -> URL? {
        let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed.subtracting(CharacterSet(charactersIn: "/?#"))) ?? query
        var components = URLComponents(string: "https://api.tomtom.com/search/2/search/\(encoded).json")
        var items = [
            URLQueryItem(name: "key", value: key), URLQueryItem(name: "limit", value: "15"),
            URLQueryItem(name: "language", value: TomTomRoutingProvider.languageTag), URLQueryItem(name: "typeahead", value: "true"),
            URLQueryItem(name: "minFuzzyLevel", value: "1"), URLQueryItem(name: "maxFuzzyLevel", value: "2"),
            URLQueryItem(name: "idxSet", value: "POI,Addr,Str,Xstr,Geo,PAD"),
        ]
        if let near {
            items.append(URLQueryItem(name: "lat", value: String(near.latitude)))
            items.append(URLQueryItem(name: "lon", value: String(near.longitude)))
        }
        components?.queryItems = items
        return components?.url
    }

    static func parse(_ data: Data, query: String) throws -> [FoundPlace] {
        let root = try JSON.object(data, provider: "TomTom")
        var out: [FoundPlace] = []
        for x in root.array("results") {
            guard let pos = x.dict("position"), let lat = pos.double("lat"), let lon = pos.double("lon") else { continue }
            let address = x.dict("address")
            let poi = x.dict("poi")
            let free = address?.string("freeformAddress") ?? ""
            let poiName = poi?.string("name") ?? ""
            let name = !poiName.isEmpty ? poiName : (free.isEmpty ? query : free)
            let sub = poi != nil ? free : (address?.string("country") ?? "")
            out = PlaceMerge.merge(out, [FoundPlace(name: name, subtitle: sub, coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon))])
        }
        return out
    }
}

extension Maneuver {
    /// "slightRight" or "SLIGHT_RIGHT" to the Android "SLIGHT_RIGHT".
    static func snake(_ s: String) -> String {
        s.replacingOccurrences(of: "([a-z0-9])([A-Z])", with: "$1_$2", options: .regularExpression).uppercased()
    }
}

extension String {
    var capitalizedFirst: String { prefix(1).uppercased() + dropFirst() }
}
