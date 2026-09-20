import Foundation
import SwiftData

/// A populated store, for looking at screens that only exist once there is data.
///
/// Reached with `--sample-data` on the launch arguments, and compiled out of release builds
/// entirely. An empty app is easy to check and tells you nothing: most of what can go wrong
/// in a layout — a name that wraps, a list that scrolls, a tile whose detail line runs long —
/// only shows up once something is in it.
///
///     xcrun simctl launch booted com.kilkari --sample-data
enum SampleData {

    static var requested: Bool {
        #if DEBUG
        return ProcessInfo.processInfo.arguments.contains("--sample-data")
        #else
        return false
        #endif
    }

    /// Which tab to open on, so a screenshot of any one of them is one launch away:
    ///
    ///     xcrun simctl launch booted com.kilkari --sample-data --tab log
    static var initialTab: Tab? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        guard let i = args.firstIndex(of: "--tab"), i + 1 < args.count else { return nil }
        return Tab(rawValue: args[i + 1])
        #else
        return nil
        #endif
    }

    /// A screen to push straight away, so a pushed screen is one launch away too:
    ///
    ///     xcrun simctl launch booted com.kilkari --sample-data --tab health --route growth
    static var initialRoute: Route? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        guard let i = args.firstIndex(of: "--route"), i + 1 < args.count else { return nil }
        switch args[i + 1] {
        case "vaccines": return .vaccines
        case "growth": return .growth
        case "teeth": return .teeth
        case "meds": return .meds
        case "appointments": return .appointments
        case "doctors": return .doctors
        case "timeline": return .timeline
        case "settings": return .settings
        default: return nil
        }
        #else
        return nil
        #endif
    }

    @MainActor
    static func seed(into context: ModelContext) {
        let existing = (try? context.fetch(FetchDescriptor<Baby>())) ?? []
        guard existing.isEmpty else { return }

        let cal = Calendar.current
        let dob = cal.date(byAdding: .day, value: -17, to: .now)!
        context.insert(Baby(name: "Ira", dob: dob, sexRaw: "girl"))

        func ago(_ hours: Int, _ minutes: Int = 0) -> Date {
            .now.addingTimeInterval(TimeInterval(-(hours * 3600 + minutes * 60)))
        }

        let entries: [LogEntry] = [
            LogEntry(kind: .feed, startAt: ago(1, 20), amount: 14, side: "L", feedType: .breast),
            LogEntry(kind: .diaper, startAt: ago(2, 5), diaperKind: .wet),
            LogEntry(kind: .sleep, startAt: ago(4), endAt: ago(2, 30)),
            LogEntry(kind: .feed, startAt: ago(5), amount: 90, feedType: .bottle),
            LogEntry(kind: .diaper, startAt: ago(6, 40), diaperKind: .both),
            LogEntry(kind: .medicine, startAt: ago(9), note: "Vitamin D drops · 1 drop"),
            LogEntry(kind: .tooth, startAt: ago(48)),
        ]
        entries.forEach(context.insert)

        // The birth group given, the six-week one still outstanding, so both halves of the
        // vaccine screen have something to show.
        ["BCG", "OPV-0", "Hep B-1"].forEach {
            context.insert(VaccineDose(groupLabel: "Birth", vaccineName: $0,
                                       givenOn: cal.date(byAdding: .day, value: -16, to: .now)!))
        }

        [(-16, 3.24), (-9, 3.41), (-2, 3.68)].forEach { days, kg in
            context.insert(GrowthRecord(date: cal.date(byAdding: .day, value: days, to: .now)!,
                                        weightKg: kg,
                                        lengthCm: 49 + Double(16 + days) * 0.1))
        }

        context.insert(FundDeposit(note: "Monthly transfer", amount: 15_000,
                                   date: cal.date(byAdding: .day, value: -12, to: .now)!))
        [
            ("Nappies, pack of 72", "Local pharmacy", ExpenseCategory.general, 1_899, -11),
            ("Paediatric visit", "Rainbow Clinic", ExpenseCategory.medical, 800, -9),
            ("Baby clothes", "Market", ExpenseCategory.general, 1_450, -5),
            ("Birth vaccines", "Rainbow Clinic", ExpenseCategory.medical, 1_200, -2),
        ].forEach { title, vendor, category, amount, days in
            context.insert(Expense(title: title, vendor: vendor, category: category,
                                   amount: amount,
                                   date: cal.date(byAdding: .day, value: days, to: .now)!))
        }

        context.insert(Appointment(title: "Six-week check", who: "Dr Nair",
                                   startAt: cal.date(byAdding: .day, value: 25, to: .now)!))
        context.insert(Milestone(title: "First smile", note: "At the ceiling fan, of course",
                                 date: cal.date(byAdding: .day, value: -3, to: .now)!))
    }
}
