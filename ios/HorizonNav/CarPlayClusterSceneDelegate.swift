import CarPlay
import UIKit

/// The map on the car's instrument cluster (the display behind the steering wheel), for cars whose CarPlay offers it: the
/// counterpart of the Android `ClusterSession`. It follows the car along the route; the car shows the turn cards itself
/// from the `CPNavigationSession` manoeuvres.
final class CarPlayClusterSceneDelegate: UIResponder, CPTemplateApplicationInstrumentClusterSceneDelegate, CPInstrumentClusterControllerDelegate {
    private var controller: CPInstrumentClusterController?
    private var mapViewController: CarPlayMapViewController?

    func templateApplicationInstrumentClusterScene(_ templateApplicationInstrumentClusterScene: CPTemplateApplicationInstrumentClusterScene,
                                                   didConnect instrumentClusterController: CPInstrumentClusterController) {
        controller = instrumentClusterController
        instrumentClusterController.delegate = self
        instrumentClusterController.inactiveDescriptionVariants = [String(localized: "Horizon Nav")]
    }

    func templateApplicationInstrumentClusterScene(_ templateApplicationInstrumentClusterScene: CPTemplateApplicationInstrumentClusterScene,
                                                   didDisconnectInstrumentClusterController instrumentClusterController: CPInstrumentClusterController) {
        controller = nil
        mapViewController = nil
    }

    func contentStyleDidChange(_ contentStyle: UIUserInterfaceStyle) {
        mapViewController?.overrideUserInterfaceStyle = contentStyle
    }

    func instrumentClusterControllerDidConnect(_ instrumentClusterWindow: UIWindow) {
        let map = CarPlayMapViewController(store: NavigationStore.shared, compact: true)
        map.showsSpeedSign = controller?.speedLimitSetting != .disabled
        instrumentClusterWindow.rootViewController = map
        mapViewController = map
    }

    func instrumentClusterControllerDidDisconnectWindow(_ instrumentClusterWindow: UIWindow) {
        mapViewController = nil
    }

    func instrumentClusterControllerDidZoom(in instrumentClusterController: CPInstrumentClusterController) {
        mapViewController?.zoom(by: 0.5)
    }

    func instrumentClusterControllerDidZoomOut(_ instrumentClusterController: CPInstrumentClusterController) {
        mapViewController?.zoom(by: 2)
    }

    func instrumentClusterController(_ instrumentClusterController: CPInstrumentClusterController, didChangeSpeedLimitSetting speedLimitSetting: CPInstrumentClusterSetting) {
        mapViewController?.showsSpeedSign = speedLimitSetting != .disabled
    }
}
