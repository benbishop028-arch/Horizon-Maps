import Combine
import MapKit
import UIKit

/// The map a CarPlay navigation app draws in its own `CPWindow`, under the `CPMapTemplate` controls.
/// Shows live traffic from Apple Maps, every route option during preview, and follows the car while guiding.
final class CarPlayMapViewController: UIViewController, MKMapViewDelegate {
    private let store: NavigationStore
    private let mapView = MKMapView()
    private var cancellables: Set<AnyCancellable> = []

    init(store: NavigationStore) {
        self.store = store
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func viewDidLoad() {
        super.viewDidLoad()
        mapView.frame = view.bounds
        mapView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        mapView.delegate = self
        mapView.showsUserLocation = true
        mapView.showsTraffic = true
        mapView.pointOfInterestFilter = .excludingAll
        mapView.userTrackingMode = .follow
        view.addSubview(mapView)

        store.$routeOptions.combineLatest(store.$selectedRouteIndex, store.$phase)
            .receive(on: RunLoop.main)
            .sink { [weak self] options, selected, phase in self?.render(options: options, selected: selected, phase: phase) }
            .store(in: &cancellables)
    }

    func recenter() {
        mapView.setUserTrackingMode(store.phase == .guiding ? .followWithHeading : .follow, animated: true)
    }

    func zoom(by factor: Double) {
        var region = mapView.region
        region.span.latitudeDelta = min(max(region.span.latitudeDelta * factor, 0.002), 90)
        region.span.longitudeDelta = min(max(region.span.longitudeDelta * factor, 0.002), 90)
        mapView.setRegion(region, animated: true)
    }

    func pan(_ direction: CGVector) {
        let center = mapView.convert(CGPoint(x: mapView.bounds.midX + direction.dx * mapView.bounds.width / 3,
                                             y: mapView.bounds.midY + direction.dy * mapView.bounds.height / 3),
                                     toCoordinateFrom: mapView)
        mapView.setCenter(center, animated: true)
    }

    private func render(options: [RouteOption], selected: Int, phase: NavigationStore.Phase) {
        mapView.removeOverlays(mapView.overlays)
        // Unselected alternatives first, so the selected route draws on top.
        let ordered = options.enumerated().sorted { a, _ in a.offset != selected }
        for (index, option) in ordered where option.coordinates.count > 1 {
            let line = MKPolyline(coordinates: option.coordinates, count: option.coordinates.count)
            line.title = index == selected ? "selected" : "alternative"
            mapView.addOverlay(line, level: .aboveRoads)
        }
        switch phase {
        case .previewing:
            let rects = mapView.overlays.map(\.boundingMapRect)
            if let first = rects.first {
                let union = rects.dropFirst().reduce(first) { $0.union($1) }
                mapView.setVisibleMapRect(union, edgePadding: UIEdgeInsets(top: 40, left: 40, bottom: 40, right: 260), animated: true)
            }
        case .guiding, .rerouting:
            mapView.setUserTrackingMode(.followWithHeading, animated: true)
        default:
            break
        }
    }

    func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
        guard let line = overlay as? MKPolyline else { return MKOverlayRenderer(overlay: overlay) }
        let renderer = MKPolylineRenderer(polyline: line)
        let selected = line.title == "selected"
        renderer.strokeColor = selected ? .systemCyan : UIColor.systemGray.withAlphaComponent(0.7)
        renderer.lineWidth = selected ? 8 : 6
        renderer.lineCap = .round
        renderer.lineJoin = .round
        return renderer
    }
}
