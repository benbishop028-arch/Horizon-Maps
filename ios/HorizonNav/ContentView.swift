import MapKit
import SwiftUI

struct ContentView: View {
    @ObservedObject var store: NavigationStore
    @State private var results: [MKMapItem] = []
    @State private var cameraPosition: MapCameraPosition = .automatic
    @State private var featureSheet: FeatureSheet.Kind? = FeatureSheet.kindToShow()

    var body: some View {
        NavigationStack {
            ZStack(alignment: .topTrailing) {
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

                locationButton
                    .padding(.trailing)
                    .padding(.top, 8)
            }
            .safeAreaInset(edge: .top) { maneuverBanner }
            .safeAreaInset(edge: .bottom) { searchPanel }
            .navigationTitle("Horizon Nav")
            .toolbar {
                if store.selectedRoute != nil { Button("End route", role: .destructive) { store.stopNavigating() } }
            }
            .sheet(item: $featureSheet) { kind in FeatureSheet(kind: kind) }
        }
    }

    @ViewBuilder
    private var maneuverBanner: some View {
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
