import Foundation
import ActivityKit

/// Séance en cours (Live Activity) : données envoyées par l'app (SessionActivity côté Kotlin).
struct SessionPayload: Codable, Hashable {
    var title: String
    var line: String
    var load: String
    var done: Int
    var total: Int
    var startMs: Int64
    var restEndMs: Int64?
    var paused: Bool
    var restLabel: String
    var pausedLabel: String

    var start: Date { Date(timeIntervalSince1970: Double(startMs) / 1000) }
    var restEnd: Date? { restEndMs.map { Date(timeIntervalSince1970: Double($0) / 1000) } }
}

struct WorkoutAttributes: ActivityAttributes {
    typealias ContentState = SessionPayload
    var name: String
}
