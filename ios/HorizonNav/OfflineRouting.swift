import CoreLocation
import Foundation
import MapKit
import os
import Valhalla

/// On-device routing with the Valhalla engine on a downloaded pack, ported from the Android `OfflineRouter`. It uses the same
/// engine library (valhalla-mobile) and the same packs (`tiles.tar`, `valhalla.json`, `admins.sqlite`) as the Android app,
/// so it plans real routes with no network inside a downloaded area.
struct ValhallaRoutingProvider: RoutingProvider {
    let name = "Offline"
    var packs: () -> [InstalledPack] = { OfflinePackStorage.installedPacks() }

    func routes(for request: RouteRequest) async throws -> [RouteOption] {
        let destination = request.destination.placemark.coordinate
        guard let pack = OfflinePackStorage.packFor(origin: request.origin, destination: destination, among: packs()) else {
            throw RoutingError.offlineUnavailable
        }
        let body = Self.requestJSON(for: request, language: Self.language)
        let response = try await Task.detached(priority: .userInitiated) {
            try ValhallaEngines.shared.route(rawRequest: body, pack: pack)
        }.value
        var route = try Self.parse(Data(response.utf8), packName: pack.name)
        // More than 1.5 km short of the destination: the downloaded roads ran out. It must not say "you have arrived" there.
        if let end = route.coordinates.last, RouteGeometry.distance(end, destination) > 1500 {
            route.steps.removeAll { $0.isArrival }
            route.advisories.append(String(localized: "This offline route ends at the edge of the downloaded area."))
        }
        return [route]
    }

    /// Valhalla's narrative languages; anything else gets US English.
    static var language: String {
        let supported = ["bg-BG", "ca-ES", "cs-CZ", "da-DK", "de-DE", "el-GR", "en-GB", "en-US", "es-ES", "et-EE", "fi-FI", "fr-FR", "hi-IN",
                         "hu-HU", "it-IT", "ja-JP", "nb-NO", "nl-NL", "pl-PL", "pt-BR", "pt-PT", "ro-RO", "ru-RU", "sk-SK", "sl-SI", "sv-SE",
                         "tr-TR", "uk-UA"]
        let tag = AppLocale.tag
        if supported.contains(tag) { return tag }
        let language = tag.prefix { $0 != "-" }
        return supported.first { $0.hasPrefix(language + "-") } ?? "en-US"
    }

    /// The request the Android `routeIn` sends: the heading for reroutes, car costing, metric units.
    static func requestJSON(for request: RouteRequest, language: String) -> String {
        var origin: [String: Any] = ["lat": request.origin.latitude, "lon": request.origin.longitude]
        if let heading = request.heading {
            origin["heading"] = Int(heading) % 360
            origin["heading_tolerance"] = 60
        }
        let destination = request.destination.placemark.coordinate
        let body: [String: Any] = [
            "locations": [origin, ["lat": destination.latitude, "lon": destination.longitude]],
            "costing": "auto",
            "directions_options": ["units": "kilometers", "language": language],
        ]
        let data = (try? JSONSerialization.data(withJSONObject: body, options: [.sortedKeys])) ?? Data()
        return String(decoding: data, as: UTF8.self)
    }

    /// Valhalla manoeuvre types to the Android names (Android `OfflineRouter.manText`). Nil for types that are not a step.
    static func maneuver(type: Int, roundaboutExits exits: Int) -> Maneuver? {
        switch type {
        case 1, 2, 3: return .depart
        case 4: return .arrive
        case 5: return .arriveRight
        case 6: return .arriveLeft
        case 7, 8, 25, 37, 38: return .follow
        case 9: return .bearRight
        case 10: return .turnRight
        case 11: return .sharpRight
        case 12, 13: return .uTurn
        case 14: return .sharpLeft
        case 15: return .turnLeft
        case 16: return .bearLeft
        case 17, 22: return .straight
        case 18, 20: return .motorwayExitRight
        case 19, 21: return .motorwayExitLeft
        case 23: return .keepRight
        case 24: return .keepLeft
        case 26: return exits <= 1 ? .roundaboutLeft : exits == 2 ? .roundaboutCross : exits == 3 ? .roundaboutRight : .roundaboutBack
        case 28: return .takeFerry
        default: return nil
        }
    }

    /// Parses a Valhalla route answer (Android `OfflineRouter.routeIn`).
    static func parse(_ data: Data, packName: String) throws -> RouteOption {
        let root = try JSON.object(data, provider: "Offline")
        guard let trip = root.dict("trip") else {
            if root["error"] != nil { throw RoutingError.noRoute }
            throw ProviderError.badResponse(provider: "Offline")
        }
        var points: [CLLocationCoordinate2D] = []
        var maneuvers: [(base: Int, m: [String: Any])] = []
        for leg in trip.array("legs") {
            let shape = Polyline6.decode(leg.string("shape"))
            let base = points.isEmpty ? 0 : points.count - 1
            points.append(contentsOf: points.isEmpty ? shape : Array(shape.dropFirst()))
            for m in leg.array("maneuvers") { maneuvers.append((base, m)) }
        }
        guard points.count >= 2 else { throw RoutingError.noRoute }
        let cumulative = RouteGeometry.cumulativeDistances(points)
        var steps: [RouteStep] = []
        for (base, m) in maneuvers {
            let exits = m.int("roundabout_exit_count") ?? 0
            guard let maneuver = maneuver(type: m.int("type") ?? 0, roundaboutExits: exits), maneuver != .depart else { continue }
            let index = min(max(base + (m.int("begin_shape_index") ?? 0), 0), points.count - 1)
            let sign = m.dict("sign")
            steps.append(RouteStep(
                instruction: m.string("instruction"), notice: nil, distance: (m.double("length") ?? 0) * 1000,
                startProgress: cumulative[index], turnAngle: RouteGeometry.turnAngle(in: points, at: index) ?? maneuver.angle,
                isArrival: maneuver.isArrival, maneuver: maneuver, street: (m["street_names"] as? [String])?.first ?? "",
                roundaboutExit: maneuver.isRoundabout ? exits : 0,
                exitNumber: sign?.array("exit_number_elements").first?.string("text") ?? "",
                signpost: sign?.array("exit_toward_elements").first?.string("text") ?? ""))
        }
        let summary = trip.dict("summary") ?? [:]
        let length = (summary.double("length") ?? 0) * 1000
        return RouteOption(name: packName, distance: length > 0 ? length : cumulative.last ?? 0,
                           expectedTravelTime: summary.double("time") ?? 0, coordinates: points, steps: steps,
                           advisories: [], source: .offline(String(localized: "Offline map")))
    }
}

/// One Valhalla engine per pack, kept open and reused, rebuilt only when the pack's tiles change (Android `OfflineRouter.engine`).
final class ValhallaEngines: @unchecked Sendable {
    static let shared = ValhallaEngines()
    private let lock = NSLock()
    private var engines: [String: (stamp: Date, engine: Valhalla)] = [:]

    func route(rawRequest: String, pack: InstalledPack) throws -> String {
        lock.lock()
        defer { lock.unlock() }
        let stamp = (try? FileManager.default.attributesOfItem(atPath: pack.tilesURL.path)[.modificationDate] as? Date) ?? .distantPast
        let engine: Valhalla
        if let cached = engines[pack.id], cached.stamp == stamp {
            engine = cached.engine
        } else {
            engines[pack.id]?.engine.close()
            engine = try Valhalla(configPath: try OfflinePackStorage.deviceConfig(for: pack).path)
            engines[pack.id] = (stamp, engine)
        }
        return try engine.route(rawRequest: rawRequest)
    }

    func close(_ id: String) {
        lock.lock()
        engines.removeValue(forKey: id)?.engine.close()
        lock.unlock()
    }
}

/// A routing pack on the device.
struct InstalledPack: Identifiable, Equatable {
    let id: String
    let name: String
    let folder: URL
    /// South, west, north, east, read from the pack's map header at download time.
    let bounds: [Double]?
    let bytes: Int64

    var tilesURL: URL { folder.appendingPathComponent("tiles.tar") }
    var camerasURL: URL { folder.appendingPathComponent("cameras.tsv") }

    func contains(_ c: CLLocationCoordinate2D) -> Bool {
        guard let b = bounds, b.count == 4 else { return false }
        return c.latitude >= b[0] && c.latitude <= b[2] && c.longitude >= b[1] && c.longitude <= b[3]
    }
}

/// Where packs live and how they are chosen, as in the Android `OfflineRouter` (packs/<id>/...).
enum OfflinePackStorage {
    static var root: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let url = base.appendingPathComponent("packs", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var mutable = url
        try? mutable.setResourceValues(values)
        return url
    }

    static func folder(_ id: String) -> URL { root.appendingPathComponent(id, isDirectory: true) }

    static func isInstalled(_ folder: URL) -> Bool {
        let fm = FileManager.default
        let tiles = (try? fm.attributesOfItem(atPath: folder.appendingPathComponent("tiles.tar").path)[.size] as? Int64) ?? 0
        return tiles > 100_000 && fm.fileExists(atPath: folder.appendingPathComponent("valhalla.json").path)
    }

    static func installedPacks() -> [InstalledPack] {
        let fm = FileManager.default
        let ids = (try? fm.contentsOfDirectory(atPath: root.path)) ?? []
        return ids.sorted().compactMap { id in
            let folder = folder(id)
            guard isInstalled(folder) else { return nil }
            let bounds = (try? String(contentsOf: folder.appendingPathComponent("bounds.txt"), encoding: .utf8))?
                .split(separator: ",").compactMap { Double($0.trimmingCharacters(in: .whitespacesAndNewlines)) }
            let bytes = ((try? fm.contentsOfDirectory(at: folder, includingPropertiesForKeys: [.fileSizeKey])) ?? [])
                .reduce(Int64(0)) { $0 + Int64((try? $1.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0) }
            return InstalledPack(id: id, name: PackCatalog.region(id)?.name ?? id, folder: folder,
                                 bounds: bounds?.count == 4 ? bounds : nil, bytes: bytes)
        }
    }

    /// The smallest pack that covers the whole trip, else one that covers either end (the route then stops at its edge),
    /// else the only pack when its area is not known (Android `areaFor`).
    static func packFor(origin: CLLocationCoordinate2D, destination: CLLocationCoordinate2D, among packs: [InstalledPack]) -> InstalledPack? {
        func area(_ p: InstalledPack) -> Double { p.bounds.map { ($0[2] - $0[0]) * ($0[3] - $0[1]) } ?? .greatestFiniteMagnitude }
        if let whole = packs.filter({ $0.contains(origin) && $0.contains(destination) }).min(by: { area($0) < area($1) }) { return whole }
        if let start = packs.filter({ $0.contains(origin) }).min(by: { area($0) < area($1) }) { return start }
        if let end = packs.filter({ $0.contains(destination) }).min(by: { area($0) < area($1) }) { return end }
        return packs.count == 1 && packs[0].bounds == nil ? packs[0] : nil
    }

    /// The build machine's valhalla.json points at its own folders: the paths are rewritten for this device
    /// (Android `OfflineRouter.deviceConfig`).
    static func deviceConfig(for pack: InstalledPack) throws -> URL {
        let data = try Data(contentsOf: pack.folder.appendingPathComponent("valhalla.json"))
        guard var config = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              var mjolnir = config["mjolnir"] as? [String: Any] else { throw RoutingError.offlineUnavailable }
        mjolnir["tile_extract"] = pack.tilesURL.path
        mjolnir["tile_dir"] = pack.folder.appendingPathComponent("tiles").path
        mjolnir["admin"] = pack.folder.appendingPathComponent("admins.sqlite").path
        mjolnir.removeValue(forKey: "timezone")
        config["mjolnir"] = mjolnir
        let out = pack.folder.appendingPathComponent("valhalla_device.json")
        try JSONSerialization.data(withJSONObject: config).write(to: out)
        return out
    }

    /// The area in a PMTiles v3 header (bytes 102 to 117: west, south, east, north as 1e-7 degrees), as south, west, north, east.
    static func bounds(fromPMTilesHeader h: Data) -> [Double]? {
        guard h.count >= 118, String(decoding: h.prefix(7), as: UTF8.self) == "PMTiles", h[h.startIndex + 7] == 3 else { return nil }
        func i32(_ o: Int) -> Double {
            let b = h.startIndex + o
            let v = UInt32(h[b]) | UInt32(h[b + 1]) << 8 | UInt32(h[b + 2]) << 16 | UInt32(h[b + 3]) << 24
            return Double(Int32(bitPattern: v)) / 1e7
        }
        let w = i32(102), s = i32(106), e = i32(110), n = i32(114)
        return n > s && e > w ? [s, w, n, e] : nil
    }
}

/// Downloads, lists and deletes routing packs, with progress, from the same GitHub releases the Android app uses.
@MainActor
final class OfflinePackStore: ObservableObject {
    static let shared = OfflinePackStore()

    @Published private(set) var installed: [InstalledPack] = OfflinePackStorage.installedPacks()
    @Published private(set) var downloadingID: String?
    @Published private(set) var progress: Double = 0
    @Published private(set) var status = ""

    private var task: Task<Void, Never>?
    private let log = Logger(subsystem: "com.horizonnav.ios", category: "offline")

    func refresh() { installed = OfflinePackStorage.installedPacks() }

    func download(_ region: PackRegion) {
        guard downloadingID == nil else { return }
        downloadingID = region.id
        progress = 0
        status = String(localized: "Starting")
        task = Task {
            do {
                try await PackDownloader(region: region) { fraction, text in
                    Task { @MainActor in self.progress = fraction; self.status = text }
                }.run()
                status = String(localized: "Download finished")
                log.info("Pack \(region.id, privacy: .public) installed")
            } catch let error as URLError where error.code == .cancelled {
                status = String(localized: "Download cancelled")
            } catch is CancellationError {
                status = String(localized: "Download cancelled")
            } catch {
                status = error.localizedDescription
                log.error("Pack \(region.id, privacy: .public) failed: \(error.localizedDescription, privacy: .public)")
            }
            downloadingID = nil
            refresh()
        }
    }

    func cancel() { task?.cancel() }

    func delete(_ pack: InstalledPack) {
        ValhallaEngines.shared.close(pack.id)
        try? FileManager.default.removeItem(at: pack.folder)
        refresh()
    }
}

/// Fetches one pack's files (Android `OfflineRouter.download`): the config and admin areas, then the road tiles, whole or in
/// numbered parts that are joined here. Only the files routing needs are fetched; the map comes from the map areas instead.
struct PackDownloader {
    let region: PackRegion
    let report: (Double, String) -> Void

    enum Failure: LocalizedError {
        case missing(String)
        var errorDescription: String? {
            if case let .missing(name) = self { return String(localized: "This area's pack has no \(name) yet. It may not have been built.") }
            return nil
        }
    }

    func run() async throws {
        let folder = OfflinePackStorage.folder(region.id)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        ValhallaEngines.shared.close(region.id)
        let base = region.url
        for name in ["valhalla.json", "admins.sqlite"] {
            guard try await fetch(base.appendingPathComponent(name), to: folder.appendingPathComponent(name), label: name, share: 0.05) else {
                throw Failure.missing(name)
            }
        }
        guard try await fetchJoined(base, name: "tiles.tar", folder: folder) else { throw Failure.missing("tiles.tar") }
        // Speed cameras for warnings with no signal (optional: older packs have none).
        if !(try await fetch(region.extrasURL.appendingPathComponent("cameras.tsv"), to: folder.appendingPathComponent("cameras.tsv"), label: "cameras.tsv", share: 0)) {
            _ = try? await fetch(base.appendingPathComponent("cameras.tsv"), to: folder.appendingPathComponent("cameras.tsv"), label: "cameras.tsv", share: 0)
        }
        // The pack's area, from the first bytes of its map file, so the right pack is picked for a trip.
        if let header = try? await headerBytes(base.appendingPathComponent("map.pmtiles")),
           let bounds = OfflinePackStorage.bounds(fromPMTilesHeader: header) {
            try? bounds.map { String($0) }.joined(separator: ",").write(to: folder.appendingPathComponent("bounds.txt"), atomically: true, encoding: .utf8)
        }
        report(1, String(localized: "Download finished"))
    }

    private func headerBytes(_ url: URL) async throws -> Data {
        var request = URLRequest(url: url, timeoutInterval: 20)
        request.setValue("bytes=0-126", forHTTPHeaderField: "Range")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { throw Failure.missing("map.pmtiles") }
        return data.prefix(127)
    }

    /// Downloads one file. False when the server does not have it. A file already here with the same size is kept.
    private func fetch(_ url: URL, to destination: URL, label: String, share: Double, offset: Double = 0) async throws -> Bool {
        let delegate = DownloadProgress { fraction, written, expected in
            let mb = String(format: "%.0f", Double(written) / 1_048_576)
            let total = expected > 0 ? String(format: " of %.0f MB", Double(expected) / 1_048_576) : ""
            report(offset + share * fraction, String(localized: "Downloading \(label)… \(mb) MB\(total)"))
        }
        let (temp, response) = try await URLSession.shared.download(from: url, delegate: delegate)
        defer { try? FileManager.default.removeItem(at: temp) }
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { return false }
        try? FileManager.default.removeItem(at: destination)
        try FileManager.default.moveItem(at: temp, to: destination)
        return true
    }

    /// A file either whole on the server or, when too big for one release file, in parts name.part00, .part01... joined here.
    private func fetchJoined(_ base: URL, name: String, folder: URL) async throws -> Bool {
        let destination = folder.appendingPathComponent(name)
        if try await fetch(base.appendingPathComponent(name), to: destination, label: name, share: 0.9, offset: 0.1) { return true }
        var parts: [URL] = []
        while true {
            let partName = String(format: "%@.part%02d", name, parts.count)
            let part = folder.appendingPathComponent(partName)
            let share = 0.9 / Double(max(parts.count + 2, 2))
            guard try await fetch(base.appendingPathComponent(partName), to: part, label: partName, share: share, offset: 0.1 + share * Double(parts.count)) else { break }
            parts.append(part)
        }
        guard !parts.isEmpty else { return false }
        report(0.98, String(localized: "Joining \(name)…"))
        let joining = folder.appendingPathComponent(name + ".joining")
        FileManager.default.createFile(atPath: joining.path, contents: nil)
        let out = try FileHandle(forWritingTo: joining)
        for part in parts {
            let input = try FileHandle(forReadingFrom: part)
            while let chunk = try input.read(upToCount: 8 << 20), !chunk.isEmpty { try out.write(contentsOf: chunk) }
            try input.close()
            try FileManager.default.removeItem(at: part)
        }
        try out.close()
        try? FileManager.default.removeItem(at: destination)
        try FileManager.default.moveItem(at: joining, to: destination)
        return true
    }
}

/// Reports a download task's progress.
final class DownloadProgress: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    private let onProgress: (Double, Int64, Int64) -> Void
    private var observation: NSKeyValueObservation?

    init(onProgress: @escaping (Double, Int64, Int64) -> Void) { self.onProgress = onProgress }

    func urlSession(_ session: URLSession, didCreateTask task: URLSessionTask) {
        observation = task.progress.observe(\.fractionCompleted) { [onProgress] progress, _ in
            onProgress(progress.fractionCompleted, task.countOfBytesReceived, task.countOfBytesExpectedToReceive)
        }
    }
}

/// Tries each offline provider in turn: the on-device router, then the saved route.
struct OfflineRoutingChain: RoutingProvider {
    var providers: [RoutingProvider]
    var name: String { providers.first?.name ?? "Offline" }

    func routes(for request: RouteRequest) async throws -> [RouteOption] {
        var last: Error = RoutingError.offlineUnavailable
        for provider in providers {
            do {
                let routes = try await provider.routes(for: request)
                if !routes.isEmpty { return routes }
            } catch {
                last = error
            }
        }
        throw last
    }
}
