import CoreLocation
import MapLibre
import SwiftUI
import UIKit

/// OpenFreeMap's OpenStreetMap styles: the same tile source as the Android app's free map, drawn with MapLibre Native so
/// downloaded areas work with no network.
enum OpenMapStyle {
    static let day = URL(string: "https://tiles.openfreemap.org/styles/liberty")!
    static let night = URL(string: "https://tiles.openfreemap.org/styles/dark")!

    static func url(dark: Bool) -> URL { dark ? night : day }
}

/// A MapLibre map with the route lines, used on the phone and in CarPlay.
final class OpenMapView: UIView, MLNMapViewDelegate {
    let mapView: MLNMapView
    private var lines: [MLNPolyline] = []
    private(set) var dark: Bool
    var onRegionChange: ((MLNCoordinateBounds, Double) -> Void)?

    init(frame: CGRect = .zero, dark: Bool) {
        self.dark = dark
        mapView = MLNMapView(frame: frame, styleURL: OpenMapStyle.url(dark: dark))
        super.init(frame: frame)
        mapView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        mapView.frame = bounds
        mapView.delegate = self
        mapView.showsUserLocation = true
        mapView.compassView.isHidden = true
        mapView.setCenter(NavigationStore.fallbackCoordinate, zoomLevel: 12, animated: false)
        addSubview(mapView)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    func setDark(_ dark: Bool) {
        guard dark != self.dark else { return }
        self.dark = dark
        mapView.styleURL = OpenMapStyle.url(dark: dark)
    }

    func show(routes: [RouteOption], selected: Int) {
        if !lines.isEmpty { mapView.removeAnnotations(lines) }
        // Unselected alternatives first, so the selected route draws on top.
        let ordered = routes.enumerated().sorted { a, _ in a.offset != selected }
        lines = ordered.compactMap { index, route in
            guard route.coordinates.count > 1 else { return nil }
            let line = MLNPolyline(coordinates: route.coordinates, count: UInt(route.coordinates.count))
            line.title = index == selected ? "selected" : "alternative"
            return line
        }
        mapView.addAnnotations(lines)
    }

    func fitRoutes(padding: UIEdgeInsets) {
        let points = lines.flatMap { line -> [CLLocationCoordinate2D] in
            var coords = [CLLocationCoordinate2D](repeating: kCLLocationCoordinate2DInvalid, count: Int(line.pointCount))
            line.getCoordinates(&coords, range: NSRange(location: 0, length: Int(line.pointCount)))
            return coords
        }
        guard !points.isEmpty else { return }
        var coords = points
        mapView.setVisibleCoordinates(&coords, count: UInt(coords.count), edgePadding: padding, animated: true)
    }

    func follow(withHeading: Bool) {
        mapView.setUserTrackingMode(withHeading ? .followWithCourse : .follow, animated: true, completionHandler: nil)
    }

    func zoom(by delta: Double) { mapView.setZoomLevel(mapView.zoomLevel + delta, animated: true) }

    func pan(_ direction: CGVector) {
        let point = CGPoint(x: mapView.bounds.midX + direction.dx * mapView.bounds.width / 3,
                            y: mapView.bounds.midY + direction.dy * mapView.bounds.height / 3)
        mapView.setCenter(mapView.convert(point, toCoordinateFrom: mapView), animated: true)
    }

    func mapView(_ mapView: MLNMapView, strokeColorForShapeAnnotation annotation: MLNShape) -> UIColor {
        annotation.title == "selected" ? .systemCyan : UIColor.systemGray.withAlphaComponent(0.7)
    }

    func mapView(_ mapView: MLNMapView, lineWidthForPolylineAnnotation annotation: MLNPolyline) -> CGFloat {
        annotation.title == "selected" ? 8 : 6
    }

    func mapView(_ mapView: MLNMapView, regionDidChangeAnimated animated: Bool) {
        onRegionChange?(mapView.visibleCoordinateBounds, mapView.zoomLevel)
    }
}

/// The OpenStreetMap map for SwiftUI.
struct OpenMap: UIViewRepresentable {
    var routes: [RouteOption] = []
    var selected = 0
    var following = false
    var onRegionChange: ((MLNCoordinateBounds, Double) -> Void)?
    @Environment(\.colorScheme) private var colorScheme

    func makeUIView(context: Context) -> OpenMapView {
        let view = OpenMapView(dark: colorScheme == .dark)
        view.onRegionChange = onRegionChange
        view.follow(withHeading: false)
        return view
    }

    func updateUIView(_ view: OpenMapView, context: Context) {
        view.setDark(colorScheme == .dark)
        view.onRegionChange = onRegionChange
        let ids = routes.map(\.id)
        if context.coordinator.shownIDs != ids || context.coordinator.shownSelected != selected {
            context.coordinator.shownIDs = ids
            context.coordinator.shownSelected = selected
            view.show(routes: routes, selected: selected)
            if !routes.isEmpty && !following { view.fitRoutes(padding: UIEdgeInsets(top: 60, left: 40, bottom: 220, right: 40)) }
        }
        if following != context.coordinator.following {
            context.coordinator.following = following
            if following { view.follow(withHeading: true) }
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator {
        var shownIDs: [UUID] = []
        var shownSelected = 0
        var following = false
    }
}
