import Foundation

/// Dates, ages and durations, written the way a tired parent reads them.
///
/// The Android app's `Formatting.kt` says the same things; these are the ones the screens
/// built so far need. "3 h 12 m ago" rather than "3.2 hours" because that is what someone
/// answers when a nurse asks when the last feed was.
enum Fmt {

    private static var cal: Calendar { Calendar.current }

    /// "4 weeks 3 days", "7 months", "2 years 1 month" — the same steps the Android app uses,
    /// because a two-week-old's age in months is a useless number.
    static func age(from dob: Date, to now: Date = .now) -> String {
        let days = cal.dateComponents([.day], from: cal.startOfDay(for: dob),
                                      to: cal.startOfDay(for: now)).day ?? 0
        if days < 0 { return "not yet born" }
        if days < 14 { return days == 1 ? "1 day" : "\(days) days" }
        if days < 61 {
            let weeks = days / 7
            let rest = days % 7
            let w = "\(weeks) week" + (weeks == 1 ? "" : "s")
            return rest == 0 ? w : "\(w) \(rest) day" + (rest == 1 ? "" : "s")
        }
        let parts = cal.dateComponents([.year, .month], from: dob, to: now)
        let years = parts.year ?? 0
        let months = parts.month ?? 0
        if years == 0 { return "\(months) month" + (months == 1 ? "" : "s") }
        let y = "\(years) year" + (years == 1 ? "" : "s")
        return months == 0 ? y : "\(y) \(months) month" + (months == 1 ? "" : "s")
    }

    /// "3 h 12 m ago", or a dash when nothing has been logged yet.
    static func ago(_ date: Date?, now: Date = .now) -> String {
        guard let date else { return "—" }
        return elapsed(from: date, to: now) + " ago"
    }

    /// "3 h 12 m", "48 m", "2 d".
    static func elapsed(from: Date, to: Date = .now) -> String {
        let seconds = max(0, Int(to.timeIntervalSince(from)))
        let minutes = seconds / 60
        if minutes < 60 { return "\(minutes) m" }
        let hours = minutes / 60
        if hours < 24 { return "\(hours) h \(minutes % 60) m" }
        let days = hours / 24
        return "\(days) d \(hours % 24) h"
    }

    /// "18:04", in whatever the phone is set to.
    static func time(_ date: Date) -> String {
        date.formatted(date: .omitted, time: .shortened)
    }

    /// "6 Sep", and the year too when it is not this one.
    static func date(_ date: Date, now: Date = .now) -> String {
        let sameYear = cal.component(.year, from: date) == cal.component(.year, from: now)
        let base = date.formatted(.dateTime.day().month(.abbreviated))
        return sameYear ? base : base + " " + String(cal.component(.year, from: date))
    }

    /// Plural without the parenthesis: "1 dose", "3 doses".
    static func plural(_ count: Int, _ word: String) -> String {
        count == 1 ? word : word + "s"
    }
}
