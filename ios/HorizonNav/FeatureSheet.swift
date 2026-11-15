import SwiftUI

/// First-run guide for new users and a one-time "What's new" sheet for people updating.
/// Bump `featureRevision` and the what's new items whenever a visible feature changes.
struct FeatureSheet: View {
    enum Kind: String, Identifiable {
        case guide
        case whatsNew
        var id: String { rawValue }
    }

    static let featureRevision = 3
    private static let seenKey = "featureSheetRevisionSeen"

    /// The guide on first launch, what's new after an update with new features, otherwise nothing.
    static func kindToShow(defaults: UserDefaults = .standard) -> Kind? {
        let seen = defaults.integer(forKey: seenKey)
        if seen == 0 { return .guide }
        return seen < featureRevision ? .whatsNew : nil
    }

    static func markSeen(defaults: UserDefaults = .standard) {
        defaults.set(featureRevision, forKey: seenKey)
    }

    let kind: Kind
    @Environment(\.dismiss) private var dismiss

    private struct Item: Identifiable {
        let id = UUID()
        let symbol: String
        let title: LocalizedStringKey
        let detail: LocalizedStringKey
    }

    private var items: [Item] {
        var list = [
            Item(symbol: "point.topleft.down.to.point.bottomright.curvepath", title: "Choose your route",
                 detail: "Search for a place. Horizon Nav shows the fastest route and any alternatives on the map. Tap one to select it, then tap Start."),
            Item(symbol: "arrow.turn.up.right", title: "Turn-by-turn guidance",
                 detail: "The next turn and the distance to it stay at the top of the screen, and the arrival time updates as you drive."),
            Item(symbol: "arrow.triangle.2.circlepath", title: "Automatic rerouting",
                 detail: "Leave the route and Horizon Nav finds a new one from where you are, without you doing anything."),
            Item(symbol: "map.fill", title: "Traffic on the map",
                 detail: "Apple Maps traffic is shown on the phone and in the car, and route times take current traffic into account."),
            Item(symbol: "car.fill", title: "CarPlay",
                 detail: "Connect to CarPlay to search, compare routes and follow turn-by-turn directions on the car screen. A trip started on the phone continues in the car."),
            Item(symbol: "key.fill", title: "TomTom and Mapbox",
                 detail: "Add your own free TomTom or Mapbox key in Settings (the gear button) for their routes, search, traffic, lanes and speed limits. With both, the quicker route is offered first. Without a key, Apple Maps is used."),
            Item(symbol: "arrow.down.circle", title: "Offline maps",
                 detail: "Tap the download button to save a map area, or a routing pack for a country or region. With no signal the map draws saved areas, routes are planned on the phone, and the last route is kept so guidance carries on."),
            Item(symbol: "speaker.wave.2.fill", title: "Spoken directions",
                 detail: "Turns are announced before each junction, with the lane to be in. Use the speaker button to mute, or Settings for alerts only."),
            Item(symbol: "road.lanes", title: "Lanes, speed limits and cameras",
                 detail: "The lanes to use, the speed limit and your speed show on the phone and in CarPlay, and speed cameras ahead are warned about. CarPlay follows the car's day and night look, or your choice in Settings."),
        ]
        if kind == .guide {
            list.insert(Item(symbol: "magnifyingglass", title: "Search anywhere",
                             detail: "Type a place or address in Where to? at the bottom of the map."), at: 0)
        }
        return list
    }

    var body: some View {
        NavigationStack {
            List(items) { item in
                Label {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(item.title).font(.headline)
                        Text(item.detail).font(.subheadline).foregroundStyle(.secondary)
                    }
                } icon: {
                    Image(systemName: item.symbol).foregroundStyle(.cyan)
                }
                .padding(.vertical, 4)
            }
            .navigationTitle(kind == .guide ? "Welcome" : "What's new")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Continue") { dismiss() }
                }
            }
        }
        .onDisappear { Self.markSeen() }
    }
}
