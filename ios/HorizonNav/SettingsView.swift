import SwiftUI

/// Settings: provider keys (kept in the Keychain) and the choices that mirror the Android Settings pages.
struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var mapboxToken = KeyStore.keychainValue(for: .mapbox) ?? ""
    @State private var tomtomKey = KeyStore.keychainValue(for: .tomtom) ?? ""
    @AppStorage(AppSettings.Keys.routingProvider) private var routingProvider = "auto"
    @AppStorage(AppSettings.Keys.searchProvider) private var searchProvider = "auto"
    @AppStorage(AppSettings.Keys.mapSource) private var mapSource = "apple"
    @AppStorage(AppSettings.Keys.soundMode) private var soundMode = 0
    @AppStorage(AppSettings.Keys.cameraWarnings) private var cameraWarnings = true
    @AppStorage(AppSettings.Keys.carPlayAppearance) private var carPlayAppearance = "car"

    var body: some View {
        NavigationStack {
            Form {
                keysSection
                Section {
                    Picker("Routes from", selection: $routingProvider) {
                        Text("Automatic").tag("auto")
                        Text("TomTom").tag("tomtom")
                        Text("Mapbox").tag("mapbox")
                    }
                    Picker("Search with", selection: $searchProvider) {
                        Text("Automatic").tag("auto")
                        Text("TomTom").tag("tomtom")
                        Text("Mapbox").tag("mapbox")
                    }
                } header: {
                    Text("Routes and search")
                } footer: {
                    Text("With both keys on Automatic, new routes are asked of TomTom and Mapbox and the quicker one is offered first. Without a key, Apple Maps plans routes and searches.")
                }
                Section {
                    Picker("Voice", selection: $soundMode) {
                        Text("All directions and alerts").tag(0)
                        Text("Alerts only").tag(1)
                        Text("Off").tag(2)
                    }
                    Toggle("Speed camera warnings", isOn: $cameraWarnings)
                } header: {
                    Text("Sound")
                } footer: {
                    Text("Directions are spoken before each turn in the app's language. Music is turned down while a direction plays, and podcasts pause, on the phone and in CarPlay.")
                }
                Section {
                    Picker("Day and night", selection: $carPlayAppearance) {
                        Text("Follow the car").tag("car")
                        Text("Follow the phone").tag("phone")
                        Text("Always day").tag("day")
                        Text("Always night").tag("night")
                    }
                } header: {
                    Text("CarPlay")
                } footer: {
                    Text("Camera warnings use OpenStreetMap's cameras and stay off in France, Germany and Switzerland, where they are not allowed.")
                }
                Section {
                    Picker("Map", selection: $mapSource) {
                        Text("Apple Maps").tag("apple")
                        Text("OpenStreetMap").tag("openstreetmap")
                    }
                } header: {
                    Text("Map")
                } footer: {
                    Text("Apple Maps shows live traffic. The OpenStreetMap map draws areas saved in Offline maps, and is used automatically when there is no signal.")
                }
            }
            .navigationTitle("Settings")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { save(); dismiss() }
                }
            }
            .onDisappear(perform: save)
        }
    }

    private var keysSection: some View {
        Section {
            SecureField("Mapbox access token (pk.)", text: $mapboxToken)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            SecureField("TomTom API key", text: $tomtomKey)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
        } header: {
            Text("Keys")
        } footer: {
            Text(keyStatus)
        }
    }

    private var keyStatus: String {
        let keys = ProviderKeys.current
        let mapbox = keys.hasMapbox ? String(localized: "Mapbox: set") : String(localized: "Mapbox: not set")
        let tomtom = keys.hasTomTom ? String(localized: "TomTom: set") : String(localized: "TomTom: not set")
        return String(localized: "Keys are stored in this iPhone's Keychain and only sent to their own service. \(mapbox). \(tomtom).")
    }

    private func save() {
        KeyStore.save(mapboxToken, for: .mapbox)
        KeyStore.save(tomtomKey, for: .tomtom)
    }
}
