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
        case "insights": return .insights
        case "reminders": return .reminders
        case "paperwork": return .paperwork
        case "documents": return .documents
        case "albums": return .albums
        case "events": return .events
        case "backup": return .backup
        case "settings": return .settings
        default: return nil
        }
        #else
        return nil
        #endif
    }

    /// Which of Money's three segments to open on: `spending`, `fund` or `invest`.
    static var moneySegment: Int {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        guard let i = args.firstIndex(of: "--money"), i + 1 < args.count else { return 0 }
        return ["spending": 0, "fund": 1, "invest": 2][args[i + 1]] ?? 0
        #else
        return 0
        #endif
    }

    /// Runs the backup export straight into the app's Documents folder at launch, so the
    /// output can be pulled off a simulator and checked rather than taken on trust.
    static var exportOnLaunch: Bool {
        #if DEBUG
        return ProcessInfo.processInfo.arguments.contains("--export-check")
        #else
        return false
        #endif
    }

    /// Exports the store, wipes it, restores it, and writes down whether it came back the
    /// same. Reached with `--roundtrip-check`.
    static var roundTripCheck: Bool {
        #if DEBUG
        return ProcessInfo.processInfo.arguments.contains("--roundtrip-check")
        #else
        return false
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

        // A week of it, not a day: the insights screen averages over a window, and a single
        // day's worth in a seven-day window reads as "0.3 feeds a day", which is arithmetic
        // working correctly on data that does not represent anything.
        var entries: [LogEntry] = [
            LogEntry(kind: .medicine, startAt: ago(9), note: "Vitamin D drops · 1 drop"),
            LogEntry(kind: .tooth, startAt: ago(48)),
        ]
        for day in 0..<7 {
            let base = day * 24
            // Seven feeds, alternating sides, with a bottle in the evening.
            for (i, hour) in [2, 5, 8, 11, 14, 17, 20].enumerated() {
                let at = base + hour
                guard at > 0 else { continue }
                entries.append(
                    hour == 20
                    ? LogEntry(kind: .feed, startAt: ago(at), amount: 90, feedType: .bottle)
                    : LogEntry(kind: .feed, startAt: ago(at), amount: 12 + i,
                               side: i.isMultiple(of: 2) ? "L" : "R", feedType: .breast)
                )
            }
            for hour in [3, 7, 12, 16, 21] where base + hour > 0 {
                entries.append(LogEntry(kind: .diaper, startAt: ago(base + hour),
                                        diaperKind: hour.isMultiple(of: 2) ? .wet : .both))
            }
            // A long night and two daytime naps.
            for (from, to) in [(base + 23, base + 16), (base + 13, base + 11), (base + 6, base + 4)]
            where from > 0 && to > 0 {
                entries.append(LogEntry(kind: .sleep, startAt: ago(from), endAt: ago(to)))
            }
        }
        entries.forEach(context.insert)

        context.insert(Reminder(title: "Vitamin D drops", minuteOfDay: 8 * 60))
        context.insert(Reminder(title: "Tummy time", minuteOfDay: 17 * 60 + 30,
                                cadence: .weekly, weekday: 1, enabled: false))

        context.insert(Investment(
            name: "SBI fixed deposit", kind: .fd, investedAmount: 100_000,
            ratePercent: 7.1,
            startedOn: cal.date(byAdding: .month, value: -14, to: .now)!,
            maturesOn: cal.date(byAdding: .year, value: 4, to: .now)!,
            currentValue: 108_400,
            valuedOn: cal.date(byAdding: .day, value: -6, to: .now)!
        ))
        context.insert(Investment(
            name: "Index fund SIP", kind: .sip, investedAmount: 0, monthlyAmount: 5_000,
            ratePercent: 12,
            startedOn: cal.date(byAdding: .month, value: -10, to: .now)!,
            maturesOn: nil,
            currentValue: 58_900,
            valuedOn: cal.date(byAdding: .day, value: -6, to: .now)!
        ))

        context.insert(Doctor(name: "Dr Meera Nair", speciality: "Paediatrician",
                              clinic: "Rainbow Clinic", phone: "+91 98450 11223"))
        context.insert(Album(title: "First month", note: "184 photos",
                             url: "https://photos.app.goo.gl/example"))
        context.insert(CalendarEvent(title: "Diwali", note: "First one",
                                     date: cal.date(from: DateComponents(year: 2026, month: 11, day: 8))!))

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
