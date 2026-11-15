import CoreLocation
import Foundation

/// Mapbox Directions (driving-traffic profile, with lanes, speed limits, traffic and junction views) and Mapbox Search Box
/// plus the Geocoding v6 API, ported from the Android `MapboxRest`.
struct MapboxRoutingProvider: RoutingProvider {
    let name = "Mapbox"
    var token: String
    /// Whether the car drives on the left, for the U-turn lane arrow.
    var leftHandTraffic = false

    func routes(for request: RouteRequest) async throws -> [RouteOption] {
        var last: Error = ProviderError.noRoute(provider: name)
        for url in Self.attemptURLs(for: request, token: token) {
            do {
                let data = try await ProviderHTTP.data(url, provider: name, timeout: request.wantsAlternatives ? 25 : 12)
                return try Self.parse(data, leftHand: leftHandTraffic)
            } catch let error as ProviderError {
                last = error
                if error.isRefusal { break }
                if case .noRoute = error { break }
            } catch {
                throw error
            }
        }
        throw last
    }

    /// Mapbox has no guidance in these languages, so English instructions are asked for (Android `MapboxRest.routesWith`).
    static var languageCode: String {
        let tag = AppLocale.tag
        let language = String(tag.prefix { $0 != "-" })
        let unsupported: Set<String> = ["th", "bg", "sl", "ro", "eu", "el", "cs", "fi", "sk", "da", "hr", "ms", "hu", "tr", "sr", "ar", "he", "ur"]
        if tag.hasPrefix("zh-Hant") || unsupported.contains(language) { return "en" }
        if tag.hasPrefix("zh") { return "zh-Hans" }
        return language
    }

    static func attemptURLs(for request: RouteRequest, token: String) -> [URL] {
        let from = request.origin, to = request.destination.placemark.coordinate
        let coords = String(format: "%.6f,%.6f;%.6f,%.6f", from.longitude, from.latitude, to.longitude, to.latitude)
        var components = URLComponents(string: "https://api.mapbox.com/directions/v5/mapbox/driving-traffic/\(coords)")!
        var items = [
            URLQueryItem(name: "geometries", value: "geojson"), URLQueryItem(name: "overview", value: "full"),
            URLQueryItem(name: "steps", value: "true"), URLQueryItem(name: "banner_instructions", value: "true"),
            URLQueryItem(name: "annotations", value: "maxspeed,congestion,duration,distance,freeflow_speed"),
            URLQueryItem(name: "language", value: languageCode),
            URLQueryItem(name: "alternatives", value: request.wantsAlternatives ? "true" : "false"),
            URLQueryItem(name: "access_token", value: token),
        ]
        // The way the car faces, so a reroute does not send it the wrong way down the road it is on.
        if let heading = request.heading { items.append(URLQueryItem(name: "bearings", value: "\(Int(heading) % 360),45;")) }
        components.queryItems = items
        let full = components.url!
        // The same without the free-flow speeds, should Mapbox refuse them: the route matters more than the delay.
        components.queryItems = items.map { $0.name == "annotations" ? URLQueryItem(name: "annotations", value: "maxspeed,congestion,duration,distance") : $0 }
        return [full, components.url!]
    }

    /// Android `MapboxRest.maneuverOf`.
    static func maneuver(type: String, modifier mod: String) -> Maneuver {
        let left = mod.contains("left"), right = mod.contains("right")
        func turn() -> Maneuver {
            if mod == "uturn" { return .uTurn }
            if mod.hasPrefix("sharp") { return left ? .sharpLeft : .sharpRight }
            if mod.hasPrefix("slight") { return left ? .bearLeft : .bearRight }
            if left { return .turnLeft }
            if right { return .turnRight }
            return .straight
        }
        switch type {
        case "depart": return .depart
        case "arrive": return left ? .arriveLeft : right ? .arriveRight : .arrive
        case "turn", "end of road", "roundabout turn": return turn()
        case "continue", "new name": return mod.isEmpty || mod == "straight" ? .straight : turn()
        case "fork", "merge": return left ? .keepLeft : right ? .keepRight : .straight
        case "on ramp": return left ? .keepLeft : right ? .keepRight : .entranceRamp
        case "off ramp": return left ? .motorwayExitLeft : .motorwayExitRight
        case "roundabout", "rotary":
            if mod == "uturn" { return .roundaboutBack }
            if mod.hasPrefix("slight") || mod == "straight" || mod.isEmpty { return .roundaboutCross }
            return left ? .roundaboutLeft : .roundaboutRight
        case "ferry": return .takeFerry
        default: return turn()
        }
    }

    /// Android `MapboxRest.laneDir`.
    static func laneDirection(_ indication: String, leftHand: Bool) -> String {
        switch indication {
        case "uturn": return leftHand ? "RIGHT_U_TURN" : "LEFT_U_TURN"
        case "sharp left": return "SHARP_LEFT"
        case "left": return "LEFT"
        case "slight left": return "SLIGHT_LEFT"
        case "sharp right": return "SHARP_RIGHT"
        case "right": return "RIGHT"
        case "slight right": return "SLIGHT_RIGHT"
        default: return "STRAIGHT"
        }
    }

    /// Parses a Directions answer (Android `MapboxRest.parse`).
    static func parse(_ data: Data, leftHand: Bool = false) throws -> [RouteOption] {
        let root = try JSON.object(data, provider: "Mapbox")
        guard root.string("code") == "Ok" else {
            if root.string("code") == "NoRoute" || root.string("code") == "NoSegment" { throw ProviderError.noRoute(provider: "Mapbox") }
            throw ProviderError.badResponse(provider: "Mapbox")
        }
        return root.array("routes").compactMap { parseRoute($0, leftHand: leftHand) }
    }

    private static func parseRoute(_ r: [String: Any], leftHand: Bool) -> RouteOption? {
        guard let raw = r.dict("geometry")?["coordinates"] as? [[Double]] else { return nil }
        let points = raw.compactMap { $0.count >= 2 ? CLLocationCoordinate2D(latitude: $0[1], longitude: $0[0]) : nil }
        guard points.count >= 2 else { return nil }
        let cumulative = RouteGeometry.cumulativeDistances(points)
        let total = cumulative.last ?? 0
        let apiLength = r.double("distance").flatMap { $0 > 0 ? $0 : nil } ?? total
        // Mapbox's distances and the line's own differ by a few metres: offsets are scaled onto the line.
        let k = apiLength > 0 ? total / apiLength : 1
        var steps: [RouteStep] = []
        var lanes: [LaneSection] = []
        var limits: [SpeedLimitSection] = []
        var legStart = 0.0
        var legPointStart = 0
        let legs = r.array("legs")
        for (legIndex, leg) in legs.enumerated() {
            var offset = legStart
            var previousOffset = legStart
            for s in leg.array("steps") {
                let m = s.dict("maneuver") ?? [:]
                let type = m.string("type"), mod = m.string("modifier")
                let at = offset * k
                let skip = type == "notification" || type == "exit roundabout" || type == "exit rotary" ||
                    (type == "arrive" && legIndex < legs.count - 1) || type == "depart"
                if !skip {
                    let maneuver = maneuver(type: type, modifier: mod)
                    let name = s.string("name")
                    let ref = s.string("ref").split(separator: ";").first.map { $0.trimmingCharacters(in: .whitespaces) } ?? ""
                    let street = type == "off ramp" && name.isEmpty ? s.string("destinations") : (name.isEmpty ? ref : name)
                    var junction: URL?
                    for banner in s.array("bannerInstructions") {
                        for component in banner.dict("view")?.array("components") ?? [] {
                            let url = component.string("imageURL")
                            if url.hasPrefix("https://") { junction = URL(string: url); break }
                        }
                        if junction != nil { break }
                    }
                    let index = RouteGeometry.index(atProgress: at, cumulative: cumulative)
                    let exit = maneuver.isRoundabout ? (m.int("exit") ?? 0) : 0
                    steps.append(RouteStep(
                        instruction: m.string("instruction"), notice: nil, distance: s.double("distance") ?? 0,
                        startProgress: min(at, total), turnAngle: RouteGeometry.turnAngle(in: points, at: index) ?? maneuver.angle,
                        isArrival: maneuver.isArrival, maneuver: maneuver, street: street, roundaboutExit: exit,
                        exitNumber: type == "off ramp" ? String(s.string("exits").split(separator: ";").first ?? "") : "",
                        signpost: s.string("destinations"), junctionImageURL: junction))
                    // The lanes at this junction: Mapbox marks the usable lanes ("valid") and, of those, the ones to be in
                    // ("active"). When it picks some, only those are lit.
                    if type != "arrive", let laneList = (s.array("intersections").first)?["lanes"] as? [[String: Any]], laneList.count >= 2 {
                        let anyActive = laneList.contains { $0.bool("valid") && $0.bool("active") }
                        let parsed = laneList.map { lane -> Lane in
                            var directions: [String] = []
                            for d in (lane["indications"] as? [String] ?? []).map({ laneDirection($0, leftHand: leftHand) }) where !directions.contains(d) { directions.append(d) }
                            if directions.isEmpty { directions = ["STRAIGHT"] }
                            let valid = lane.string("valid_indication")
                            let use = lane.bool("valid") && (!anyActive || lane.bool("active"))
                            return Lane(directions: directions, follow: use ? (valid.isEmpty ? directions[0] : laneDirection(valid, leftHand: leftHand)) : nil)
                        }
                        // Shown from 300 m out, never before the previous junction.
                        let from = max(previousOffset * k, at - 300)
                        if parsed.contains(where: { $0.follow != nil }) && parsed.contains(where: { $0.follow == nil }) && from < at - 20 {
                            lanes.append(LaneSection(start: from, end: at, lanes: parsed))
                        }
                    }
                }
                previousOffset = offset
                offset += s.double("distance") ?? 0
            }
            // Posted limits for each stretch of the line.
            let annotation = leg.dict("annotation")
            let speeds = annotation?["maxspeed"] as? [[String: Any]] ?? []
            for (i, speed) in speeds.enumerated() {
                let idx = legPointStart + i
                guard idx + 1 < cumulative.count, let v = speed.double("speed"), v > 0 else { continue }
                let mps = speed.string("unit") == "mph" ? v * 0.44704 : v / 3.6
                guard mps < 70 else { continue }
                if let last = limits.last, abs(last.metersPerSecond - mps) < 0.05, abs(last.end - cumulative[idx]) < 1 {
                    limits[limits.count - 1].end = cumulative[idx + 1]
                } else {
                    limits.append(SpeedLimitSection(start: cumulative[idx], end: cumulative[idx + 1], metersPerSecond: mps))
                }
            }
            legStart += leg.double("distance") ?? 0
            legPointStart += max(speeds.count, (annotation?["duration"] as? [Any])?.count ?? 0)
        }
        if let last = steps.indices.last { steps[last].isArrival = true }
        let time = r.double("duration") ?? 0
        let typical = r.double("duration_typical") ?? time
        let summary = legs.compactMap { $0["summary"] as? String }.first { !$0.isEmpty } ?? ""
        return RouteOption(name: summary, distance: apiLength, expectedTravelTime: time, coordinates: points, steps: steps,
                           advisories: [], source: .online("Mapbox"), trafficDelay: max(0, time - typical),
                           laneSections: lanes, speedLimits: limits)
    }
}

/// Mapbox Search Box (businesses and places) and the Geocoding v6 API (addresses), asked together; addresses first
/// when the query starts with a house number (Android `MapboxRest.geocode`).
struct MapboxSearchProvider: PlaceSearchProvider {
    let name = "Mapbox"
    var token: String

    func search(_ query: String, near: CLLocationCoordinate2D?) async throws -> [FoundPlace] {
        let urls = Self.searchURLs(query, near: near, token: token)
        async let box = try? ProviderHTTP.data(urls.searchBox, provider: name, timeout: 9)
        async let geo = try? ProviderHTTP.data(urls.geocode, provider: name, timeout: 9)
        let (boxData, geoData) = await (box, geo)
        let boxPlaces = boxData.flatMap { try? Self.parse($0, query: query) } ?? []
        let geoPlaces = geoData.flatMap { try? Self.parse($0, query: query) } ?? []
        if boxData == nil && geoData == nil { throw ProviderError.badResponse(provider: name) }
        let addressy = query.trimmingCharacters(in: .whitespaces).first?.isNumber == true
        return addressy ? PlaceMerge.merge(geoPlaces, boxPlaces) : PlaceMerge.merge(boxPlaces, geoPlaces)
    }

    static func searchURLs(_ query: String, near: CLLocationCoordinate2D?, token: String) -> (searchBox: URL, geocode: URL) {
        var common = [URLQueryItem(name: "q", value: query.trimmingCharacters(in: .whitespaces)),
                      URLQueryItem(name: "language", value: MapboxRoutingProvider.languageCode),
                      URLQueryItem(name: "access_token", value: token)]
        if let near { common.append(URLQueryItem(name: "proximity", value: String(format: "%.5f,%.5f", near.longitude, near.latitude))) }
        var box = URLComponents(string: "https://api.mapbox.com/search/searchbox/v1/forward")!
        box.queryItems = common + [URLQueryItem(name: "limit", value: "10"), URLQueryItem(name: "auto_complete", value: "true")]
        var geo = URLComponents(string: "https://api.mapbox.com/search/geocode/v6/forward")!
        geo.queryItems = common + [URLQueryItem(name: "limit", value: "8"), URLQueryItem(name: "autocomplete", value: "true"),
                                   URLQueryItem(name: "types", value: "address,street,postcode,place,locality,neighborhood,district")]
        return (box.url!, geo.url!)
    }

    /// Android `MapboxRest.feature`, for both the Search Box and Geocoding v6 feature collections.
    static func parse(_ data: Data, query: String) throws -> [FoundPlace] {
        let root = try JSON.object(data, provider: "Mapbox")
        var out: [FoundPlace] = []
        for x in root.array("features") {
            let geometry = x.dict("geometry")?["coordinates"] as? [Double]
            let properties = x.dict("properties") ?? [:]
            let coords = properties.dict("coordinates")
            guard let lat = coords?.double("latitude") ?? geometry.flatMap({ $0.count > 1 ? $0[1] : nil }),
                  let lon = coords?.double("longitude") ?? geometry?.first else { continue }
            let full = properties.string("full_address")
            var name = properties.string("name")
            if name.isEmpty { name = String(full.split(separator: ",").first ?? "") }
            if name.isEmpty { name = query }
            let sub = !full.isEmpty && full != name && properties.string("feature_type") == "poi" ? full : properties.string("place_formatted")
            out = PlaceMerge.merge(out, [FoundPlace(name: name, subtitle: sub, coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon))])
        }
        return out
    }
}
