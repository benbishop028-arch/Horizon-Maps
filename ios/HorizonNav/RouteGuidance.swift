import CoreLocation

/// What the guidance engine concluded from one location fix.
struct GuidanceUpdate: Equatable {
    enum Status: Equatable {
        case onRoute
        case offRoute(strikes: Int)
        case rerouteNeeded
        case arrived
    }

    var status: Status
    /// Index into `RouteOption.steps` of the next manoeuvre, nil after the last one.
    var upcomingStepIndex: Int?
    var distanceToManeuver: CLLocationDistance
    var remainingDistance: CLLocationDistance
    var remainingTime: TimeInterval
    var distanceFromRoute: CLLocationDistance
    var progress: CLLocationDistance
}

/// Turn-by-turn progress and off-route detection for one route. Pure logic with no
/// framework callbacks, so the same engine drives the phone UI, CarPlay and the unit tests.
final class RouteGuidance {
    let route: RouteOption
    private(set) var trustedProgress: CLLocationDistance = 0
    private var strikes = 0
    private var segmentHint = 0
    private var lastTrustedTime: Date?
    private(set) var lastUpdate: GuidanceUpdate?

    /// A manoeuvre counts as done once the car is this far past its start.
    static let maneuverPassedTolerance: CLLocationDistance = 15
    static let arrivalRadius: CLLocationDistance = 30

    init(route: RouteOption) {
        self.route = route
    }

    func update(with location: CLLocation) -> GuidanceUpdate {
        let accuracy = location.horizontalAccuracy
        let speed = max(location.speed, 0)
        let windowed = GuidanceWindow.range(around: segmentHint, count: route.coordinates.count - 1)
        // Search near the last match first, like the Android RouteMatcher, then the whole route.
        var projection = RouteGeometry.project(location.coordinate, onto: route, segments: windowed)
        if projection == nil || projection!.distance > 60 {
            if let full = RouteGeometry.project(location.coordinate, onto: route),
               projection == nil || full.distance < projection!.distance {
                projection = full
            }
        }
        guard let match = projection else {
            return makeUpdate(status: .onRoute, progress: trustedProgress, distanceFromRoute: 0)
        }

        let limit = NavigationPolicy.offRouteLimit(accuracyMeters: accuracy)
        let reliable = NavigationPolicy.gpsReliableForReroute(accuracyMeters: accuracy)
        let elapsed = lastTrustedTime.map { location.timestamp.timeIntervalSince($0) } ?? 0
        let jump = lastTrustedTime != nil && NavigationPolicy.isProgressJump(
            candidateProgress: match.progress, trustedProgress: trustedProgress, speedMps: speed, elapsedSeconds: elapsed)

        var headingAway = false
        if location.course >= 0, match.segmentIndex + 1 < route.coordinates.count {
            let segmentBearing = RouteGeometry.bearing(route.coordinates[match.segmentIndex], route.coordinates[match.segmentIndex + 1])
            headingAway = NavigationPolicy.shouldCountHeadingAwayStrike(
                distanceMeters: match.distance, speedMps: speed,
                headingDifferenceDegrees: NavigationPolicy.angleDifference(from: segmentBearing, to: location.course))
        }

        let offRoute = reliable && (match.distance > limit || jump)
        if offRoute {
            strikes += NavigationPolicy.offRouteStrikeCount(progressJump: jump, distanceMeters: match.distance, limitMeters: limit)
        } else if reliable && headingAway {
            strikes += 1
        } else {
            strikes = 0
        }

        if !offRoute && !headingAway {
            trustedProgress = match.progress
            lastTrustedTime = location.timestamp
            segmentHint = match.segmentIndex
        }

        if route.length - trustedProgress <= Self.arrivalRadius && !offRoute {
            return makeUpdate(status: .arrived, progress: route.length, distanceFromRoute: match.distance)
        }
        if strikes >= NavigationPolicy.rerouteStrikeThreshold {
            strikes = 0
            return makeUpdate(status: .rerouteNeeded, progress: trustedProgress, distanceFromRoute: match.distance)
        }
        let status: GuidanceUpdate.Status = strikes > 0 ? .offRoute(strikes: strikes) : .onRoute
        return makeUpdate(status: status, progress: trustedProgress, distanceFromRoute: match.distance)
    }

    /// The first manoeuvre that has not been passed at the given progress.
    func upcomingStepIndex(at progress: CLLocationDistance) -> Int? {
        route.steps.firstIndex { $0.startProgress > progress + Self.maneuverPassedTolerance || $0.isArrival }
    }

    private func makeUpdate(status: GuidanceUpdate.Status, progress: CLLocationDistance, distanceFromRoute: CLLocationDistance) -> GuidanceUpdate {
        let upcoming = status == .arrived ? nil : upcomingStepIndex(at: progress)
        let toManeuver = upcoming.map { max(route.steps[$0].startProgress - progress, 0) } ?? 0
        let remaining = max(route.length - progress, 0)
        let fraction = route.length > 0 ? remaining / route.length : 0
        let update = GuidanceUpdate(
            status: status, upcomingStepIndex: upcoming, distanceToManeuver: toManeuver,
            remainingDistance: status == .arrived ? 0 : remaining,
            remainingTime: status == .arrived ? 0 : route.expectedTravelTime * fraction,
            distanceFromRoute: distanceFromRoute, progress: progress)
        lastUpdate = update
        return update
    }
}

enum GuidanceWindow {
    static func range(around hint: Int, count: Int) -> Range<Int>? {
        guard count > 0 else { return nil }
        let lower = max(hint - 20, 0)
        let upper = min(hint + 200, count)
        return lower < upper ? lower..<upper : nil
    }
}
