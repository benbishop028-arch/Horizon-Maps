import Foundation
import Network
import os

/// Whether the phone has a usable connection. With none, routing goes straight to the offline providers and the
/// maps switch to the OpenStreetMap map, which draws downloaded areas without a network.
final class NetworkMonitor: ObservableObject, @unchecked Sendable {
    static let shared = NetworkMonitor()

    @Published private(set) var isOnline = true
    private let state = OSAllocatedUnfairLock(initialState: true)
    private let monitor = NWPathMonitor()

    /// Thread-safe read for the routing providers.
    var isOnlineNow: Bool { state.withLock { $0 } }

    private init() {
        monitor.pathUpdateHandler = { [weak self] path in
            let online = path.status == .satisfied
            self?.state.withLock { $0 = online }
            DispatchQueue.main.async { self?.isOnline = online }
        }
        monitor.start(queue: DispatchQueue(label: "com.horizonnav.ios.network"))
    }
}

/// Which map to draw: Apple Maps online (with its traffic), or the OpenStreetMap map when chosen in Settings or when there
/// is no connection, since only it can draw downloaded areas offline.
enum MapSourcePolicy {
    static func usesOpenMap(choice: String = AppSettings.mapSource, online: Bool) -> Bool {
        choice == "openstreetmap" || !online
    }
}
