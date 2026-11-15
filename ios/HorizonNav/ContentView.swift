import MapKit
import SwiftUI

struct ContentView: View {
    @ObservedObject var store: NavigationStore
    @State private var results: [MKMapItem] = []
    @State private var cameraPosition: MapCameraPosition = .automatic
    @State private var featureSheet: FeatureSheet.Kind? = FeatureSheet.kindToShow()
    @State private var showsSettings = false
    @State private var showsOfflineMaps = false
    @ObservedObject private var network = NetworkMonitor.shared
    @AppStorage(AppSettings.Keys.mapSource) private var mapSource = "apple"
    @AppStorage(AppSettings.Keys.soundMode) private var soundMode = 0
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        NavigationStack {
            ZStack(alignment: .topTrailing) {
                if MapSourcePolicy.usesOpenMap(choice: mapSource, online: network.isOnline) {
                    OpenMap(routes: store.routeOptions, selected: store.selectedRouteIndex, following: store.phase == .guiding)
                        .ignoresSafeArea()
                } else {
                    appleMap
                }

                VStack(spacing: 10) {
                    locationButton
                    muteButton
                }
                .padding(.trailing)
                .padding(.top, 8)
            }
            .overlay(alignment: .topLeading) {
                if store.phase == .guiding || store.phase == .rerouting {
                    SpeedSignBadge(limit: store.speedLimit, speed: store.currentSpeed)
                        .padding(.leading)
                        .padding(.top, 8)
                }
            }
            .onChange(of: colorScheme) { NotificationCenter.default.post(name: .phoneAppearanceChanged, object: nil) }
            .safeAreaInset(edge: .top) { maneuverBanner }
            .safeAreaInset(edge: .bottom) { searchPanel }
            .navigationTitle("Horizon Nav")
            .toolbar {
                if store.selectedRoute != nil { Button("End route", role: .destructive) { store.stopNavigating() } }
                Button { showsOfflineMaps = true } label: { Image(systemName: "arrow.down.circle") }
                    .accessibilityLabel("Offline maps")
                Button { showsSettings = true } label: { Image(systemName: "gearshape") }
                    .accessibilityLabel("Settings")
            }
            .sheet(item: $featureSheet) { kind in FeatureSheet(kind: kind) }
            .sheet(isPresented: $showsSettings) { SettingsView() }
            .sheet(isPresented: $showsOfflineMaps) { OfflineMapsView(store: store) }
        }
    }

    /// Apple Maps with its live traffic, the map used online by default.
    private var appleMap: some View {
        Map(position: $cameraPosition) {
            UserAnnotation()
            ForEach(Array(store.routeOptions.enumerated()), id: \.element.id) { index, option in
                if index != store.selectedRouteIndex {
                    MapPolyline(coordinates: option.coordinates).stroke(.gray.opacity(0.7), lineWidth: 6)
                }
            }
            if let route = store.selectedRoute { MapPolyline(coordinates: route.coordinates).stroke(.cyan, lineWidth: 7) }
        }
        .mapStyle(.standard(elevation: .realistic, showsTraffic: true))
    }

    @ViewBuilder
    private var maneuverBanner: some View {
        if let alert = store.activeAlert {
            Label {
                Text("\(alert.title) · \(alert.detail)")
            } icon: {
                Image(systemName: alert.symbol)
            }
            .font(.headline)
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding()
            .background(Color.orange)
        }
        if store.phase == .rerouting {
            Label("Rerouting", systemImage: "arrow.triangle.2.circlepath")
                .font(.headline)
                .frame(maxWidth: .infinity)
                .padding()
                .background(.regularMaterial)
        } else if store.phase == .guiding, let route = store.selectedRoute, let update = store.guidance,
                  let index = update.upcomingStepIndex, route.steps.indices.contains(index) {
            let step = route.steps[index]
            HStack(spacing: 14) {
                Image(systemName: step.symbolName).font(.largeTitle.weight(.semibold))
                VStack(alignment: .leading) {
                    Text(Measurement(value: update.distanceToManeuver, unit: UnitLength.meters).formatted(.measurement(width: .abbreviated, usage: .road)))
                        .font(.title2.weight(.bold))
                    Text(step.instruction).font(.headline).lineLimit(2)
                    if let lanes = route.laneSection(forStep: index, progress: update.progress,
                                                     fastRoad: NavigationStore.isFastRoad(step: step, route: route, progress: update.progress)),
                       let image = LaneImage.image(LanePolicy.marked(lanes, maneuver: step.maneuver)) {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFit()
                            .frame(height: 36)
                            .padding(4)
                            .background(Color.black.opacity(0.75), in: RoundedRectangle(cornerRadius: 6))
                            .accessibilityLabel(LanePolicy.advice(lanes, maneuver: step.maneuver) ?? "")
                    }
                }
                Spacer()
            }
            .padding()
            .background(.regularMaterial)
        } else if store.phase == .arrived {
            Label("You have arrived", systemImage: "flag.checkered")
                .font(.headline)
                .frame(maxWidth: .infinity)
                .padding()
                .background(.regularMaterial)
        }
    }

    private var locationButton: some View {
        Button {
            cameraPosition = .userLocation(fallback: .automatic)
        } label: {
            Image(systemName: "location.fill")
                .font(.title3)
                .frame(width: 48, height: 48)
        }
        .buttonStyle(.plain)
        .horizonGlassControl(in: .circle)
        .accessibilityLabel("Show my location")
    }

    /// Mutes or unmutes spoken guidance (Settings has the alerts-only choice too).
    private var muteButton: some View {
        Button {
            VoiceGuidance.shared.toggleMute()
        } label: {
            Image(systemName: soundMode == 2 ? "speaker.slash.fill" : "speaker.wave.2.fill")
                .font(.title3)
                .frame(width: 48, height: 48)
        }
        .buttonStyle(.plain)
        .horizonGlassControl(in: .circle)
        .accessibilityLabel(soundMode == 2 ? "Unmute voice guidance" : "Mute voice guidance")
    }

    private var searchPanel: some View {
        VStack(spacing: 12) {
            HStack {
                Image(systemName: "magnifyingglass")
                TextField("Where to?", text: $store.searchText)
                    .textInputAutocapitalization(.words)
                    .submitLabel(.search)
                    .onSubmit { Task { results = await store.search(store.searchText) } }
            }
            .padding(14)
            .background(.thinMaterial, in: Capsule())
            if !network.isOnline {
                Label("No signal: using offline maps", systemImage: "wifi.slash").font(.caption).foregroundStyle(.secondary)
            }
            if store.phase == .idle, results.isEmpty, let trip = store.savedTrip {
                HStack {
                    Button {
                        Task { await store.resumeSavedTrip() }
                    } label: {
                        Label("Resume trip to \(trip.destinationName)", systemImage: "arrow.uturn.forward.circle")
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    Button { store.forgetSavedTrip() } label: { Image(systemName: "xmark") }
                        .accessibilityLabel("Forget saved trip")
                }
                .buttonStyle(.bordered)
            }
            if store.phase == .planning { ProgressView("Finding a route") }
            if let error = store.lastError { Text(error).font(.caption).foregroundStyle(.red) }
            ForEach(results, id: \.self) { item in
                Button {
                    Task { await store.navigate(to: item); results = [] }
                } label: {
                    VStack(alignment: .leading) {
                        Text(item.name ?? "Unnamed place").font(.headline)
                        Text(item.placemark.title ?? "").font(.caption).foregroundStyle(.secondary)
                    }.frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)
            }
            if store.phase == .previewing {
                routeChoices
            } else if let route = store.selectedRoute, let destination = store.destination {
                let remaining = store.guidance?.remainingTime ?? route.expectedTravelTime
                Label("\(destination.name ?? "Destination") · \(Duration.seconds(remaining).formatted(.units(allowed: [.hours, .minutes], width: .abbreviated)))", systemImage: "arrow.triangle.turn.up.right.diamond.fill")
                    .font(.subheadline.weight(.semibold))
            }
        }
        .padding()
        .background(.regularMaterial)
    }
}

extension ContentView {
    /// Route preview: every alternative with its time and distance, and the button that starts guidance.
    private var routeChoices: some View {
        VStack(spacing: 8) {
            ForEach(Array(store.routeOptions.enumerated()), id: \.element.id) { index, option in
                Button {
                    store.selectRoute(index)
                } label: {
                    HStack {
                        VStack(alignment: .leading) {
                            Text(Duration.seconds(option.expectedTravelTime).formatted(.units(allowed: [.hours, .minutes], width: .abbreviated)))
                                .font(.headline)
                            Text("\(Measurement(value: option.distance, unit: UnitLength.meters).formatted(.measurement(width: .abbreviated, usage: .road))) · \(option.name)")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        if index == store.selectedRouteIndex { Image(systemName: "checkmark.circle.fill").foregroundStyle(.cyan) }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)
            }
            Button {
                store.startGuidance()
            } label: {
                Label("Start", systemImage: "location.north.line.fill").frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
        }
    }
}

private extension View {
    /// Uses custom Liquid Glass only for the map's floating functional controls.
    @ViewBuilder
    func horizonGlassControl(in shape: some Shape) -> some View {
        if #available(iOS 26.0, *) {
            glassEffect(.regular.interactive(), in: shape)
        } else {
            background(.ultraThinMaterial, in: shape)
        }
    }
}

extension Notification.Name {
    /// The phone switched between light and dark, for the CarPlay map set to follow the phone.
    static let phoneAppearanceChanged = Notification.Name("HorizonNavPhoneAppearanceChanged")
}
