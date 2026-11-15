import CoreLocation
import Foundation
import os

/// A fixed speed camera from OpenStreetMap, with the way it checks when that is mapped.
struct SpeedCamera: Equatable, Hashable {
    var latitude: Double
    var longitude: Double
    /// Degrees clockwise from north; nil when unknown or both ways.
    var direction: Double?

    var coordinate: CLLocationCoordinate2D { CLLocationCoordinate2D(latitude: latitude, longitude: longitude) }
}

/// A camera placed on the route, `offset` metres from its start.
struct PlacedCamera: Equatable {
    var camera: SpeedCamera
    var offset: CLLocationDistance
}

/// Speed cameras along a route and the warnings for them, ported from the Android `Overpass.routeFeatures`,
/// `NavEngine.mergeCams` and the camera check in `NavEngine.tick`.
enum SpeedCameras {
    static let endpoints = [
        URL(string: "https://overpass-api.de/api/interpreter")!,
        URL(string: "https://overpass.kumi.systems/api/interpreter")!,
        URL(string: "https://overpass.private.coffee/api/interpreter")!,
        URL(string: "https://maps.mail.ru/osm/tools/overpass/api/interpreter")!,
    ]

    /// Countries where camera warnings are against the law (Android `CAMERA_BANNED`).
    static let bannedCountries: Set<String> = ["FR", "DE", "CH"]

    /// Speed cameras within 50 m of the route line, from Overpass, trying each public server in turn.
    static func fetch(along route: RouteOption) async throws -> [SpeedCamera] {
        let line = thinned(route.coordinates, maxPoints: 300).map { String(format: "%.5f,%.5f", $0.latitude, $0.longitude) }.joined(separator: ",")
        let query = "[out:json][timeout:25];node(around:50,\(line))[\"highway\"=\"speed_camera\"];out 3000;"
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._*")
        let body = Data(("data=" + (query.addingPercentEncoding(withAllowedCharacters: allowed) ?? query)).utf8)
        var last: Error = URLError(.cannotConnectToHost)
        for endpoint in endpoints {
            do {
                let data = try await ProviderHTTP.data(endpoint, provider: "Overpass", timeout: 35, body: body, contentType: "application/x-www-form-urlencoded")
                return try parse(data)
            } catch {
                last = error
            }
        }
        throw last
    }

    /// Parses an Overpass answer. A busy server answers "OK" with a remark saying it gave up: that is a failure, not "none".
    static func parse(_ data: Data) throws -> [SpeedCamera] {
        let root = try JSON.object(data, provider: "Overpass")
        let remark = root.string("remark").lowercased()
        if remark.contains("error") || remark.contains("timed out") || remark.contains("timeout") || remark.contains("out of memory") {
            throw ProviderError.badResponse(provider: "Overpass")
        }
        return root.array("elements").compactMap { e in
            guard let lat = e.double("lat"), let lon = e.double("lon") else { return nil }
            let tags = e.dict("tags") ?? [:]
            guard tags["highway"] as? String == "speed_camera" else { return nil }
            let raw = (tags["direction"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? tags["camera:direction"] as? String
            return SpeedCamera(latitude: lat, longitude: lon, direction: parseDirection(raw))
        }
    }

    /// The cameras in an offline pack's cameras.tsv (key, latitude, longitude, optional direction) inside a box.
    static func parsePackFile(_ text: String, south: Double, west: Double, north: Double, east: Double) -> [SpeedCamera] {
        text.split(separator: "\n").compactMap { line in
            let c = line.split(separator: "\t", omittingEmptySubsequences: false)
            guard c.count >= 3, let lat = Double(c[1]), let lon = Double(c[2]),
                  lat >= south, lat <= north, lon >= west, lon <= east else { return nil }
            return SpeedCamera(latitude: lat, longitude: lon, direction: c.count > 3 ? Double(c[3]) : nil)
        }
    }

    private static let cardinal: [String: Double] = ["N": 0, "NNE": 22.5, "NE": 45, "ENE": 67.5, "E": 90, "ESE": 112.5, "SE": 135, "SSE": 157.5,
        "S": 180, "SSW": 202.5, "SW": 225, "WSW": 247.5, "W": 270, "WNW": 292.5, "NW": 315, "NNW": 337.5]

    /// A direction tag as degrees, or nil when missing or covering both ways ("90;270") (Android `Overpass.parseDir`).
    static func parseDirection(_ value: String?) -> Double? {
        guard let t = value?.trimmingCharacters(in: .whitespaces).uppercased(), !t.isEmpty, !t.contains(";") else { return nil }
        if let d = cardinal[t] { return d }
        return Double(t).map { ($0.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360) }
    }

    static func thinned(_ points: [CLLocationCoordinate2D], maxPoints: Int) -> [CLLocationCoordinate2D] {
        guard points.count > maxPoints else { return points }
        let step = Double(points.count - 1) / Double(maxPoints - 1)
        return (0..<maxPoints).map { points[Int((Double($0) * step).rounded())] }
    }

    /// Cameras on the route: within 30 m of the line itself, not checking the other carriageway when their direction is
    /// known, and one alert for a camera mapped twice (Android `mergeCams`).
    static func place(_ cameras: [SpeedCamera], on route: RouteOption) -> [PlacedCamera] {
        var out: [PlacedCamera] = []
        for camera in cameras {
            guard let p = RouteGeometry.project(camera.coordinate, onto: route), p.distance < 30 else { continue }
            let i = p.segmentIndex
            if let d = camera.direction, i + 1 < route.coordinates.count,
               abs(NavigationPolicy.angleDifference(from: d, to: RouteGeometry.bearing(route.coordinates[i], route.coordinates[i + 1]))) > 75 {
                continue
            }
            if out.contains(where: { abs($0.offset - p.progress) < 40 && RouteGeometry.distance($0.camera.coordinate, camera.coordinate) < 60 }) { continue }
            out.append(PlacedCamera(camera: camera, offset: p.progress))
        }
        return out.sorted { $0.offset < $1.offset }
    }

    /// The nearest camera ahead that should be warned about now: within 500 m, or about 18 seconds at speed, and facing the
    /// car when its direction is known. Returns it with the distance to it.
    static func warning(_ placed: [PlacedCamera], progress: CLLocationDistance, speed: Double, heading: Double?) -> (PlacedCamera, CLLocationDistance)? {
        let warnAt = max(500, speed * 18)
        var best: (PlacedCamera, CLLocationDistance)?
        for c in placed {
            let ahead = c.offset - progress
            guard ahead >= -15, ahead <= warnAt, ahead < (best?.1 ?? .greatestFiniteMagnitude) else { continue }
            if let d = c.camera.direction, let heading, abs(NavigationPolicy.angleDifference(from: d, to: heading)) > 75 { continue }
            best = (c, max(0, ahead))
        }
        return best
    }
}
