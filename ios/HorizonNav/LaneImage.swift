import CarPlay
import UIKit

/// The lanes drawn side by side, white arrows for the lanes to use and faded ones for the rest: a port of the Android
/// `CarNav.lanesPicture`, which Android Auto shows beside the turn. CarPlay shows it as the lane row under the manoeuvre.
enum LaneImage {
    static let laneWidth: CGFloat = 72
    static let height: CGFloat = 96

    static func image(_ lanes: [[(direction: String, use: Bool)]]) -> UIImage? {
        guard !lanes.isEmpty, lanes.count <= 10 else { return nil }
        let size = CGSize(width: laneWidth * CGFloat(lanes.count), height: height)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 2
        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            let c = context.cgContext
            c.setLineCap(.round)
            c.setLineJoin(.round)
            for (i, arrows) in lanes.enumerated() {
                let cx = laneWidth * CGFloat(i) + laneWidth / 2
                if i > 0 {
                    c.saveGState()
                    c.setStrokeColor(UIColor.white.withAlphaComponent(0.4).cgColor)
                    c.setLineWidth(3)
                    c.setLineDash(phase: 0, lengths: [10, 8])
                    c.move(to: CGPoint(x: laneWidth * CGFloat(i), y: 8))
                    c.addLine(to: CGPoint(x: laneWidth * CGFloat(i), y: height - 8))
                    c.strokePath()
                    c.restoreGState()
                }
                // The lane's other arrows first, then the ones to use on top.
                for arrow in arrows.sorted(by: { !$0.use && $1.use }) {
                    drawArrow(c, centerX: cx, angle: LanePolicy.angle(for: arrow.direction),
                              color: arrow.use ? .white : UIColor.white.withAlphaComponent(0.5))
                }
            }
        }
    }

    private static func drawArrow(_ c: CGContext, centerX cx: CGFloat, angle: Double, color: UIColor) {
        c.setStrokeColor(color.cgColor)
        c.setFillColor(color.cgColor)
        c.setLineWidth(7)
        let baseY = height - 10, midY = height * 0.52
        let path = UIBezierPath()
        path.move(to: CGPoint(x: cx, y: baseY))
        let tip: CGPoint
        let direction: Double
        if abs(angle) >= 179 {
            // A U-turn: up, over and back down.
            let side: CGFloat = angle < 0 ? -1 : 1
            path.addLine(to: CGPoint(x: cx, y: height * 0.34))
            path.addQuadCurve(to: CGPoint(x: cx + side * 14, y: 12), controlPoint: CGPoint(x: cx, y: 12))
            path.addQuadCurve(to: CGPoint(x: cx + side * 26, y: height * 0.34), controlPoint: CGPoint(x: cx + side * 26, y: 12))
            path.addLine(to: CGPoint(x: cx + side * 26, y: height * 0.48))
            tip = CGPoint(x: cx + side * 26, y: height * 0.48 + 4)
            direction = 180
        } else {
            path.addLine(to: CGPoint(x: cx, y: midY))
            let length: CGFloat = angle == 0 ? midY - 16 : 26
            let r = angle * .pi / 180
            tip = CGPoint(x: cx + CGFloat(sin(r)) * length, y: midY - CGFloat(cos(r)) * length)
            path.addLine(to: tip)
            direction = angle
        }
        c.addPath(path.cgPath)
        c.strokePath()
        // The arrowhead, pointing the way the arrow goes.
        let r = direction * .pi / 180
        let fx = CGFloat(sin(r)), fy = CGFloat(-cos(r))
        let px = -fy, py = fx
        c.move(to: CGPoint(x: tip.x + fx * 12, y: tip.y + fy * 12))
        c.addLine(to: CGPoint(x: tip.x - fx * 4 + px * 12, y: tip.y - fy * 4 + py * 12))
        c.addLine(to: CGPoint(x: tip.x - fx * 4 - px * 12, y: tip.y - fy * 4 - py * 12))
        c.closePath()
        c.fillPath()
    }
}

/// CarPlay's lane guidance for the instrument cluster and head-up display (iOS 17.4 and later): each lane's arrow angles,
/// with the arrow to follow highlighted on the lanes to use (Android `CarNav.step` lanes).
@available(iOS 17.4, *)
enum CarPlayLaneGuidance {
    struct LaneAngles: Equatable {
        var highlighted: Double?
        var others: [Double]
        var preferred: Bool
    }

    /// The angles for each lane: the used arrow is highlighted and left out of the others, as CarPlay requires.
    static func angles(_ marked: [[(direction: String, use: Bool)]]) -> [LaneAngles] {
        marked.map { arrows in
            let used = arrows.first { $0.use }
            var others: [Double] = []
            for a in arrows where a.direction != used?.direction {
                let angle = LanePolicy.angle(for: a.direction)
                if !others.contains(angle) && angle != used.map({ LanePolicy.angle(for: $0.direction) }) { others.append(angle) }
            }
            return LaneAngles(highlighted: used.map { LanePolicy.angle(for: $0.direction) }, others: others, preferred: used != nil)
        }
    }

    static func guidance(_ section: LaneSection, maneuver: Maneuver, instruction: String) -> CPLaneGuidance {
        let guidance = CPLaneGuidance()
        guidance.instructionVariants = [instruction]
        guidance.lanes = angles(LanePolicy.marked(section, maneuver: maneuver)).map { lane in
            let others = lane.others.map { Measurement(value: $0, unit: UnitAngle.degrees) }
            if #available(iOS 18.0, *) {
                if let highlighted = lane.highlighted {
                    return CPLane(angles: others, highlightedAngle: Measurement(value: highlighted, unit: UnitAngle.degrees), isPreferred: lane.preferred)
                }
                return CPLane(angles: others)
            }
            let cpLane = CPLane()
            if let highlighted = lane.highlighted { cpLane.primaryAngle = Measurement(value: highlighted, unit: UnitAngle.degrees) }
            cpLane.secondaryAngles = others
            cpLane.status = lane.preferred ? .preferred : .notGood
            return cpLane
        }
        return guidance
    }
}
