import CarPlay
import CoreLocation
import Testing
import UIKit
@testable import HorizonNav

private let threeLanes = LaneSection(start: 0, end: 300, lanes: [
    Lane(directions: ["STRAIGHT"], follow: nil),
    Lane(directions: ["STRAIGHT", "RIGHT"], follow: "RIGHT"),
    Lane(directions: ["RIGHT"], follow: "RIGHT"),
])

/// Alpha and whiteness of one point of an image, in points.
private func pixel(_ image: UIImage, x: CGFloat, y: CGFloat) -> (white: CGFloat, alpha: CGFloat) {
    let cg = image.cgImage!
    let scale = image.scale
    var data = [UInt8](repeating: 0, count: 4)
    let context = CGContext(data: &data, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4,
                            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    context.draw(cg, in: CGRect(x: -x * scale, y: -(CGFloat(cg.height) - y * scale), width: CGFloat(cg.width), height: CGFloat(cg.height)))
    let alpha = CGFloat(data[3]) / 255
    return (alpha > 0 ? CGFloat(data[0]) / 255 / alpha : 0, alpha)
}

struct LaneImageTests {
    @Test func marksTheProviderLanesToFollow() {
        let marked = LanePolicy.marked(threeLanes, maneuver: .turnRight)
        #expect(marked.map { $0.map(\.direction) } == [["STRAIGHT"], ["STRAIGHT", "RIGHT"], ["RIGHT"]])
        #expect(marked.map { $0.map(\.use) } == [[false], [false, true], [true]])
    }

    @Test func withoutFollowTheManeuverPicksTheLanes() {
        let section = LaneSection(start: 0, end: 100, lanes: [Lane(directions: ["LEFT"], follow: nil), Lane(directions: ["STRAIGHT", "SLIGHT_RIGHT"], follow: nil)])
        #expect(LanePolicy.marked(section, maneuver: .bearRight).map { $0.map(\.use) } == [[false], [false, true]])
        #expect(LanePolicy.marked(section, maneuver: .straight).map { $0.map(\.use) } == [[false], [true, false]])
        #expect(LanePolicy.advice(threeLanes, maneuver: .turnRight) == "Use the right 2 lanes")
    }

    @Test func pictureHasOneColumnPerLaneWithUsedArrowsWhite() throws {
        let image = try #require(LaneImage.image(LanePolicy.marked(threeLanes, maneuver: .turnRight)))
        #expect(image.size == CGSize(width: 216, height: 96))
        // The straight stem low in each lane: faded in lane 1 (not used), white in lane 3 (used).
        let unused = pixel(image, x: 36, y: 80)
        let used = pixel(image, x: 180, y: 80)
        #expect(unused.alpha > 0.3 && unused.alpha < 0.7)
        #expect(used.alpha > 0.95 && used.white > 0.95)
        // The right turn arrow head in lane 3 sits to the right of the stem.
        #expect(pixel(image, x: 180 + 26, y: 96 * 0.52).alpha > 0.9)
        #expect(LaneImage.image([]) == nil)
        #expect(LaneImage.image(Array(repeating: [("STRAIGHT", true)], count: 11)) == nil)
    }

    @Test func arrowAnglesMatchTheAndroidPicture() {
        #expect(LanePolicy.angle(for: "STRAIGHT") == 0)
        #expect(LanePolicy.angle(for: "SLIGHT_LEFT") == -45)
        #expect(LanePolicy.angle(for: "RIGHT") == 90)
        #expect(LanePolicy.angle(for: "SHARP_RIGHT") == 135)
        #expect(LanePolicy.angle(for: "LEFT_U_TURN") == -180)
    }

    @Test @available(iOS 17.4, *)
    func carPlayLaneGuidanceHighlightsTheArrowToFollow() {
        let lanes = CarPlayLaneGuidance.angles(LanePolicy.marked(threeLanes, maneuver: .turnRight))
        #expect(lanes == [
            .init(highlighted: nil, others: [0], preferred: false),
            .init(highlighted: 90, others: [0], preferred: true),
            .init(highlighted: 90, others: [], preferred: true),
        ])
        let guidance = CarPlayLaneGuidance.guidance(threeLanes, maneuver: .turnRight, instruction: "Use the right 2 lanes")
        #expect(guidance.lanes.count == 3)
        #expect(guidance.instructionVariants == ["Use the right 2 lanes"])
    }

    @Test func laneSectionIsChosenForTheRightJunction() throws {
        let route = try MapboxRoutingProvider.parse(try fixture("mapbox-directions"))[0]
        #expect(route.laneSection(forStep: 0, progress: 100, fastRoad: false) != nil)
        // Past the junction the section no longer applies, and the arrival has none.
        #expect(route.laneSection(forStep: 0, progress: 360, fastRoad: false) == nil)
        #expect(route.laneSection(forStep: 1, progress: 400, fastRoad: false) == nil)
    }
}

struct CarPlayManeuverTests {
    @Test func instructionVariantsRunFromLongToShort() {
        let step = RouteStep(instruction: "At the roundabout take the 2nd exit onto Horse Guards Avenue", notice: nil, distance: 200,
                             startProgress: 300, turnAngle: 90, isArrival: false, maneuver: .roundaboutRight, street: "Horse Guards Avenue", roundaboutExit: 2)
        let variants = CarPlayManeuvers.instructionVariants(for: step)
        #expect(variants.first == step.instruction)
        #expect(variants.last == "Take exit 2")
        let exit = RouteStep(instruction: "", notice: nil, distance: 0, startProgress: 0, turnAngle: nil, isArrival: false, maneuver: .motorwayExitLeft, exitNumber: "4")
        #expect(CarPlayManeuvers.instructionVariants(for: exit) == ["Take the exit on the left", "Exit 4"])
    }

    @Test @available(iOS 17.4, *)
    func maneuverTypesForTheCluster() {
        func step(_ m: Maneuver, exit: Int = 0, angle: Double? = nil) -> RouteStep {
            RouteStep(instruction: "", notice: nil, distance: 0, startProgress: 0, turnAngle: angle, isArrival: m.isArrival, maneuver: m, roundaboutExit: exit)
        }
        #expect(CarPlayManeuvers.maneuverType(for: step(.roundaboutRight, exit: 3)) == .roundaboutExit3)
        #expect(CarPlayManeuvers.maneuverType(for: step(.roundaboutBack)) == .uTurnAtRoundabout)
        #expect(CarPlayManeuvers.maneuverType(for: step(.motorwayExitLeft)) == .highwayOffRampLeft)
        #expect(CarPlayManeuvers.maneuverType(for: step(.arriveRight)) == .arriveAtDestinationRight)
        // Apple Maps steps have no kind: the geometry decides.
        #expect(CarPlayManeuvers.maneuverType(for: step(.unknown, angle: -95)) == .leftTurn)
        let maneuver = CarPlayManeuvers.maneuver(for: step(.turnRight), lanes: threeLanes, junctionImage: nil)
        #expect(maneuver.maneuverType == .rightTurn)
        let guidance = (maneuver.userInfo as? CarPlayManeuvers.Info)?.laneGuidance as? CPLaneGuidance
        #expect(guidance?.lanes.count == 3)
        #expect(maneuver.junctionExitAngle?.value == 90)
        #expect(CarPlayManeuvers.laneRow(for: step(.turnRight), lanes: threeLanes)?.userInfo as? String == CarPlayManeuvers.laneRowMarker)
    }

    @Test func dayAndNightFollowTheChoice() {
        #expect(CarPlayManeuvers.isDark(setting: "car", carDark: true, phoneDark: false))
        #expect(!CarPlayManeuvers.isDark(setting: "phone", carDark: true, phoneDark: false))
        #expect(!CarPlayManeuvers.isDark(setting: "day", carDark: true, phoneDark: true))
        #expect(CarPlayManeuvers.isDark(setting: "night", carDark: false, phoneDark: false))
    }

    @Test func speedDisplayUnitsAndOverLimit() {
        #expect(SpeedDisplay.isOver(speed: 15, limit: 13.4))
        #expect(!SpeedDisplay.isOver(speed: 13.4, limit: 13.4))
        #expect(!SpeedDisplay.isOver(speed: 40, limit: nil))
    }
}

struct SpeedCameraTests {
    @Test func overpassAnswerParsesWithDirections() throws {
        let cameras = try SpeedCameras.parse(try fixture("overpass-cameras"))
        #expect(cameras.count == 5)
        #expect(cameras[0].direction == 90)
        #expect(cameras[1].direction == nil)
        #expect(cameras[3].direction == nil)
        #expect(cameras[4].direction == 80)
        let busy = Data(#"{"elements":[],"remark":"runtime error: Query timed out in \"query\" at line 1 after 26 seconds."}"#.utf8)
        #expect(throws: ProviderError.self) { try SpeedCameras.parse(busy) }
    }

    @Test func packCamerasFileIsReadInsideTheBox() {
        let text = "1624725828\t1.498636\t103.765492\t\n1625125833\t1.450598\t103.817730\t150\n1625225830\t1.445170\t103.777015\t\n"
        let cameras = SpeedCameras.parsePackFile(text, south: 1.44, west: 103.77, north: 1.46, east: 103.82)
        #expect(cameras.count == 2)
        #expect(cameras[0].direction == 150)
        #expect(cameras[1].direction == nil)
    }

    @Test func placementKeepsCamerasOnTheRouteFacingTheCar() throws {
        let route = try TomTomRoutingProvider.parse(try fixture("tomtom-route"))[0]
        let placed = SpeedCameras.place(try SpeedCameras.parse(try fixture("overpass-cameras")), on: route)
        // On the line facing east, its duplicate merged, the other carriageway's and the one 90 m away dropped.
        #expect(placed.count == 2)
        #expect(placed[0].camera.direction == 90)
        #expect(abs(placed[0].offset - (route.cumulative[2] + 21)) < 5)
        #expect(placed[1].camera.direction == 80)
    }

    @Test func warningTimingMatchesAndroid() throws {
        let camera = PlacedCamera(camera: SpeedCamera(latitude: 0, longitude: 0, direction: 90), offset: 1000)
        // In town: 500 m ahead.
        #expect(SpeedCameras.warning([camera], progress: 400, speed: 13, heading: 90) == nil)
        #expect(SpeedCameras.warning([camera], progress: 500, speed: 13, heading: 90)?.1 == 500)
        // At 35 m/s: about 18 seconds, 630 m.
        #expect(SpeedCameras.warning([camera], progress: 380, speed: 35, heading: 90)?.1 == 620)
        // Facing the other way: no warning.
        #expect(SpeedCameras.warning([camera], progress: 700, speed: 13, heading: 270) == nil)
        // Just passed: still within 15 m behind.
        #expect(SpeedCameras.warning([camera], progress: 1010, speed: 13, heading: 90)?.1 == 0)
        #expect(SpeedCameras.warning([camera], progress: 1020, speed: 13, heading: 90) == nil)
        #expect(SpeedCameras.parseDirection("NE") == 45)
        #expect(SpeedCameras.parseDirection("-90") == 270)
    }
}
