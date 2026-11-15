import AVFoundation
import CoreLocation
import os

/// One spoken prompt. Directions cut off whatever is being said; alerts wait their turn (Android `NavEngine.speak`).
struct VoicePrompt: Equatable {
    var key: String
    var text: String
    var isAlert = false
}

/// When to speak, ported from the Android `NavEngine` call-outs: a heads-up between 1.7 and 1.1 km on fast roads, an early
/// lane call about a mile out, the main call between 520 and 200 m, and the final one within 120 m. Each fires once per
/// manoeuvre. Pure logic, so the timing is unit tested.
struct VoicePromptPlanner {
    /// Heads-up on fast roads: further than 1100 m and up to 1700 m, above 18 m/s (about 40 mph).
    static let earlyFrom: CLLocationDistance = 1700
    static let earlyUntil: CLLocationDistance = 1100
    static let earlySpeed: Double = 18
    static let earlyLaneRange: ClosedRange<CLLocationDistance> = 1250...1850
    /// The main call: up to 520 m and further than 200 m.
    static let mainFrom: CLLocationDistance = 520
    static let mainUntil: CLLocationDistance = 200
    static let finalDistance: CLLocationDistance = 120

    private(set) var said: Set<String> = []

    mutating func reset() { said.removeAll() }

    /// The prompts due for the upcoming manoeuvre at `distance` metres.
    mutating func prompts(stepIndex: Int, step: RouteStep, distance d: CLLocationDistance, speed: Double,
                          lanes: LaneSection?, fastRoad: Bool, imperial: Bool) -> [VoicePrompt] {
        let arriving = step.isArrival
        let key = "\(stepIndex)"
        let phrase = Self.phrase(for: step)
        let laneAdvice = lanes.flatMap { LanePolicy.advice($0, maneuver: step.maneuver) }
        let laneMove = lanes.flatMap { LanePolicy.advice($0, maneuver: step.maneuver, move: true) }
        let side = Self.arrivalSide(step.maneuver)
        let spoken = Self.spokenDistance(d, imperial: imperial)
        var out: [VoicePrompt] = []
        if fastRoad, let laneAdvice, Self.earlyLaneRange.contains(d), said.insert("\(key)l").inserted {
            let lane = laneMove ?? laneAdvice
            out.append(VoicePrompt(key: "\(key)l", text: imperial ? String(localized: "In about a mile, \(lane)")
                                                               : String(localized: "In about one and a half kilometres, \(lane)")))
        }
        if !arriving, d <= Self.earlyFrom, d > Self.earlyUntil, speed > Self.earlySpeed, said.insert("\(key)e").inserted {
            out.append(VoicePrompt(key: "\(key)e", text: String(localized: "In \(spoken), \(phrase)")))
        }
        if d <= Self.mainFrom, d > Self.mainUntil, said.insert("\(key)f").inserted {
            let lane = laneAdvice.map { ". \($0)" } ?? ""
            let text = arriving
                ? String(localized: "In \(spoken), you will arrive") + (side.map { ". \($0)" } ?? "")
                : String(localized: "In \(spoken), \(phrase)") + lane
            out.append(VoicePrompt(key: "\(key)f", text: text))
        }
        if d <= Self.finalDistance, said.insert("\(key)n").inserted {
            let text = arriving ? (side ?? String(localized: "You have arrived")) : phrase.capitalizedFirst
            out.append(VoicePrompt(key: "\(key)n", text: text))
        }
        return out
    }

    /// "turn left onto Smith Street" from the manoeuvre kind; Apple Maps routes, which have no kind, use their own text.
    static func phrase(for step: RouteStep) -> String {
        if let word = step.maneuver.phrase(exit: step.roundaboutExit) {
            return step.street.isEmpty || step.isArrival ? word : word + String(localized: " onto \(step.street)")
        }
        let text = step.instruction.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return String(localized: "continue") }
        return text.prefix(1).lowercased() + text.dropFirst()
    }

    static func arrivalSide(_ maneuver: Maneuver) -> String? {
        switch maneuver {
        case .arriveLeft: return String(localized: "Your destination is on the left")
        case .arriveRight: return String(localized: "Your destination is on the right")
        default: return nil
        }
    }

    /// Distances as spoken, rounded like the Android `Geo.spoken`: 50 m or 50 ft steps, then tenths of a kilometre or mile.
    static func spokenDistance(_ m: CLLocationDistance, imperial: Bool) -> String {
        if imperial {
            let feet = m * 3.28084
            if feet < 528 { return String(localized: "\(Int((feet / 50).rounded()) * 50) feet") }
            return String(localized: "\(String(format: "%.1f", m / 1609.344)) miles")
        }
        if m < 1000 { return String(localized: "\(Int((m / 50).rounded()) * 50) metres") }
        return String(localized: "\(String(format: "%.1f", m / 1000)) kilometres")
    }
}

/// Speaks prompts with `AVSpeechSynthesizer`. The audio session uses the playback category in the voice prompt mode, which
/// CarPlay routes as navigation audio, ducks music and pauses spoken audio such as podcasts while a prompt plays, then
/// gives the audio back. The voice follows the app's language.
@MainActor
final class VoiceGuidance: NSObject, AVSpeechSynthesizerDelegate {
    static let shared = VoiceGuidance()

    private let synthesizer = AVSpeechSynthesizer()
    private let session = AVAudioSession.sharedInstance()
    private let log = Logger(subsystem: "com.horizonnav.ios", category: "voice")

    override private init() {
        super.init()
        synthesizer.delegate = self
    }

    /// 0 every direction and alert, 1 alerts only, 2 silent.
    var soundMode: Int {
        get { AppSettings.soundMode }
        set { AppSettings.defaults.set(newValue, forKey: AppSettings.Keys.soundMode); if newValue == 2 { stop() } }
    }

    var isMuted: Bool { soundMode == 2 }

    func toggleMute() { soundMode = isMuted ? 0 : 2 }

    func speak(_ prompt: VoicePrompt) {
        let mode = soundMode
        guard mode != 2, mode == 0 || prompt.isAlert else { return }
        do {
            try session.setCategory(.playback, mode: .voicePrompt, options: [.duckOthers, .interruptSpokenAudioAndMixWithOthers])
            try session.setActive(true)
        } catch {
            log.error("Audio session: \(error.localizedDescription, privacy: .public)")
        }
        if !prompt.isAlert && synthesizer.isSpeaking { synthesizer.stopSpeaking(at: .immediate) }
        let utterance = AVSpeechUtterance(string: prompt.text)
        utterance.voice = AVSpeechSynthesisVoice(language: AppLocale.tag) ?? AVSpeechSynthesisVoice(language: AppLocale.current.language.languageCode?.identifier)
        utterance.prefersAssistiveTechnologySettings = true
        synthesizer.speak(utterance)
        log.info("Speaking: \(prompt.text, privacy: .public)")
    }

    func stop() {
        synthesizer.stopSpeaking(at: .immediate)
        deactivate()
    }

    private func deactivate() {
        guard !synthesizer.isSpeaking else { return }
        try? session.setActive(false, options: .notifyOthersOnDeactivation)
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor in self.deactivate() }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        Task { @MainActor in self.deactivate() }
    }
}
