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

    @MainActor
    static func seed(into context: ModelContext) {
        let existing = (try? context.fetch(FetchDescriptor<Baby>())) ?? []
        guard existing.isEmpty else { return }

        let cal = Calendar.current
        let dob = cal.date(byAdding: .day, value: -17, to: .now)!
        context.insert(Baby(name: "Ira", dob: dob))

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
        ]
        entries.forEach(context.insert)
    }
}
