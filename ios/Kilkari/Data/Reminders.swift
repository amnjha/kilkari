import Foundation
import SwiftData
import UserNotifications

/// How often a reminder comes round.
enum Cadence: String, Codable, CaseIterable, Identifiable {
    case daily, weekly

    var id: String { rawValue }
    var label: String { self == .daily ? "Every day" : "Every week" }
}

/// Something the parent wants to be told about.
///
/// Vitamin drops at eight, tummy time after the nap. Scheduled with the system rather than
/// by the app staying awake, so it fires whether or not Kilkari has been opened.
@Model
final class Reminder {
    var title: String
    /// Minutes past midnight, so the time survives a timezone change as a time of day rather
    /// than as an instant.
    var minuteOfDay: Int
    var cadenceRaw: String
    /// 1 = Sunday, matching Calendar's weekday numbering.
    var weekday: Int
    var enabled: Bool

    init(title: String, minuteOfDay: Int, cadence: Cadence = .daily, weekday: Int = 1, enabled: Bool = true) {
        self.title = title
        self.minuteOfDay = minuteOfDay
        self.cadenceRaw = cadence.rawValue
        self.weekday = weekday
        self.enabled = enabled
    }

    var cadence: Cadence { Cadence(rawValue: cadenceRaw) ?? .daily }

    /// A stable id so rescheduling replaces the pending request rather than adding another.
    var requestId: String { "kilkari.reminder.\(persistentModelID.hashValue)" }

    var timeLabel: String {
        let components = DateComponents(hour: minuteOfDay / 60, minute: minuteOfDay % 60)
        let date = Calendar.current.date(from: components) ?? .now
        return Fmt.time(date)
    }

    var describe: String {
        switch cadence {
        case .daily: return "Every day · \(timeLabel)"
        case .weekly:
            let day = Calendar.current.weekdaySymbols[max(0, min(6, weekday - 1))]
            return "\(day)s · \(timeLabel)"
        }
    }
}

/// Talking to the system's notification centre.
///
/// Everything here is best-effort and silent on failure by design — except the permission
/// itself, which the screen shows the state of. A reminder that never arrives looks like an
/// app that does not send reminders, so the one thing worth surfacing is whether permission
/// was ever granted.
enum Notifications {

    static func requestPermission() async -> Bool {
        (try? await UNUserNotificationCenter.current()
            .requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    static func authorized() async -> Bool {
        await UNUserNotificationCenter.current().notificationSettings()
            .authorizationStatus == .authorized
    }

    /// Replaces every scheduled reminder with what is currently enabled.
    ///
    /// Rescheduling the lot rather than tracking deltas: there are a handful of these, the
    /// system tolerates it, and a delta that goes wrong leaves a notification firing for a
    /// reminder the parent deleted.
    static func reschedule(_ reminders: [Reminder]) async {
        let centre = UNUserNotificationCenter.current()
        let stale = await centre.pendingNotificationRequests()
            .map(\.identifier)
            .filter { $0.hasPrefix("kilkari.reminder.") }
        centre.removePendingNotificationRequests(withIdentifiers: stale)

        for reminder in reminders where reminder.enabled {
            var components = DateComponents()
            components.hour = reminder.minuteOfDay / 60
            components.minute = reminder.minuteOfDay % 60
            if reminder.cadence == .weekly { components.weekday = reminder.weekday }

            let content = UNMutableNotificationContent()
            content.title = reminder.title
            content.sound = .default

            let request = UNNotificationRequest(
                identifier: reminder.requestId,
                content: content,
                trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
            )
            try? await centre.add(request)
        }
    }
}
