import MapLibre
import SwiftUI

/// Offline maps: map areas to draw without a network, and routing packs to plan routes without one (the Android
/// Settings > Offline maps page).
struct OfflineMapsView: View {
    @ObservedObject var store: NavigationStore
    @ObservedObject private var maps = OfflineMapStore.shared
    @ObservedObject private var packs = OfflinePackStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var visible: MLNCoordinateBounds?
    @State private var zoom = 12.0
    @State private var areaName = ""

    var body: some View {
        NavigationStack {
            List {
                mapSection
                routingSection
                Section {
                    Text("A map area draws the map with no signal. A routing pack plans new routes and reroutes with no signal inside its area. Without a pack, the last planned route is kept on the phone, so guidance carries on along it. Live traffic, online search and new routes outside a pack need a connection.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Offline maps")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
            .alert("Offline maps", isPresented: Binding(get: { maps.message != nil }, set: { if !$0 { maps.message = nil } })) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(maps.message ?? "")
            }
        }
    }

    private var mapSection: some View {
        Section {
            OpenMap(onRegionChange: { bounds, level in
                visible = bounds
                zoom = level
            })
            .frame(height: 240)
            .listRowInsets(EdgeInsets())
            TextField("Area name", text: $areaName)
            Button {
                guard let visible else { return }
                maps.downloadArea(name: areaName.isEmpty ? String(localized: "Map area") : areaName, bounds: visible, minimumZoom: max(zoom - 4, 0))
                areaName = ""
            } label: {
                Label("Save the area shown", systemImage: "square.and.arrow.down")
            }
            .disabled(visible == nil)
            if let route = store.selectedRoute, let destination = store.destination {
                Button {
                    maps.downloadRoute(name: String(localized: "Route to \(destination.name ?? String(localized: "Destination"))"), coordinates: route.coordinates)
                } label: {
                    Label("Save the map along the route", systemImage: "point.topleft.down.to.point.bottomright.curvepath")
                }
            }
            ForEach(maps.areas) { area in
                VStack(alignment: .leading, spacing: 4) {
                    Text(area.name).font(.headline)
                    if area.complete {
                        Text(ByteCountFormatter.string(fromByteCount: Int64(area.bytes), countStyle: .file)).font(.caption).foregroundStyle(.secondary)
                    } else {
                        ProgressView(value: area.fraction)
                        Text("\(area.completed) of \(area.expected) tiles").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            .onDelete { offsets in offsets.map { maps.areas[$0] }.forEach(maps.delete) }
        } header: {
            Text("Map areas")
        } footer: {
            Text("Move the map to the area you want, then save it. Areas are saved from a few zoom levels out to street detail (zoom 14), for the day and night maps.")
        }
    }

    private var routingSection: some View {
        Section {
            if let id = packs.downloadingID {
                VStack(alignment: .leading, spacing: 6) {
                    Text(PackCatalog.region(id)?.name ?? id).font(.headline)
                    ProgressView(value: packs.progress)
                    Text(packs.status).font(.caption).foregroundStyle(.secondary)
                    Button("Cancel", role: .destructive) { packs.cancel() }
                }
            } else if !packs.status.isEmpty {
                Text(packs.status).font(.caption).foregroundStyle(.secondary)
            }
            ForEach(packs.installed) { pack in
                VStack(alignment: .leading) {
                    Text(pack.name).font(.headline)
                    Text(ByteCountFormatter.string(fromByteCount: pack.bytes, countStyle: .file)).font(.caption).foregroundStyle(.secondary)
                }
            }
            .onDelete { offsets in offsets.map { packs.installed[$0] }.forEach(packs.delete) }
            NavigationLink {
                PackPickerView(packs: packs)
            } label: {
                Label("Download a routing pack", systemImage: "plus.circle")
            }
            .disabled(packs.downloadingID != nil)
        } header: {
            Text("Offline routing")
        } footer: {
            Text("Routing packs are the same OpenStreetMap packs the Android app uses, planned on the phone by the Valhalla engine. Big countries can be downloaded a region at a time.")
        }
    }
}

/// The areas a routing pack can be downloaded for (Android `PackPicker`).
struct PackPickerView: View {
    @ObservedObject var packs: OfflinePackStore
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    private var matches: [PackRegion] {
        let q = query.trimmingCharacters(in: .whitespaces)
        return q.isEmpty ? PackCatalog.all : PackCatalog.all.filter { $0.name.localizedCaseInsensitiveContains(q) }
    }

    var body: some View {
        List {
            ForEach(PackCatalog.groups, id: \.self) { group in
                let regions = matches.filter { $0.group == group }
                if !regions.isEmpty {
                    Section(group) {
                        ForEach(regions) { region in
                            Button {
                                packs.download(region)
                                dismiss()
                            } label: {
                                HStack {
                                    Text(region.parent.isEmpty ? region.name : "\(region.name) (\(PackCatalog.region(region.parent)?.name ?? region.parent))")
                                    Spacer()
                                    if packs.installed.contains(where: { $0.id == region.id }) {
                                        Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        .searchable(text: $query)
        .navigationTitle("Choose an area")
    }
}
