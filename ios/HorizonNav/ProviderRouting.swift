import CoreLocation
import Foundation
import MapKit

/// Picks the routing service the way the Android `NavEngine.provider` does: the chosen one when its key is set, otherwise
/// the other keyed service, and Apple Maps as the keyless fallback. With both keys and the choice on Auto, a new route is
/// asked of both at once and the clearly quicker one goes first (Android `netRoutesRaw`).
struct KeyedRoutingProvider: RoutingProvider {
    var keys: () -> ProviderKeys = { ProviderKeys.current }
    var choice: () -> String = { AppSettings.routingProvider }
    var apple: RoutingProvider = MapKitRoutingProvider()
    var makeTomTom: (String) -> RoutingProvider = { TomTomRoutingProvider(key: $0) }
    var makeMapbox: (String) -> RoutingProvider = { MapboxRoutingProvider(token: $0, leftHandTraffic: TrafficSide.isLeftHand) }

    var name: String {
        let order = Self.order(choice: choice(), keys: keys())
        return order.first == "tomtom" ? "TomTom" : order.first == "mapbox" ? "Mapbox" : apple.name
    }

    /// The services to try, in order (Android `provider` plus its backup).
    static func order(choice: String, keys: ProviderKeys) -> [String] {
        switch choice {
        case "mapbox": return (keys.hasMapbox ? ["mapbox"] : []) + (keys.hasTomTom ? ["tomtom"] : [])
        default: return (keys.hasTomTom ? ["tomtom"] : []) + (keys.hasMapbox ? ["mapbox"] : [])
        }
    }

    private func provider(_ id: String, _ keys: ProviderKeys) -> RoutingProvider? {
        switch id {
        case "tomtom": return keys.tomtom.map(makeTomTom)
        case "mapbox": return keys.mapbox.map(makeMapbox)
        default: return nil
        }
    }

    func routes(for request: RouteRequest) async throws -> [RouteOption] {
        let keys = keys()
        let order = Self.order(choice: choice(), keys: keys)
        guard let first = order.first, let main = provider(first, keys) else { return try await apple.routes(for: request) }
        let backup = order.dropFirst().first.flatMap { provider($0, keys) }
        let askBoth = request.wantsAlternatives && choice() == "auto" && backup != nil
        guard askBoth, let other = backup else { return try await withBackup(main, backup, request) }

        // The other service on a task of its own, so the two are asked at the same time.
        var single = request
        single.wantsAlternatives = false
        let theirsTask = Task { try? await other.routes(for: single) }
        let mine: [RouteOption]
        do {
            mine = try await main.routes(for: request)
        } catch {
            if RoutingFallbackPolicy.shouldUseOffline(error) { theirsTask.cancel(); throw error }
            if let theirs = await theirsTask.value, !theirs.isEmpty { return theirs }
            return try await apple.routes(for: request)
        }
        // The main service has answered: the other gets a few more seconds at most.
        let theirs = await Self.value(of: theirsTask, within: 6) ?? []
        return RouteComparison.combine(mine: mine, theirs: theirs)
    }

    private func withBackup(_ main: RoutingProvider, _ backup: RoutingProvider?, _ request: RouteRequest) async throws -> [RouteOption] {
        do {
            return try await main.routes(for: request)
        } catch {
            // No signal goes to the offline router; a refused key, a limit or an outage tries the other service, then Apple.
            if RoutingFallbackPolicy.shouldUseOffline(error) { throw error }
            if let backup, let routes = try? await backup.routes(for: request), !routes.isEmpty { return routes }
            if case ProviderError.noRoute = error { throw RoutingError.noRoute }
            return try await apple.routes(for: request)
        }
    }

    private static func value(of task: Task<[RouteOption]?, Never>, within seconds: Double) async -> [RouteOption]? {
        await withTaskGroup(of: [RouteOption]?.self) { group in
            group.addTask { await task.value }
            group.addTask { try? await Task.sleep(for: .seconds(seconds)); return nil }
            let first = await group.next() ?? nil
            group.cancelAll()
            if first == nil { task.cancel() }
            return first
        }
    }
}

/// Merging two services' answers (Android `netRoutesRaw` and `sameRoad`).
enum RouteComparison {
    /// The other service's best route goes first when it is clearly quicker (2 minutes, or 5% on longer trips), is offered
    /// as another choice otherwise, and is dropped when it is the same road as one already listed.
    static func combine(mine: [RouteOption], theirs: [RouteOption]) -> [RouteOption] {
        guard let best = theirs.first else { return mine }
        guard let top = mine.first else { return theirs }
        if mine.contains(where: { sameRoad($0, best) }) { return mine }
        if best.expectedTravelTime < top.expectedTravelTime - max(120, top.expectedTravelTime * 0.05) {
            return Array(([best] + mine).prefix(3))
        }
        return Array(mine.prefix(2)) + [best]
    }

    /// True when `b` runs along `a` almost all the way: 21 of 23 sample points within 60 m, lengths within 6%.
    static func sameRoad(_ a: RouteOption, _ b: RouteOption) -> Bool {
        guard a.coordinates.count >= 2, b.coordinates.count >= 2 else { return false }
        guard abs(a.length - b.length) <= 0.06 * max(a.length, b.length) else { return false }
        let n = 24
        var near = 0
        let step = max(1, a.coordinates.count / 4000)
        for i in 1..<n {
            let q = b.coordinates[i * (b.coordinates.count - 1) / n]
            var d = Double.greatestFiniteMagnitude
            var j = 0
            while j < a.coordinates.count { d = min(d, RouteGeometry.distance(a.coordinates[j], q)); j += step }
            if d < 60 { near += 1 }
        }
        return Double(near) >= Double(n - 1) * 0.9
    }
}

/// Place search the way the Android `NavEngine.netGeocode` chooses: TomTom with a key (topped up with Mapbox when it finds
/// little), Mapbox with only a token, and Apple's search as the keyless fallback.
struct PlaceSearchService {
    var keys: () -> ProviderKeys = { ProviderKeys.current }
    var choice: () -> String = { AppSettings.searchProvider }

    func search(_ query: String, near: CLLocationCoordinate2D) async -> [MKMapItem] {
        let keys = keys()
        let with = choice()
        if let tomtom = keys.tomtom, with == "tomtom" || with == "auto" || !keys.hasMapbox {
            let tt = try? await TomTomSearchProvider(key: tomtom).search(query, near: near)
            if let tt, tt.count >= 4 || !keys.hasMapbox { return tt.map(\.mapItem) }
            if let token = keys.mapbox, let mb = try? await MapboxSearchProvider(token: token).search(query, near: near) {
                let merged = PlaceMerge.merge(tt ?? [], mb)
                if !merged.isEmpty { return merged.map(\.mapItem) }
            }
            if let tt, !tt.isEmpty { return tt.map(\.mapItem) }
        } else if let token = keys.mapbox, let mb = try? await MapboxSearchProvider(token: token).search(query, near: near), !mb.isEmpty {
            return mb.map(\.mapItem)
        }
        return await Self.appleSearch(query, near: near)
    }

    static func appleSearch(_ query: String, near: CLLocationCoordinate2D) async -> [MKMapItem] {
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = query
        request.region = MKCoordinateRegion(center: near, span: MKCoordinateSpan(latitudeDelta: 0.35, longitudeDelta: 0.35))
        do { return try await MKLocalSearch(request: request).start().mapItems } catch { return [] }
    }
}

/// Which side of the road traffic keeps to, from the device region (Android `KEEP_LEFT`).
enum TrafficSide {
    static let keepLeftRegions: Set<String> = ["GB", "IE", "IM", "GG", "JE", "MT", "CY", "AU", "NZ", "JP", "IN", "ZA", "HK", "MO", "SG", "MY",
        "TH", "ID", "KE", "TZ", "UG", "ZM", "ZW", "BW", "NA", "MZ", "MW", "LS", "SZ", "PK", "BD", "LK", "NP", "BT", "JM", "TT", "BB", "BS",
        "GY", "SR", "FJ", "PG", "MU", "SC", "BN", "TL", "KY", "BM", "VG", "AG", "DM", "GD", "KN", "LC", "VC", "FK", "SH", "MV", "KI", "SB",
        "TO", "TV", "WS", "NR"]

    static var isLeftHand: Bool { keepLeftRegions.contains(Locale.current.region?.identifier ?? "") }
}
