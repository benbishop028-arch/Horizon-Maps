import Foundation
import Security

/// Provider keys. A key typed into Settings is kept in the Keychain and wins; otherwise the build's
/// `HN_MAPBOX_ACCESS_TOKEN` / `HN_TOMTOM_API_KEY` from the untracked `Config/Secrets.xcconfig`, passed through
/// Info.plist, is used. No key is ever stored in the repository or in exported settings.
struct ProviderKeys: Equatable {
    var mapbox: String?
    var tomtom: String?

    var hasMapbox: Bool { mapbox != nil }
    var hasTomTom: Bool { tomtom != nil }

    static var current: ProviderKeys {
        ProviderKeys(mapbox: KeyStore.value(for: .mapbox), tomtom: KeyStore.value(for: .tomtom))
    }
}

enum KeyStore {
    enum Provider: String, CaseIterable {
        case mapbox
        case tomtom

        var infoPlistKey: String { self == .mapbox ? "HNMapboxAccessToken" : "HNTomTomAPIKey" }
    }

    private static let service = "com.horizonnav.ios.provider-keys"

    /// The key in use: the Keychain's, else the build's, with stray spaces removed (pasted keys often carry them).
    static func value(for provider: Provider) -> String? {
        if let saved = keychainValue(for: provider), !saved.isEmpty { return saved }
        let built = (Bundle.main.object(forInfoDictionaryKey: provider.infoPlistKey) as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return built.isEmpty || built.hasPrefix("$(") ? nil : built
    }

    static func keychainValue(for provider: Provider) -> String? {
        var query = baseQuery(provider)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    /// Saves a key to the Keychain, or removes it when blank.
    @discardableResult
    static func save(_ value: String, for provider: Provider) -> Bool {
        let cleaned = value.components(separatedBy: .whitespacesAndNewlines).joined()
        SecItemDelete(baseQuery(provider) as CFDictionary)
        guard !cleaned.isEmpty else { return true }
        var item = baseQuery(provider)
        item[kSecValueData as String] = Data(cleaned.utf8)
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        return SecItemAdd(item as CFDictionary, nil) == errSecSuccess
    }

    private static func baseQuery(_ provider: Provider) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: provider.rawValue]
    }
}

/// User settings shared by the phone and CarPlay. Names are new to iOS; the choices mirror the Android preferences
/// they port (`DATAPROV`, `SEARCHWITH`, `SOUND_MODE`, `CAMERA_WARNINGS`, `CARTHEME`).
enum AppSettings {
    enum Keys {
        static let routingProvider = "routingProvider"
        static let searchProvider = "searchProvider"
        static let soundMode = "soundMode"
        static let cameraWarnings = "cameraWarnings"
        static let carPlayAppearance = "carPlayAppearance"
        static let mapSource = "mapSource"
    }

    static var defaults: UserDefaults { .standard }

    /// "auto", "tomtom" or "mapbox".
    static var routingProvider: String { defaults.string(forKey: Keys.routingProvider) ?? "auto" }
    static var searchProvider: String { defaults.string(forKey: Keys.searchProvider) ?? "auto" }
    /// 0 every direction and alert, 1 alerts only, 2 silent (Android `soundMode`).
    static var soundMode: Int { min(max(defaults.integer(forKey: Keys.soundMode), 0), 2) }
    static var cameraWarnings: Bool { defaults.object(forKey: Keys.cameraWarnings) as? Bool ?? true }
    /// "car", "phone", "day" or "night".
    static var carPlayAppearance: String { defaults.string(forKey: Keys.carPlayAppearance) ?? "car" }
    /// "apple" or "openstreetmap".
    static var mapSource: String { defaults.string(forKey: Keys.mapSource) ?? "apple" }
}
