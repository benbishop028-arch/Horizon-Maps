import CarPlay
import Combine
import MapKit
import os

/// CarPlay navigation scene: search, route preview with alternatives, and a `CPNavigationSession`
/// fed with manoeuvres, travel estimates and reroute pauses from the shared `NavigationStore`.
final class CarPlaySceneDelegate: NSObject, CPTemplateApplicationSceneDelegate {
    private let store = NavigationStore.shared
    private let log = Logger(subsystem: "com.horizonnav.ios", category: "carplay")
    private var interfaceController: CPInterfaceController?
    private var carWindow: CPWindow?
    private var mapViewController: CarPlayMapViewController?
    private var mapTemplate: CPMapTemplate?
    private var navigationSession: CPNavigationSession?
    private var activeTrip: CPTrip?
    private var shownStepIndex: Int?
    private var shownRouteID: UUID?
    private var shownLanes: LaneSection?
    private var shownJunction = false
    private var scene: CPTemplateApplicationScene?
    private var searchResults: [MKMapItem] = []
    private var cancellables: Set<AnyCancellable> = []

    // MARK: Scene lifecycle

    func templateApplicationScene(_ templateApplicationScene: CPTemplateApplicationScene,
                                  didConnect interfaceController: CPInterfaceController, to window: CPWindow) {
        self.interfaceController = interfaceController
        scene = templateApplicationScene
        carWindow = window
        let mapViewController = CarPlayMapViewController(store: store)
        window.rootViewController = mapViewController
        self.mapViewController = mapViewController

        let template = CPMapTemplate()
        template.mapDelegate = self
        template.automaticallyHidesNavigationBar = false
        template.guidanceBackgroundColor = .systemBackground
        template.tripEstimateStyle = .dark
        mapTemplate = template
        updateBarButtons()
        applyAppearance()
        interfaceController.setRootTemplate(template, animated: false, completion: nil)
        log.info("CarPlay scene connected, map template is root")

        store.$guidance
            .receive(on: RunLoop.main)
            .sink { [weak self] update in self?.apply(update) }
            .store(in: &cancellables)
        store.events
            .receive(on: RunLoop.main)
            .sink { [weak self] event in self?.handle(event) }
            .store(in: &cancellables)
        NotificationCenter.default.publisher(for: UserDefaults.didChangeNotification)
            .merge(with: NotificationCenter.default.publisher(for: .phoneAppearanceChanged))
            .receive(on: RunLoop.main)
            .sink { [weak self] _ in self?.applyAppearance() }
            .store(in: &cancellables)

        // A trip already running on the phone continues on the car screen.
        if store.phase == .guiding, let route = store.selectedRoute { beginSession(for: makeTrip(), route: route) }
        runAutomationIfRequested()
    }

    func templateApplicationScene(_ templateApplicationScene: CPTemplateApplicationScene,
                                  didDisconnect interfaceController: CPInterfaceController, from window: CPWindow) {
        cancellables.removeAll()
        navigationSession?.cancelTrip()
        navigationSession = nil
        activeTrip = nil
        mapTemplate = nil
        mapViewController = nil
        scene = nil
        carWindow = nil
        self.interfaceController = nil
        log.info("CarPlay scene disconnected")
    }

    /// The car switching between its day and night look.
    func contentStyleDidChange(_ contentStyle: UIUserInterfaceStyle) {
        applyAppearance()
    }

    /// Day or night map and cards: the car's own look, or the phone's, or always day or night (Settings).
    private func applyAppearance() {
        let carDark = (scene?.contentStyle ?? carWindow?.traitCollection.userInterfaceStyle) == .dark
        let phoneDark = UIScreen.main.traitCollection.userInterfaceStyle == .dark
        let dark = CarPlayManeuvers.isDark(setting: AppSettings.carPlayAppearance, carDark: carDark, phoneDark: phoneDark)
        mapViewController?.overrideUserInterfaceStyle = dark ? .dark : .light
        mapTemplate?.tripEstimateStyle = dark ? .dark : .light
        mapTemplate?.guidanceBackgroundColor = dark ? UIColor(white: 0.11, alpha: 1) : .white
    }

    // MARK: Templates

    private func updateBarButtons() {
        guard let mapTemplate else { return }
        let navigating = navigationSession != nil
        if navigating {
            let end = CPBarButton(title: String(localized: "End")) { [weak self] _ in self?.store.stopNavigating() }
            mapTemplate.leadingNavigationBarButtons = [end]
        } else {
            let search = CPBarButton(image: UIImage(systemName: "magnifyingglass")!) { [weak self] _ in self?.showSearch() }
            mapTemplate.leadingNavigationBarButtons = [search]
        }
        let recenter = CPMapButton { [weak self] _ in self?.mapViewController?.recenter() }
        recenter.image = UIImage(systemName: "location.fill")
        let zoomIn = CPMapButton { [weak self] _ in self?.mapViewController?.zoom(by: 0.5) }
        zoomIn.image = UIImage(systemName: "plus.magnifyingglass")
        let zoomOut = CPMapButton { [weak self] _ in self?.mapViewController?.zoom(by: 2) }
        zoomOut.image = UIImage(systemName: "minus.magnifyingglass")
        let mute = CPMapButton { [weak self] _ in
            VoiceGuidance.shared.toggleMute()
            self?.updateBarButtons()
        }
        mute.image = UIImage(systemName: VoiceGuidance.shared.isMuted ? "speaker.slash.fill" : "speaker.wave.2.fill")
        mapTemplate.mapButtons = [recenter, zoomIn, zoomOut, mute]
    }

    private func showSearch() {
        let searchTemplate = CPSearchTemplate()
        searchTemplate.delegate = self
        interfaceController?.pushTemplate(searchTemplate, animated: true, completion: nil)
    }

    /// Plans routes and shows them as a trip preview, one route choice per alternative.
    private func preview(destination: MKMapItem) {
        Task { @MainActor in
            await store.navigate(to: destination)
            guard store.phase == .previewing, let mapTemplate else {
                log.error("No route to preview")
                return
            }
            let trip = makeTrip()
            let text = CPTripPreviewTextConfiguration(startButtonTitle: String(localized: "Go"),
                                                     additionalRoutesButtonTitle: String(localized: "Routes"),
                                                     overviewButtonTitle: String(localized: "Overview"))
            mapTemplate.showTripPreviews([trip], textConfiguration: text)
            log.info("Trip preview shown with \(trip.routeChoices.count, privacy: .public) route choice(s)")
        }
    }

    private func makeTrip() -> CPTrip {
        let origin = MKMapItem(placemark: MKPlacemark(coordinate: store.currentCoordinate))
        origin.name = String(localized: "Current Location")
        let destination = store.destination ?? MKMapItem(placemark: MKPlacemark(coordinate: store.selectedRoute?.coordinates.last ?? store.currentCoordinate))
        let choices = store.routeOptions.enumerated().map { index, option -> CPRouteChoice in
            let time = Self.format(duration: option.expectedTravelTime)
            let distance = Self.format(distance: option.distance)
            let summary = option.name.isEmpty ? distance : "\(option.name), \(distance)"
            let choice = CPRouteChoice(summaryVariants: [summary, distance],
                                       additionalInformationVariants: option.advisories.isEmpty ? [time] : [time] + option.advisories,
                                       selectionSummaryVariants: [time])
            choice.userInfo = index
            return choice
        }
        return CPTrip(origin: origin, destination: destination, routeChoices: choices)
    }

    private func beginSession(for trip: CPTrip, route: RouteOption) {
        guard let mapTemplate, navigationSession == nil else { return }
        mapTemplate.hideTripPreviews()
        activeTrip = trip
        navigationSession = mapTemplate.startNavigationSession(for: trip)
        shownStepIndex = nil
        shownRouteID = nil
        updateBarButtons()
        log.info("CPNavigationSession started for route with \(route.steps.count, privacy: .public) manoeuvres")
        apply(store.guidance)
    }

    private func endSession(arrived: Bool) {
        guard let session = navigationSession else { return }
        if arrived { session.finishTrip() } else { session.cancelTrip() }
        navigationSession = nil
        activeTrip = nil
        shownStepIndex = nil
        shownRouteID = nil
        shownLanes = nil
        updateBarButtons()
        log.info("CPNavigationSession \(arrived ? "finished" : "cancelled", privacy: .public)")
    }

    // MARK: Guidance

    private func handle(_ event: NavigationStore.TripEvent) {
        switch event {
        case .guidanceStarted(let route):
            if navigationSession == nil { beginSession(for: activeTrip ?? makeTrip(), route: route) }
        case .rerouteStarted:
            navigationSession?.pauseTrip(for: .rerouting, description: String(localized: "Rerouting"))
            log.info("CPNavigationSession paused for rerouting")
        case .rerouteFinished:
            shownStepIndex = nil
            apply(store.guidance)
            log.info("CPNavigationSession resumed on the new route")
        case .rerouteFailed:
            shownStepIndex = nil
            apply(store.guidance)
        case .arrived:
            endSession(arrived: true)
        case .ended:
            endSession(arrived: false)
        case .alert(let alert):
            present(alert)
        }
    }

    /// A warning as a CarPlay navigation alert (Android raises its own alert card on the car screen).
    private func present(_ alert: NavigationStore.NavigationAlert) {
        guard let mapTemplate else { return }
        let ok = CPAlertAction(title: String(localized: "OK"), style: .cancel) { [weak self] _ in
            self?.mapTemplate?.dismissNavigationAlert(animated: true) { _ in }
        }
        let navigationAlert = CPNavigationAlert(titleVariants: [alert.title], subtitleVariants: [alert.detail],
                                                image: UIImage(systemName: alert.symbol), primaryAction: ok, secondaryAction: nil, duration: 8)
        mapTemplate.dismissNavigationAlert(animated: false) { _ in
            mapTemplate.present(navigationAlert: navigationAlert, animated: true)
        }
        log.info("Navigation alert: \(alert.title, privacy: .public)")
    }

    /// Pushes the upcoming manoeuvres, lanes and live estimates into the session.
    private func apply(_ update: GuidanceUpdate?) {
        guard let session = navigationSession, let update, let route = store.selectedRoute,
              let index = update.upcomingStepIndex, route.steps.indices.contains(index) else { return }
        let step = route.steps[index]
        let lanes = route.laneSection(forStep: index, progress: update.progress,
                                      fastRoad: NavigationStore.isFastRoad(step: step, route: route, progress: update.progress))
        let junction = JunctionImages.shared.image(for: step.junctionImageURL)
        let isNew = index != shownStepIndex || route.id != shownRouteID
        if isNew || lanes != shownLanes || (junction != nil) != shownJunction {
            var maneuvers = [CarPlayManeuvers.maneuver(for: step, lanes: lanes, junctionImage: junction)]
            // The lanes as a row under the turn when there are lanes to pick, otherwise the turn after this one.
            if let lanes, let row = CarPlayManeuvers.laneRow(for: step, lanes: lanes) {
                maneuvers.append(row)
            } else if route.steps.indices.contains(index + 1) {
                maneuvers.append(CarPlayManeuvers.maneuver(for: route.steps[index + 1], lanes: nil, junctionImage: nil))
            }
            session.upcomingManeuvers = maneuvers
            if #available(iOS 17.4, *) {
                session.currentLaneGuidance = (maneuvers.first?.userInfo as? CarPlayManeuvers.Info)?.laneGuidance as? CPLaneGuidance
                if isNew { session.maneuverState = .initial }
            }
            shownStepIndex = index
            shownRouteID = route.id
            shownLanes = lanes
            shownJunction = junction != nil
            JunctionImages.shared.load(step.junctionImageURL) { [weak self] in self?.apply(self?.store.guidance) }
            if isNew { log.info("Manoeuvre \(index, privacy: .public) of \(route.steps.count, privacy: .public): \(step.instruction, privacy: .public)") }
        }
        if #available(iOS 17.4, *) {
            if !isNew { session.maneuverState = update.distanceToManeuver <= 120 ? .execute : update.distanceToManeuver <= 520 ? .prepare : .continue }
            session.currentRoadNameVariants = store.currentRoad.isEmpty ? [] : [store.currentRoad]
        }
        if let current = session.upcomingManeuvers.first {
            let timeToManeuver = route.distance > 0 ? update.remainingTime * update.distanceToManeuver / max(update.remainingDistance, 1) : 0
            session.updateEstimates(CPTravelEstimates(distanceRemaining: Self.measurement(update.distanceToManeuver), timeRemaining: timeToManeuver), for: current)
        }
        if let trip = activeTrip {
            mapTemplate?.update(CPTravelEstimates(distanceRemaining: Self.measurement(update.remainingDistance), timeRemaining: update.remainingTime), for: trip, with: .default)
        }
    }

    private static func measurement(_ meters: CLLocationDistance) -> Measurement<UnitLength> {
        Measurement(value: meters, unit: UnitLength.meters)
    }

    private static func format(distance: CLLocationDistance) -> String {
        measurement(distance).formatted(.measurement(width: .abbreviated, usage: .road))
    }

    private static func format(duration: TimeInterval) -> String {
        Duration.seconds(duration).formatted(.units(allowed: [.hours, .minutes], width: .abbreviated))
    }
}

// MARK: - CPMapTemplateDelegate

extension CarPlaySceneDelegate: CPMapTemplateDelegate {
    func mapTemplate(_ mapTemplate: CPMapTemplate, selectedPreviewFor trip: CPTrip, using routeChoice: CPRouteChoice) {
        guard let index = routeChoice.userInfo as? Int else { return }
        store.selectRoute(index)
    }

    func mapTemplate(_ mapTemplate: CPMapTemplate, startedTrip trip: CPTrip, using routeChoice: CPRouteChoice) {
        if let index = routeChoice.userInfo as? Int { store.selectRoute(index) }
        activeTrip = trip
        guard let route = store.selectedRoute else { return }
        beginSession(for: trip, route: route)
        store.startGuidance()
    }

    func mapTemplateDidCancelNavigation(_ mapTemplate: CPMapTemplate) {
        store.stopNavigating()
    }

    func mapTemplate(_ mapTemplate: CPMapTemplate, displayStyleFor maneuver: CPManeuver) -> CPManeuverDisplayStyle {
        maneuver.userInfo as? String == CarPlayManeuvers.laneRowMarker ? .symbolOnly : .leadingSymbol
    }

    func mapTemplate(_ mapTemplate: CPMapTemplate, shouldShowNotificationFor navigationAlert: CPNavigationAlert) -> Bool { true }

    func mapTemplateDidBeginPanGesture(_ mapTemplate: CPMapTemplate) { }

    func mapTemplate(_ mapTemplate: CPMapTemplate, didUpdatePanGestureWithTranslation translation: CGPoint, velocity: CGPoint) { }

    func mapTemplate(_ mapTemplate: CPMapTemplate, panWith direction: CPMapTemplate.PanDirection) {
        var vector = CGVector.zero
        if direction.contains(.left) { vector.dx = -1 }
        if direction.contains(.right) { vector.dx = 1 }
        if direction.contains(.up) { vector.dy = -1 }
        if direction.contains(.down) { vector.dy = 1 }
        mapViewController?.pan(vector)
    }
}

// MARK: - CPSearchTemplateDelegate

extension CarPlaySceneDelegate: CPSearchTemplateDelegate {
    func searchTemplate(_ searchTemplate: CPSearchTemplate, updatedSearchText searchText: String, completionHandler: @escaping ([CPListItem]) -> Void) {
        Task { @MainActor in
            let items = await store.search(searchText)
            searchResults = items
            log.info("CarPlay search returned \(items.count, privacy: .public) result(s)")
            completionHandler(items.enumerated().map { index, item in
                let row = CPListItem(text: item.name ?? String(localized: "Unnamed place"), detailText: item.placemark.title)
                row.userInfo = index
                return row
            })
        }
    }

    func searchTemplate(_ searchTemplate: CPSearchTemplate, selectedResult item: CPListItem, completionHandler: @escaping () -> Void) {
        if let index = item.userInfo as? Int, searchResults.indices.contains(index) {
            interfaceController?.popToRootTemplate(animated: true, completion: nil)
            preview(destination: searchResults[index])
        }
        completionHandler()
    }

    func searchTemplateSearchButtonPressed(_ searchTemplate: CPSearchTemplate) { }
}

// MARK: - Simulator automation

extension CarPlaySceneDelegate {
    /// Debug builds only: `-HNCarPlayAutoTest "<query>"` drives search, preview, alternative selection
    /// and trip start through the same methods the CarPlay templates call, for repeatable runtime checks
    /// in the CarPlay simulator where template taps cannot be scripted.
    private func runAutomationIfRequested() {
        #if DEBUG
        guard let query = UserDefaults.standard.string(forKey: "HNCarPlayAutoTest"), !query.isEmpty else { return }
        log.info("Automation: searching for \(query, privacy: .public)")
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(3))
            let results = await store.search(query)
            searchResults = results
            log.info("Automation: search returned \(results.count, privacy: .public) result(s)")
            guard let first = results.first else { return }
            preview(destination: first)
            for _ in 0..<40 where store.phase != .previewing { try? await Task.sleep(for: .milliseconds(250)) }
            guard store.phase == .previewing, let mapTemplate else { return }
            let trip = makeTrip()
            let choice = trip.routeChoices.count > 1 ? trip.routeChoices[1] : trip.routeChoices[0]
            self.mapTemplate(mapTemplate, selectedPreviewFor: trip, using: choice)
            log.info("Automation: selected route choice \(String(describing: choice.userInfo), privacy: .public) of \(trip.routeChoices.count, privacy: .public)")
            try? await Task.sleep(for: .seconds(3))
            self.mapTemplate(mapTemplate, startedTrip: trip, using: choice)
            log.info("Automation: navigation session active = \(self.navigationSession != nil, privacy: .public)")
        }
        #endif
    }
}
