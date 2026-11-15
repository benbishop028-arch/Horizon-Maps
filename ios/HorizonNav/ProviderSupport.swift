import CoreLocation
import Foundation
import MapKit

/// A failed request to a keyed provider, explained in plain words for the common cases (Android `TomTomRest.httpError`).
enum ProviderError: LocalizedError, Equatable {
    case http(provider: String, status: Int, body: String)
    case noRoute(provider: String)
    case badResponse(provider: String)

    var errorDescription: String? {
        switch self {
        case let .http(provider, status, body):
            switch status {
            case 401, 403:
                return provider == "TomTom"
                    ? String(localized: "TomTom didn't accept the API key. Check it in Settings (a free key from developer.tomtom.com needs the Search and Routing APIs).")
                    : String(localized: "Mapbox didn't accept your access token. Copy it again from account.mapbox.com > Tokens.")
            case 429:
                return String(localized: "This \(provider) key has reached its limit. Try again later.")
            default:
                return "\(provider) HTTP \(status) \(body.prefix(140))"
            }
        case let .noRoute(provider): return String(localized: "\(provider) found no route.")
        case let .badResponse(provider): return String(localized: "\(provider) sent an answer the app could not read.")
        }
    }

    /// A refused key or a used-up allowance: trying other request variants cannot help (Android stops its attempts there).
    var isRefusal: Bool {
        if case let .http(_, status, _) = self { return status == 401 || status == 403 || status == 429 }
        return false
    }
}

enum ProviderHTTP {
    static var session: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.httpAdditionalHeaders = ["User-Agent": "HorizonNav-iOS/1.0"]
        configuration.waitsForConnectivity = false
        return URLSession(configuration: configuration)
    }()

    /// GET or POST a URL, returning the body for a 2xx answer. Connection failures are thrown as the system's
    /// `URLError`, so `RoutingFallbackPolicy` recognises them and the offline router takes over.
    static func data(_ url: URL, provider: String, timeout: TimeInterval = 20, body: Data? = nil,
                     contentType: String = "application/json") async throws -> Data {
        var request = URLRequest(url: url, timeoutInterval: timeout)
        if let body {
            request.httpMethod = "POST"
            request.httpBody = body
            request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        }
        let (data, response) = try await session.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            throw ProviderError.http(provider: provider, status: status, body: String(decoding: data.prefix(400), as: UTF8.self))
        }
        return data
    }
}

/// The app's language for provider instructions and the voice: the localization the app runs in, with the
/// device region (so English becomes en-GB in the UK), the same role as the Android `L.locale`.
enum AppLocale {
    static var current: Locale {
        let language = Bundle.main.preferredLocalizations.first ?? "en"
        let device = Locale.current
        if device.language.languageCode?.identifier == Locale(identifier: language).language.languageCode?.identifier { return device }
        return Locale(identifier: language)
    }

    /// BCP 47 tag such as "en-GB".
    static var tag: String {
        let locale = current
        let language = locale.language.languageCode?.identifier ?? "en"
        if let script = locale.language.script?.identifier, language == "zh" { return "zh-\(script)" }
        if let region = locale.region?.identifier { return "\(language)-\(region)" }
        return language
    }

    static var usesImperialDistances: Bool {
        let region = current.region?.identifier ?? Locale.current.region?.identifier
        return region == "GB" || region == "US" || region == "LR" || region == "MM"
    }
}

/// A place found by a provider search.
struct FoundPlace: Equatable {
    var name: String
    var subtitle: String
    var coordinate: CLLocationCoordinate2D

    static func == (a: FoundPlace, b: FoundPlace) -> Bool {
        a.name == b.name && a.subtitle == b.subtitle && a.coordinate.latitude == b.coordinate.latitude && a.coordinate.longitude == b.coordinate.longitude
    }

    var mapItem: MKMapItem {
        let item = MKMapItem(placemark: MKPlacemark(coordinate: coordinate, addressDictionary: subtitle.isEmpty ? nil : ["Street": subtitle]))
        item.name = name
        return item
    }
}

/// A place search behind the same kind of boundary as `RoutingProvider`.
protocol PlaceSearchProvider {
    var name: String { get }
    func search(_ query: String, near: CLLocationCoordinate2D?) async throws -> [FoundPlace]
}

enum PlaceMerge {
    /// Adds places not already in the list: a different name within 80 m, or anything within 15 m, is a duplicate
    /// (Android `MapboxRest.geocode` and `NavEngine.netGeocode`).
    static func merge(_ base: [FoundPlace], _ more: [FoundPlace]) -> [FoundPlace] {
        var out = base
        for p in more where !out.contains(where: { q in
            let d = RouteGeometry.distance(q.coordinate, p.coordinate)
            return (q.name.caseInsensitiveCompare(p.name) == .orderedSame && d < 80) || d < 15
        }) { out.append(p) }
        return out
    }
}

/// Decodes Google's encoded polyline with six decimal places (Valhalla `shape`).
enum Polyline6 {
    static func decode(_ s: String) -> [CLLocationCoordinate2D] {
        var out: [CLLocationCoordinate2D] = []
        let bytes = Array(s.utf8)
        var i = 0, lat = 0, lon = 0
        func next() -> Int? {
            var shift = 0, result = 0
            while i < bytes.count {
                let b = Int(bytes[i]) - 63
                i += 1
                result |= (b & 31) << shift
                shift += 5
                if b < 32 { return (result & 1) != 0 ? ~(result >> 1) : result >> 1 }
            }
            return nil
        }
        while i < bytes.count {
            guard let dLat = next(), let dLon = next() else { break }
            lat += dLat
            lon += dLon
            out.append(CLLocationCoordinate2D(latitude: Double(lat) / 1e6, longitude: Double(lon) / 1e6))
        }
        return out
    }
}

extension RouteGeometry {
    /// Index of the last coordinate at or before a distance along the route.
    static func index(atProgress progress: CLLocationDistance, cumulative: [CLLocationDistance]) -> Int {
        guard !cumulative.isEmpty else { return 0 }
        var lo = 0, hi = cumulative.count - 1
        while lo < hi {
            let mid = (lo + hi + 1) / 2
            if cumulative[mid] <= progress { lo = mid } else { hi = mid - 1 }
        }
        return lo
    }
}

/// JSON helpers for the provider parsers, which read loosely typed answers the way the Android `optJSON...` calls do.
extension Dictionary where Key == String, Value == Any {
    func dict(_ key: String) -> [String: Any]? { self[key] as? [String: Any] }
    func array(_ key: String) -> [[String: Any]] { self[key] as? [[String: Any]] ?? [] }
    func string(_ key: String) -> String { self[key] as? String ?? "" }
    func double(_ key: String) -> Double? { (self[key] as? NSNumber)?.doubleValue }
    func int(_ key: String) -> Int? { (self[key] as? NSNumber)?.intValue }
    func bool(_ key: String) -> Bool { (self[key] as? NSNumber)?.boolValue ?? false }
}

enum JSON {
    static func object(_ data: Data, provider: String) throws -> [String: Any] {
        guard let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw ProviderError.badResponse(provider: provider) }
        return o
    }
}
