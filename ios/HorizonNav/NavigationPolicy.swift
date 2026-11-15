import Foundation

/// Pure navigation decisions, ported from the Android `NavigationPolicy` and `NavEngine`
/// so both apps reroute on the same evidence.
enum NavigationPolicy {
    /// Strikes needed before a reroute starts (Android `NavEngine`: `offCount >= 3`).
    static let rerouteStrikeThreshold = 3

    /// Unknown accuracy is allowed for compatibility; known poor fixes never trigger a reroute.
    static func gpsReliableForReroute(accuracyMeters: Double) -> Bool {
        !accuracyMeters.isFinite || accuracyMeters <= 0 || accuracyMeters <= 45
    }

    /// Distance from the route tolerated before a credible fix counts as off-route.
    static func offRouteLimit(accuracyMeters: Double) -> Double {
        let usable = accuracyMeters.isFinite && accuracyMeters > 0 ? accuracyMeters : 8
        return max(30, usable * 1.5 + 12)
    }

    /// A large departure or an impossible progress jump counts as two strikes.
    static func offRouteStrikeCount(progressJump: Bool, distanceMeters: Double, limitMeters: Double) -> Int {
        progressJump || distanceMeters > limitMeters * 2 ? 2 : 1
    }

    /// A fix close to the route can still mean a wrong turn when the car moves away from the route heading.
    static func shouldCountHeadingAwayStrike(distanceMeters: Double, speedMps: Double, headingDifferenceDegrees: Double) -> Bool {
        distanceMeters > 15 && speedMps > 4 && headingDifferenceDegrees.isFinite && abs(headingDifferenceDegrees) > 70
    }

    /// Rejects forward snaps that would skip more route progress than the car could have driven.
    static func isProgressJump(candidateProgress: Double, trustedProgress: Double, speedMps: Double, elapsedSeconds: Double) -> Bool {
        guard candidateProgress.isFinite, trustedProgress.isFinite, speedMps.isFinite, elapsedSeconds.isFinite,
              trustedProgress >= 0 else { return false }
        let allowedAdvance = max(400, speedMps * 1.6 * max(elapsedSeconds, 0) + 250)
        return candidateProgress > trustedProgress + allowedAdvance
    }

    /// Signed difference between two bearings, in -180...180 degrees. Positive means clockwise (to the right).
    static func angleDifference(from a: Double, to b: Double) -> Double {
        var d = (b - a).truncatingRemainder(dividingBy: 360)
        if d > 180 { d -= 360 }
        if d < -180 { d += 360 }
        return d
    }
}

/// Decides whether an online routing failure looks like a connection problem that merits the
/// offline provider. Ported from the Android `NavigationFallbackPolicy`: authentication and
/// "no route" failures are never treated as connectivity.
enum RoutingFallbackPolicy {
    private static let connectivityCodes: Set<Int> = [
        NSURLErrorNotConnectedToInternet, NSURLErrorTimedOut, NSURLErrorNetworkConnectionLost,
        NSURLErrorCannotFindHost, NSURLErrorCannotConnectToHost, NSURLErrorDNSLookupFailed,
        NSURLErrorInternationalRoamingOff, NSURLErrorDataNotAllowed,
    ]
    private static let connectivityPhrases = [
        "no signal", "no network", "network unavailable", "network error", "connection",
        "timed out", "timeout", "unable to resolve host", "failed to connect", "offline",
    ]

    static func shouldUseOffline(_ error: Error?) -> Bool {
        var current = error.map { $0 as NSError }
        var depth = 0
        while let e = current, depth < 8 {
            if e.domain == NSURLErrorDomain && connectivityCodes.contains(e.code) { return true }
            let message = e.localizedDescription.lowercased()
            if connectivityPhrases.contains(where: message.contains) { return true }
            current = e.userInfo[NSUnderlyingErrorKey] as? NSError
            depth += 1
        }
        return false
    }
}
