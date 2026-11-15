import CoreLocation
import Foundation
import MapLibre
import os

/// Map areas kept on the device with MapLibre's offline packs: OpenStreetMap tiles for a bounding box and zoom range, or
/// for the strip along a route (the Android `MapboxOffline` circle round home and strips along routes). Each area is saved
/// for the day and the night style, so either draws with no network.
@MainActor
final class OfflineMapStore: ObservableObject {
    static let shared = OfflineMapStore()

    struct Area: Identifiable, Equatable {
        let id: String
        var name: String
        var completed: UInt64
        var expected: UInt64
        var bytes: UInt64
        var complete: Bool

        var fraction: Double { expected > 0 ? Double(completed) / Double(expected) : 0 }
    }

    @Published private(set) var areas: [Area] = []
    @Published var message: String?

    /// OpenFreeMap's vector tiles stop at zoom 14 and are drawn larger beyond it.
    static let maximumZoom = 14.0
    /// About 200 MB of vector tiles; bigger areas are refused with a request to pick a smaller one.
    static let tileLimit = 60_000

    private var observers: [NSObjectProtocol] = []
    private var packsObservation: NSKeyValueObservation?
    private let log = Logger(subsystem: "com.horizonnav.ios", category: "offline-map")

    private init() {
        let center = NotificationCenter.default
        observers.append(center.addObserver(forName: .MLNOfflinePackProgressChanged, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.reload() }
        })
        observers.append(center.addObserver(forName: .MLNOfflinePackError, object: nil, queue: .main) { [weak self] note in
            let error = note.userInfo?[MLNOfflinePackUserInfoKey.error] as? NSError
            MainActor.assumeIsolated { self?.message = error?.localizedDescription }
        })
        packsObservation = MLNOfflineStorage.shared.observe(\.packs, options: [.initial, .new]) { [weak self] _, _ in
            DispatchQueue.main.async { self?.reload() }
        }
    }

    private struct Context: Codable {
        var area: String
        var name: String
        var style: String
    }

    private func context(of pack: MLNOfflinePack) -> Context? { try? JSONDecoder().decode(Context.self, from: pack.context) }

    func reload() {
        var byArea: [String: Area] = [:]
        var order: [String] = []
        for pack in MLNOfflineStorage.shared.packs ?? [] {
            guard let ctx = context(of: pack) else { continue }
            if pack.state == .unknown { pack.requestProgress() }
            let p = pack.progress
            if byArea[ctx.area] == nil { order.append(ctx.area) }
            var area = byArea[ctx.area] ?? Area(id: ctx.area, name: ctx.name, completed: 0, expected: 0, bytes: 0, complete: true)
            area.completed += p.countOfResourcesCompleted
            area.expected += max(p.countOfResourcesExpected, p.countOfResourcesCompleted)
            area.bytes += p.countOfBytesCompleted
            area.complete = area.complete && pack.state == .complete
            byArea[ctx.area] = area
        }
        areas = order.compactMap { byArea[$0] }
    }

    /// Tiles in a box over a zoom range, for the size check before downloading.
    static func tileCount(south: Double, west: Double, north: Double, east: Double, zoom: ClosedRange<Double>) -> Int {
        func x(_ lon: Double, _ z: Int) -> Int { Int(floor((lon + 180) / 360 * pow(2, Double(z)))) }
        func y(_ lat: Double, _ z: Int) -> Int {
            let r = min(max(lat, -85.0511), 85.0511) * .pi / 180
            return Int(floor((1 - Foundation.log(tan(r) + 1 / cos(r)) / .pi) / 2 * pow(2, Double(z))))
        }
        var total = 0
        for z in Int(zoom.lowerBound)...Int(zoom.upperBound) {
            total += (abs(x(east, z) - x(west, z)) + 1) * (abs(y(south, z) - y(north, z)) + 1)
        }
        return total
    }

    /// Saves the tiles for a bounding box from `minimumZoom` to zoom 14, in both styles.
    func downloadArea(name: String, bounds: MLNCoordinateBounds, minimumZoom: Double) {
        let zoom = max(0, min(minimumZoom, Self.maximumZoom - 1))...Self.maximumZoom
        let tiles = Self.tileCount(south: bounds.sw.latitude, west: bounds.sw.longitude, north: bounds.ne.latitude, east: bounds.ne.longitude, zoom: zoom)
        guard tiles <= Self.tileLimit else {
            message = String(localized: "That area is too big to save at street detail. Zoom in and try again.")
            return
        }
        let id = UUID().uuidString
        for (style, url) in [("day", OpenMapStyle.day), ("night", OpenMapStyle.night)] {
            let region = MLNTilePyramidOfflineRegion(styleURL: url, bounds: bounds, fromZoomLevel: zoom.lowerBound, toZoomLevel: zoom.upperBound)
            add(region, Context(area: id, name: name, style: style))
        }
    }

    /// Saves the tiles along a route's line from zoom 8 to 14, in both styles.
    func downloadRoute(name: String, coordinates: [CLLocationCoordinate2D]) {
        guard coordinates.count > 1 else { return }
        var coords = coordinates
        let line = MLNPolylineFeature(coordinates: &coords, count: UInt(coords.count))
        let id = UUID().uuidString
        for (style, url) in [("day", OpenMapStyle.day), ("night", OpenMapStyle.night)] {
            let region = MLNShapeOfflineRegion(styleURL: url, shape: line, fromZoomLevel: 8, toZoomLevel: Self.maximumZoom)
            add(region, Context(area: id, name: name, style: style))
        }
    }

    private func add(_ region: MLNOfflineRegion & NSObjectProtocol, _ context: Context) {
        guard let data = try? JSONEncoder().encode(context) else { return }
        MLNOfflineStorage.shared.addPack(for: region, withContext: data) { [weak self] pack, error in
            if let error {
                MainActor.assumeIsolated { self?.message = error.localizedDescription }
                return
            }
            pack?.resume()
            MainActor.assumeIsolated { self?.reload() }
        }
        log.info("Saving map area \(context.name, privacy: .public) (\(context.style, privacy: .public))")
    }

    func delete(_ area: Area) {
        for pack in MLNOfflineStorage.shared.packs ?? [] where context(of: pack)?.area == area.id {
            MLNOfflineStorage.shared.removePack(pack) { [weak self] _ in
                MainActor.assumeIsolated { self?.reload() }
            }
        }
    }
}
