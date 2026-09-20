import Foundation
import SwiftData

/// Everything the app holds, as one readable file.
///
/// Android's backup is a zip around its SQLite database, which is Room's file and means
/// nothing to SwiftData. Rather than pretend otherwise this writes a plain JSON interchange
/// format: one array per kind of record, readable without Kilkari at all, and documented in
/// `common/BACKUP.md` so the Android side can learn to read and write it too.
///
/// Deliberately not a View. Building it from a `ModelContext` rather than from `@Query`
/// properties is what makes it runnable outside a rendered screen — which is how the output
/// gets checked rather than taken on trust.
enum Backup {

    /// The whole store as a JSON-serialisable dictionary.
    static func document(context: ModelContext, prefs: Preferences = .shared) -> [String: Any] {
        let iso = ISO8601DateFormatter()
        func day(_ date: Date) -> String { iso.string(from: date) }
        // Optionals cannot go in directly: `nil as Any` is an Optional, not a null, and
        // JSONSerialization refuses the whole document rather than that one field.
        func j<T>(_ value: T?) -> Any { value ?? NSNull() }
        func all<T: PersistentModel>(_ type: T.Type) -> [T] {
            (try? context.fetch(FetchDescriptor<T>())) ?? []
        }

        let baby = all(Baby.self).first

        return [
            "format": "kilkari-backup",
            "version": 1,
            "writtenBy": "ios",
            "writtenAt": day(.now),
            "settings": [
                "currency": prefs.currency.code,
                "metric": prefs.metric,
                "scheduleId": prefs.scheduleId,
            ],
            "baby": baby.map {
                ["name": $0.name, "dob": day($0.dob), "sex": j($0.sexRaw)]
            } ?? [:],
            "logEntries": all(LogEntry.self).map {
                [
                    "kind": $0.kindRaw, "startAt": day($0.startAt),
                    "endAt": j($0.endAt.map(day)), "amount": j($0.amount),
                    "side": j($0.side), "feedType": j($0.feedTypeRaw),
                    "diaperKind": j($0.diaperKindRaw), "note": j($0.note),
                ]
            },
            "doses": all(VaccineDose.self).map {
                ["group": $0.groupLabel, "vaccine": $0.vaccineName, "givenOn": day($0.givenOn)]
            },
            "growth": all(GrowthRecord.self).map {
                ["date": day($0.date), "weightKg": j($0.weightKg),
                 "lengthCm": j($0.lengthCm), "headCm": j($0.headCm)]
            },
            "expenses": all(Expense.self).map {
                ["title": $0.title, "vendor": j($0.vendor), "category": $0.categoryRaw,
                 "amount": $0.amount, "date": day($0.date), "paidFromFund": $0.paidFromFund]
            },
            "deposits": all(FundDeposit.self).map {
                ["note": j($0.note), "amount": $0.amount, "date": day($0.date)]
            },
            "investments": all(Investment.self).map {
                ["name": $0.name, "kind": $0.kindRaw, "invested": $0.investedAmount,
                 "monthly": j($0.monthlyAmount), "rate": j($0.ratePercent),
                 "startedOn": day($0.startedOn), "maturesOn": j($0.maturesOn.map(day)),
                 "value": j($0.currentValue), "valuedOn": j($0.valuedOn.map(day))]
            },
            "appointments": all(Appointment.self).map {
                ["title": $0.title, "who": j($0.who), "startAt": day($0.startAt)]
            },
            "milestones": all(Milestone.self).map {
                ["title": $0.title, "note": j($0.note), "date": day($0.date)]
            },
            "events": all(CalendarEvent.self).map {
                ["title": $0.title, "note": j($0.note), "date": day($0.date), "annual": $0.annual]
            },
            "albums": all(Album.self).map {
                ["title": $0.title, "note": j($0.note), "url": $0.url]
            },
            "doctors": all(Doctor.self).map {
                ["name": $0.name, "speciality": j($0.speciality),
                 "clinic": j($0.clinic), "phone": j($0.phone)]
            },
            "reminders": all(Reminder.self).map {
                ["title": $0.title, "minuteOfDay": $0.minuteOfDay,
                 "cadence": $0.cadenceRaw, "weekday": $0.weekday, "enabled": $0.enabled]
            },
            "paperwork": all(PaperworkRecord.self).map {
                ["key": $0.key, "obtainedOn": j($0.obtainedOn.map(day)), "skipped": $0.skipped]
            },
        ]
    }

    static func json(context: ModelContext, prefs: Preferences = .shared) -> Data? {
        try? JSONSerialization.data(withJSONObject: document(context: context, prefs: prefs),
                                    options: [.prettyPrinted, .sortedKeys])
    }

    /// Spending as a spreadsheet. Only the expenses: a CSV of everything would be a CSV of
    /// nothing in particular.
    static func spendingCSV(context: ModelContext, prefs: Preferences = .shared) -> String {
        func escape(_ field: String) -> String {
            field.contains(where: { $0 == "," || $0 == "\"" || $0 == "\n" })
                ? "\"" + field.replacingOccurrences(of: "\"", with: "\"\"") + "\""
                : field
        }
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withFullDate]
        let expenses = ((try? context.fetch(FetchDescriptor<Expense>())) ?? [])
            .sorted { $0.date < $1.date }

        var lines = ["date,title,vendor,category,amount,currency,paid_from_fund"]
        for expense in expenses {
            lines.append([
                iso.string(from: expense.date),
                escape(expense.title),
                escape(expense.vendor ?? ""),
                expense.category.rawValue,
                String(expense.amount),
                prefs.currency.code,
                expense.paidFromFund ? "yes" : "no",
            ].joined(separator: ","))
        }
        return lines.joined(separator: "\n")
    }

    /// Writes both into the app's Documents folder so they can be pulled off a simulator and
    /// checked. Debug builds only, reached with `--export-check`.
    static func writeForChecking(context: ModelContext) {
        #if DEBUG
        let folder = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        try? json(context: context)?.write(to: folder.appendingPathComponent("check.json"))
        try? spendingCSV(context: context).data(using: .utf8)?
            .write(to: folder.appendingPathComponent("check.csv"))
        #endif
    }
}
