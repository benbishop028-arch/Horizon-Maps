import CoreLocation
import Foundation

/// A manoeuvre kind, named as in the Android app (`Geo.maneuverText`, `CarNav.step`), so the providers,
/// the voice and the car screen agree on one vocabulary whichever service planned the route.
enum Maneuver: String, Codable, CaseIterable {
    case depart = "DEPART"
    case arrive = "ARRIVE"
    case arriveLeft = "ARRIVE_LEFT"
    case arriveRight = "ARRIVE_RIGHT"
    case straight = "STRAIGHT"
    case follow = "FOLLOW"
    case turnLeft = "TURN_LEFT"
    case turnRight = "TURN_RIGHT"
    case sharpLeft = "SHARP_LEFT"
    case sharpRight = "SHARP_RIGHT"
    case bearLeft = "BEAR_LEFT"
    case bearRight = "BEAR_RIGHT"
    case keepLeft = "KEEP_LEFT"
    case keepRight = "KEEP_RIGHT"
    case uTurn = "MAKE_UTURN"
    case uTurnWhenPossible = "TRY_MAKE_UTURN"
    case roundaboutLeft = "ROUNDABOUT_LEFT"
    case roundaboutRight = "ROUNDABOUT_RIGHT"
    case roundaboutCross = "ROUNDABOUT_CROSS"
    case roundaboutBack = "ROUNDABOUT_BACK"
    case motorwayExitLeft = "MOTORWAY_EXIT_LEFT"
    case motorwayExitRight = "MOTORWAY_EXIT_RIGHT"
    case entranceRamp = "ENTRANCE_RAMP"
    case enterMotorway = "ENTER_MOTORWAY"
    case enterMainRoad = "ENTER_HIGHWAY"
    case takeExit = "TAKE_EXIT"
    case takeFerry = "TAKE_FERRY"
    case switchParallelRoad = "SWITCH_PARALLEL_ROAD"
    case switchMainRoad = "SWITCH_MAIN_ROAD"
    case unknown = "UNKNOWN"

    /// Accepts the TomTom spellings too ("TURN_LEFT", "turnLeft", "ENTER_FREEWAY").
    init(providerName: String) {
        let snake = providerName.replacingOccurrences(of: "([a-z0-9])([A-Z])", with: "$1_$2", options: .regularExpression).uppercased()
        switch snake {
        case "ENTER_FREEWAY": self = .enterMainRoad
        default: self = Maneuver(rawValue: snake) ?? .unknown
        }
    }

    var isArrival: Bool { self == .arrive || self == .arriveLeft || self == .arriveRight }
    var isRoundabout: Bool { rawValue.hasPrefix("ROUNDABOUT") }
    var isLeft: Bool { rawValue.contains("LEFT") }
    var isRight: Bool { rawValue.contains("RIGHT") }

    /// Turn angle for the arrow, negative to the left (Android `Geo.maneuverAngle`). Nil when unknown.
    var angle: Double? {
        switch self {
        case .turnLeft, .roundaboutLeft: return -90
        case .turnRight, .roundaboutRight: return 90
        case .sharpLeft: return -140
        case .sharpRight: return 140
        case .bearLeft, .motorwayExitLeft: return -35
        case .bearRight, .motorwayExitRight: return 35
        case .keepLeft: return -22
        case .keepRight: return 22
        case .straight, .depart, .follow, .roundaboutCross, .enterMotorway, .enterMainRoad, .switchMainRoad, .switchParallelRoad: return 0
        case .roundaboutBack, .uTurn, .uTurnWhenPossible: return 180
        default: return nil
        }
    }

    /// SF Symbol for the manoeuvre, or nil to fall back to the geometry.
    var symbolName: String? {
        switch self {
        case .arrive, .arriveLeft, .arriveRight: return "flag.checkered"
        case .turnLeft, .sharpLeft: return "arrow.turn.up.left"
        case .turnRight, .sharpRight: return "arrow.turn.up.right"
        case .bearLeft, .keepLeft, .motorwayExitLeft: return "arrow.up.left"
        case .bearRight, .keepRight, .motorwayExitRight: return "arrow.up.right"
        case .uTurn, .uTurnWhenPossible: return "arrow.uturn.down"
        case .roundaboutLeft: return "arrow.triangle.turn.up.right.circle"
        case .roundaboutRight, .roundaboutCross, .roundaboutBack: return "arrow.triangle.turn.up.right.circle"
        case .takeFerry: return "ferry"
        case .straight, .follow, .depart, .enterMotorway, .enterMainRoad, .entranceRamp, .takeExit, .switchMainRoad, .switchParallelRoad: return "arrow.up"
        case .unknown: return nil
        }
    }

    /// Spoken and short wording, e.g. "turn left" (Android `Geo.maneuverText`). Nil when unknown.
    func phrase(exit: Int = 0) -> String? {
        if isRoundabout {
            if self == .roundaboutBack {
                return exit > 0 ? String(localized: "take the \(Self.ordinal(exit)) exit at the roundabout") : String(localized: "go all the way round the roundabout")
            }
            return exit > 0 ? String(localized: "at the roundabout, take the \(Self.ordinal(exit)) exit") : String(localized: "take the roundabout")
        }
        switch self {
        case .arrive: return String(localized: "arrive")
        case .arriveLeft: return String(localized: "arrive, destination on the left")
        case .arriveRight: return String(localized: "arrive, destination on the right")
        case .depart: return String(localized: "head out")
        case .uTurn: return String(localized: "make a U-turn")
        case .uTurnWhenPossible: return String(localized: "make a U-turn when possible")
        case .straight: return String(localized: "continue straight")
        case .follow: return String(localized: "continue")
        case .turnLeft: return String(localized: "turn left")
        case .turnRight: return String(localized: "turn right")
        case .sharpLeft: return String(localized: "turn sharp left")
        case .sharpRight: return String(localized: "turn sharp right")
        case .bearLeft: return String(localized: "bear left")
        case .bearRight: return String(localized: "bear right")
        case .keepLeft: return String(localized: "keep left")
        case .keepRight: return String(localized: "keep right")
        case .enterMotorway: return String(localized: "join the motorway")
        case .enterMainRoad: return String(localized: "join the main road")
        case .takeExit: return String(localized: "take the exit")
        case .motorwayExitLeft: return String(localized: "take the exit on the left")
        case .motorwayExitRight: return String(localized: "take the exit on the right")
        case .entranceRamp: return String(localized: "take the slip road")
        case .switchParallelRoad: return String(localized: "switch to the parallel road")
        case .switchMainRoad: return String(localized: "switch to the main road")
        case .takeFerry: return String(localized: "take the ferry")
        default: return nil
        }
    }

    static func ordinal(_ n: Int) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .ordinal
        return formatter.string(from: NSNumber(value: n)) ?? "\(n)"
    }
}

/// One lane at a junction: the arrows painted on it, and the arrow to follow when the route uses it
/// (Android `Lane`). Directions use the Android names: "LEFT", "SLIGHT_RIGHT", "STRAIGHT", "LEFT_U_TURN"...
struct Lane: Equatable, Codable {
    var directions: [String]
    var follow: String?
}

/// The lanes from `start` to `end` metres along a route (Android `LaneSec`).
struct LaneSection: Equatable, Codable {
    var start: CLLocationDistance
    var end: CLLocationDistance
    var lanes: [Lane]
}

/// A posted speed limit from `start` to `end` metres along a route (Android `LimitSec`).
struct SpeedLimitSection: Equatable, Codable {
    var start: CLLocationDistance
    var end: CLLocationDistance
    var metersPerSecond: Double
}

extension RouteOption {
    /// The posted limit at a distance along the route, in metres per second.
    func speedLimit(at progress: CLLocationDistance) -> Double? {
        speedLimits.first { progress >= $0.start && progress < $0.end }?.metersPerSecond
    }

    /// The lanes for the manoeuvre at `stepIndex`, chosen with the Android `NavEngine` rules: a section has to run up to
    /// this junction (not end well before it, at an earlier slip road), must not belong to the next turn, and among the
    /// candidates the one with the most lanes wins, then the one ending nearest the junction.
    func laneSection(forStep stepIndex: Int, progress: CLLocationDistance, fastRoad: Bool) -> LaneSection? {
        guard steps.indices.contains(stepIndex) else { return nil }
        let step = steps[stepIndex]
        let distance = step.startProgress - progress
        guard !step.isArrival, distance < (fastRoad ? 1900 : 1000) else { return nil }
        let next = steps.indices.contains(stepIndex + 1) ? steps[stepIndex + 1] : nil
        let at = step.startProgress
        return laneSections.filter { section in
            progress < section.end + 5 && section.end > at - 60 && section.end < at + 200 &&
                (next == nil || next!.startProgress - at < 15 || section.end < next!.startProgress - 10 ||
                 abs(section.end - at) < abs(section.end - next!.startProgress))
        }.sorted { a, b in
            a.lanes.count != b.lanes.count ? a.lanes.count > b.lanes.count : abs(a.end - at) < abs(b.end - at)
        }.first
    }
}

enum LanePolicy {
    /// Whether a lane arrow leads where the manoeuvre goes (Android `CarNav.laneHit`).
    static func laneHit(_ direction: String, maneuver: Maneuver) -> Bool {
        let m = maneuver.rawValue
        if m.contains("UTURN") || m.contains("U_TURN") { return direction.contains("U_TURN") }
        if maneuver.isLeft { return direction.contains("LEFT") && !direction.contains("U_TURN") }
        if maneuver.isRight { return direction.contains("RIGHT") && !direction.contains("U_TURN") }
        return direction == "STRAIGHT"
    }

    /// Each lane's arrows with the ones to use marked: the provider's "follow" arrow when it gives one, otherwise
    /// the arrows that lead where the manoeuvre goes.
    static func marked(_ section: LaneSection, maneuver: Maneuver) -> [[(direction: String, use: Bool)]] {
        let anyFollow = section.lanes.contains { $0.follow != nil }
        return section.lanes.map { lane in
            let directions = lane.directions.isEmpty ? ["STRAIGHT"] : lane.directions
            return directions.map { d in (d, anyFollow ? d == lane.follow : laneHit(d, maneuver: maneuver)) }
        }
    }

    /// Arrow angle in degrees, negative to the left, as drawn in the Android lane picture.
    static func angle(for direction: String) -> Double {
        let magnitude: Double
        if direction.contains("U_TURN") { magnitude = 180 }
        else if direction.contains("SHARP") { magnitude = 135 }
        else if direction.contains("SLIGHT") || direction.contains("BEAR") { magnitude = 45 }
        else if direction.contains("LEFT") || direction.contains("RIGHT") { magnitude = 90 }
        else { magnitude = 0 }
        return direction.contains("LEFT") ? -magnitude : magnitude
    }

    /// "Use the left two lanes" and similar (Android `Geo.laneAdvice`). Nil when every lane works.
    static func advice(_ section: LaneSection, maneuver: Maneuver, move: Bool = false) -> String? {
        let n = section.lanes.count
        guard n >= 2 else { return nil }
        let anyFollow = section.lanes.contains { $0.follow != nil }
        let active = section.lanes.indices.filter { i in
            anyFollow ? section.lanes[i].follow != nil : section.lanes[i].directions.contains { laneHit($0, maneuver: maneuver) }
        }
        guard let first = active.first, let last = active.last, active.count < n else { return nil }
        let count = active.count
        if first == 0 {
            if count == 1 { return move ? String(localized: "move into the left lane") : String(localized: "Use the left lane") }
            return move ? String(localized: "move into the left \(count) lanes") : String(localized: "Use the left \(count) lanes")
        }
        if last == n - 1 {
            if count == 1 { return move ? String(localized: "move into the right lane") : String(localized: "Use the right lane") }
            return move ? String(localized: "move into the right \(count) lanes") : String(localized: "Use the right \(count) lanes")
        }
        if count == 1 { return move ? String(localized: "move into lane \(first + 1) from the left") : String(localized: "Use lane \(first + 1) from the left") }
        return move ? String(localized: "move into the middle \(count) lanes") : String(localized: "Use the middle \(count) lanes")
    }
}
