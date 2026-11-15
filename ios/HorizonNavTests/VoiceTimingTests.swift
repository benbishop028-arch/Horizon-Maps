import CoreLocation
import Testing
@testable import HorizonNav

private func turn(_ maneuver: Maneuver = .turnRight, street: String = "High Street", exitNumber: String = "", arrival: Bool = false) -> RouteStep {
    RouteStep(instruction: "Turn right onto High Street", notice: nil, distance: 300, startProgress: 2000, turnAngle: 90,
              isArrival: arrival, maneuver: maneuver, street: street, exitNumber: exitNumber)
}

/// Runs the planner down an approach from `from` metres to the junction, as fixes arriving every `step` metres would.
private func approach(_ planner: inout VoicePromptPlanner, step: RouteStep, from: Double = 2000, every: Double = 10,
                      speed: Double, lanes: LaneSection? = nil, fastRoad: Bool = false) -> [(Double, VoicePrompt)] {
    var out: [(Double, VoicePrompt)] = []
    var d = from
    while d >= 0 {
        for p in planner.prompts(stepIndex: 0, step: step, distance: d, speed: speed, lanes: lanes, fastRoad: fastRoad, imperial: false) {
            out.append((d, p))
        }
        d -= every
    }
    return out
}

struct VoiceTimingTests {
    @Test func townApproachHasMainAndFinalCallsOnce() {
        var planner = VoicePromptPlanner()
        let calls = approach(&planner, step: turn(), speed: 13)
        #expect(calls.map(\.1.key) == ["0f", "0n"])
        #expect(calls[0].0 == 520)
        #expect(calls[0].1.text == "In 500 metres, turn right onto High Street")
        #expect(calls[1].0 == 120)
        #expect(calls[1].1.text == "Turn right onto High Street")
    }

    @Test func fastApproachAddsTheHeadsUp() {
        var planner = VoicePromptPlanner()
        let calls = approach(&planner, step: turn(), speed: 25)
        #expect(calls.map(\.1.key) == ["0e", "0f", "0n"])
        #expect(calls[0].0 == 1700)
        #expect(calls[0].1.text == "In 1.7 kilometres, turn right onto High Street")
    }

    @Test func headsUpNeedsSpeedAndIsNeverForArrival() {
        var slow = VoicePromptPlanner()
        #expect(slow.prompts(stepIndex: 0, step: turn(), distance: 1500, speed: 17, lanes: nil, fastRoad: false, imperial: false).isEmpty)
        var arriving = VoicePromptPlanner()
        let arrival = turn(.arriveLeft, street: "", arrival: true)
        let calls = approach(&arriving, step: arrival, speed: 30)
        #expect(calls.map(\.1.text) == ["In 500 metres, you will arrive. Your destination is on the left", "Your destination is on the left"])
    }

    @Test func earlyLaneCallOnFastRoads() {
        var planner = VoicePromptPlanner()
        let lanes = LaneSection(start: 1500, end: 2000, lanes: [Lane(directions: ["STRAIGHT"], follow: nil), Lane(directions: ["STRAIGHT"], follow: nil),
                                                                Lane(directions: ["RIGHT"], follow: "RIGHT")])
        let calls = approach(&planner, step: turn(.motorwayExitRight, street: "", exitNumber: "4"), speed: 30, lanes: lanes, fastRoad: true)
        #expect(calls.first?.0 == 1850)
        #expect(calls.first?.1.text == "In about one and a half kilometres, move into the right lane")
        #expect(calls.first { $0.1.key == "0f" }?.1.text == "In 500 metres, take the exit on the right. Use the right lane")
    }

    @Test func turnSeenLateSkipsTheMainCall() {
        var planner = VoicePromptPlanner()
        let calls = approach(&planner, step: turn(), from: 180, speed: 10)
        #expect(calls.map(\.1.key) == ["0n"])
    }

    @Test func eachManeuverIsSpokenSeparatelyAndResetClears() {
        var planner = VoicePromptPlanner()
        #expect(planner.prompts(stepIndex: 3, step: turn(), distance: 100, speed: 5, lanes: nil, fastRoad: false, imperial: false).count == 1)
        #expect(planner.prompts(stepIndex: 3, step: turn(), distance: 90, speed: 5, lanes: nil, fastRoad: false, imperial: false).isEmpty)
        #expect(planner.prompts(stepIndex: 4, step: turn(), distance: 90, speed: 5, lanes: nil, fastRoad: false, imperial: false).count == 1)
        planner.reset()
        #expect(planner.prompts(stepIndex: 3, step: turn(), distance: 90, speed: 5, lanes: nil, fastRoad: false, imperial: false).count == 1)
    }

    @Test func spokenDistancesRoundLikeAndroid() {
        #expect(VoicePromptPlanner.spokenDistance(487, imperial: false) == "500 metres")
        #expect(VoicePromptPlanner.spokenDistance(1640, imperial: false) == "1.6 kilometres")
        #expect(VoicePromptPlanner.spokenDistance(140, imperial: true) == "450 feet")
        #expect(VoicePromptPlanner.spokenDistance(2414, imperial: true) == "1.5 miles")
    }

    @Test func appleMapsStepsUseTheirOwnText() {
        let step = RouteStep(instruction: "Turn left onto Mill Lane", notice: nil, distance: 100, startProgress: 0, turnAngle: -90, isArrival: false)
        #expect(VoicePromptPlanner.phrase(for: step) == "turn left onto Mill Lane")
        let roundabout = RouteStep(instruction: "", notice: nil, distance: 100, startProgress: 0, turnAngle: nil, isArrival: false,
                                   maneuver: .roundaboutRight, street: "A3", roundaboutExit: 3)
        #expect(VoicePromptPlanner.phrase(for: roundabout) == "at the roundabout, take the 3rd exit onto A3")
    }

    @Test func fastRoadFromExitNumberOrLimit() throws {
        let route = try TomTomRoutingProvider.parse(try fixture("tomtom-route"))[0]
        #expect(!NavigationStore.isFastRoad(step: route.steps[0], route: route, progress: 10))
        #expect(NavigationStore.isFastRoad(step: turn(exitNumber: "12"), route: route, progress: 10))
    }
}
