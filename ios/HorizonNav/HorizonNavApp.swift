import SwiftUI

@main
struct HorizonNavApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView(store: appDelegate.navigationStore)
        }
    }
}
