import CarPlay
import UIKit

/// Builds CarPlay manoeuvres from route steps, as the Android `CarNav.step` builds Android Auto's: the turn kind, the
/// instruction from long to short, the lanes as a picture and (iOS 17.4 and later) as cluster lane guidance, the junction
/// view, and the manoeuvre metadata the instrument cluster and head-up display use.
enum CarPlayManeuvers {
    /// `userInfo` of the lane row manoeuvre, which the map template shows as a symbol only.
    static let laneRowMarker = "lanes"

    /// `userInfo` of a turn manoeuvre. It also keeps the lane guidance alive: `CPManeuver.linkedLaneGuidance` is declared
    /// `assign`, so CarPlay does not retain it and reading it back after release would crash.
    final class Info {
        let startProgress: Double
        let laneGuidance: AnyObject?
        init(startProgress: Double, laneGuidance: AnyObject?) {
            self.startProgress = startProgress
            self.laneGuidance = laneGuidance
        }
    }

    /// Instruction variants from most to least preferred (CarPlay picks the longest that fits).
    static func instructionVariants(for step: RouteStep) -> [String] {
        let generated = VoicePromptPlanner.phrase(for: step).capitalizedFirst
        var short: String
        if step.isArrival {
            short = String(localized: "Arrive")
        } else if step.maneuver.isRoundabout && step.roundaboutExit > 0 {
            short = String(localized: "Take exit \(step.roundaboutExit)")
        } else if !step.exitNumber.isEmpty {
            short = String(localized: "Exit \(step.exitNumber)")
        } else {
            short = step.street.isEmpty ? (step.maneuver.phrase()?.capitalizedFirst ?? generated) : step.street
        }
        if short.isEmpty { short = String(localized: "Continue") }
        var out: [String] = []
        for v in [step.instruction, generated, short] where !v.isEmpty && !out.contains(v) { out.append(v) }
        return out
    }

    static func maneuver(for step: RouteStep, lanes: LaneSection?, junctionImage: UIImage?) -> CPManeuver {
        let maneuver = CPManeuver()
        let variants = instructionVariants(for: step)
        maneuver.instructionVariants = variants
        maneuver.dashboardInstructionVariants = variants
        maneuver.notificationInstructionVariants = variants
        if let image = UIImage(systemName: step.symbolName) {
            maneuver.symbolImage = image
            maneuver.dashboardSymbolImage = image
            maneuver.notificationSymbolImage = image
        }
        if let junctionImage {
            maneuver.junctionImage = junctionImage
            maneuver.dashboardJunctionImage = junctionImage
        }
        maneuver.initialTravelEstimates = CPTravelEstimates(distanceRemaining: Measurement(value: step.distance, unit: UnitLength.meters), timeRemaining: 0)
        var laneGuidance: AnyObject?
        defer { maneuver.userInfo = Info(startProgress: step.startProgress, laneGuidance: laneGuidance) }
        if #available(iOS 17.4, *) {
            maneuver.maneuverType = maneuverType(for: step)
            maneuver.trafficSide = TrafficSide.isLeftHand ? .left : .right
            maneuver.junctionType = step.maneuver.isRoundabout ? .roundabout : .intersection
            if let angle = step.turnAngle ?? step.maneuver.angle {
                maneuver.junctionExitAngle = Measurement(value: angle, unit: UnitAngle.degrees)
            }
            if !step.street.isEmpty { maneuver.roadFollowingManeuverVariants = [step.street] }
            if !step.exitNumber.isEmpty { maneuver.highwayExitLabel = step.exitNumber }
            if let lanes, let advice = LanePolicy.advice(lanes, maneuver: step.maneuver) {
                let guidance = CarPlayLaneGuidance.guidance(lanes, maneuver: step.maneuver, instruction: advice)
                laneGuidance = guidance
                maneuver.linkedLaneGuidance = guidance
            }
        }
        return maneuver
    }

    /// The lane picture as its own row under the turn card, shown with the symbol-only style CarPlay has for lane guidance.
    static func laneRow(for step: RouteStep, lanes: LaneSection) -> CPManeuver? {
        guard let image = LaneImage.image(LanePolicy.marked(lanes, maneuver: step.maneuver)) else { return nil }
        let row = CPManeuver()
        row.symbolImage = image
        row.instructionVariants = [LanePolicy.advice(lanes, maneuver: step.maneuver) ?? ""]
        row.userInfo = laneRowMarker
        return row
    }

    /// CarPlay's manoeuvre kind for the cluster and head-up display (Android `CarNav.step` maps to Android Auto's types).
    @available(iOS 17.4, *)
    static func maneuverType(for step: RouteStep) -> CPManeuverType {
        let m = step.maneuver
        if m.isRoundabout {
            if step.roundaboutExit > 0, let exit = CPManeuverType(rawValue: CPManeuverType.roundaboutExit1.rawValue + UInt(min(step.roundaboutExit, 19) - 1)) {
                return exit
            }
            return m == .roundaboutBack ? .uTurnAtRoundabout : .enterRoundabout
        }
        switch m {
        case .depart: return .startRoute
        case .arrive: return .arriveAtDestination
        case .arriveLeft: return .arriveAtDestinationLeft
        case .arriveRight: return .arriveAtDestinationRight
        case .straight: return .straightAhead
        case .follow, .switchMainRoad, .switchParallelRoad: return .followRoad
        case .turnLeft: return .leftTurn
        case .turnRight: return .rightTurn
        case .sharpLeft: return .sharpLeftTurn
        case .sharpRight: return .sharpRightTurn
        case .bearLeft: return .slightLeftTurn
        case .bearRight: return .slightRightTurn
        case .keepLeft: return .keepLeft
        case .keepRight: return .keepRight
        case .uTurn: return .uTurn
        case .uTurnWhenPossible: return .uTurnWhenPossible
        case .motorwayExitLeft: return .highwayOffRampLeft
        case .motorwayExitRight: return .highwayOffRampRight
        case .takeExit: return .offRamp
        case .entranceRamp, .enterMotorway, .enterMainRoad: return .onRamp
        case .takeFerry: return .enter_Ferry
        default:
            if step.isArrival { return .arriveAtDestination }
            guard let angle = step.turnAngle else { return .noTurn }
            switch abs(angle) {
            case ..<20: return .straightAhead
            case ..<50: return angle > 0 ? .slightRightTurn : .slightLeftTurn
            case ..<120: return angle > 0 ? .rightTurn : .leftTurn
            case ..<160: return angle > 0 ? .sharpRightTurn : .sharpLeftTurn
            default: return .uTurn
            }
        }
    }

    /// The car's own day or night look, overridden by Settings (Android `CarNav.carDark`: car, phone, day or night).
    static func isDark(setting: String, carDark: Bool, phoneDark: Bool) -> Bool {
        switch setting {
        case "day": return false
        case "night": return true
        case "phone": return phoneDark
        default: return carDark
        }
    }
}

/// Mapbox junction views, fetched once each (the URL needs the access token).
@MainActor
final class JunctionImages {
    static let shared = JunctionImages()
    private var images: [URL: UIImage] = [:]
    private var loading: Set<URL> = []

    func image(for url: URL?) -> UIImage? { url.flatMap { images[$0] } }

    /// Loads a junction view, calling back when it arrives.
    func load(_ url: URL?, then done: @escaping () -> Void) {
        guard let url, images[url] == nil, !loading.contains(url), let token = ProviderKeys.current.mapbox,
              var components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return }
        components.queryItems = (components.queryItems ?? []) + [URLQueryItem(name: "access_token", value: token)]
        guard let signed = components.url else { return }
        loading.insert(url)
        Task {
            let data = try? await ProviderHTTP.data(signed, provider: "Mapbox", timeout: 10)
            loading.remove(url)
            if let data, let image = UIImage(data: data) {
                images[url] = image
                done()
            }
        }
    }
}
