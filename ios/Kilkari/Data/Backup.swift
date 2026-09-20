import Foundation
import SwiftData
import KilkariCore

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
                // The sign is this app's business; the format spells the direction out and
                // keeps the amount positive, the way the other one has always written it.
                ["note": j($0.note), "kind": $0.amount < 0 ? "withdrawal" : "deposit",
                 "amount": abs($0.amount), "date": day($0.date)]
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

        // The record too: a PDF that silently stops at the fold is the failure worth catching,
        // and only a real render can say how many pages came out.
        if let baby = (try? context.fetch(FetchDescriptor<Baby>()))?.first {
            let doses = (try? context.fetch(FetchDescriptor<VaccineDose>())) ?? []
            let pdf = VaccinationRecord.pdf(
                babyName: baby.name,
                dob: baby.dob,
                scheduleName: VaccineSchedules.byId(Preferences.shared.scheduleId).name,
                groups: VaccinePlan.groups(for: baby, doses: doses)
            )
            try? pdf.write(to: folder.appendingPathComponent("check.pdf"))
        }
        #endif
    }
}

// MARK: - Reading one back

extension Backup {

    /// What a file turned out to contain, before anything is done with it.
    struct Preview {
        let writtenBy: String
        let writtenAt: String
        let babyName: String?
        let counts: [(String, Int)]
        var total: Int { counts.reduce(0) { $0 + $1.1 } }
    }

    enum ImportError: LocalizedError {
        case unreadable
        case notABackup
        case newerVersion(Int)

        var errorDescription: String? {
            switch self {
            case .unreadable: return "That file could not be read as JSON."
            case .notABackup: return "That is not a Kilkari backup."
            case .newerVersion(let v):
                return "That backup is version \(v), written by a newer Kilkari than this one."
            }
        }
    }

    static let formatVersion = 1

    /// Reads a file far enough to say what is in it, without touching the store.
    ///
    /// Nothing is imported until the parent has seen this and said yes: a restore replaces
    /// everything, and "everything" should be a number they have read rather than a promise.
    static func preview(_ data: Data) throws -> (Preview, [String: Any]) {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw ImportError.unreadable
        }
        guard root["format"] as? String == "kilkari-backup" else { throw ImportError.notABackup }
        let version = root["version"] as? Int ?? 0
        guard version <= formatVersion else { throw ImportError.newerVersion(version) }

        let arrays = [
            "logEntries", "doses", "growth", "expenses", "deposits", "investments",
            "appointments", "milestones", "events", "albums", "doctors", "reminders", "paperwork",
        ]
        let counts = arrays.compactMap { key -> (String, Int)? in
            guard let rows = root[key] as? [[String: Any]], !rows.isEmpty else { return nil }
            return (label(key), rows.count)
        }
        return (
            Preview(
                writtenBy: root["writtenBy"] as? String ?? "unknown",
                writtenAt: root["writtenAt"] as? String ?? "",
                babyName: (root["baby"] as? [String: Any])?["name"] as? String,
                counts: counts
            ),
            root
        )
    }

    private static func label(_ key: String) -> String {
        switch key {
        case "logEntries": return "Log entries"
        case "doses": return "Doses given"
        case "growth": return "Measurements"
        case "expenses": return "Expenses"
        case "deposits": return "Deposits"
        case "investments": return "Holdings"
        case "appointments": return "Appointments"
        case "milestones": return "Moments"
        case "events": return "Dates"
        case "albums": return "Albums"
        case "doctors": return "Doctors"
        case "reminders": return "Reminders"
        case "paperwork": return "Paperwork"
        default: return key
        }
    }

    /// Replaces everything in the store with what the file holds.
    ///
    /// A restore, not a merge. Merging two histories of the same baby produces duplicate feeds
    /// nobody can tell apart, and the parent asked to put a backup back, not to add one to
    /// what is already here. The screen says so plainly before this runs.
    @MainActor
    static func restore(_ root: [String: Any], into context: ModelContext,
                        prefs: Preferences = .shared) {
        let iso = ISO8601DateFormatter()
        func date(_ any: Any?) -> Date? { (any as? String).flatMap(iso.date(from:)) }
        func rows(_ key: String) -> [[String: Any]] { root[key] as? [[String: Any]] ?? [] }

        // Everything goes first, so a restore cannot leave half of one history beside half of
        // another. SwiftData has no truncate, so each type is fetched and deleted.
        func wipe<T: PersistentModel>(_ type: T.Type) {
            ((try? context.fetch(FetchDescriptor<T>())) ?? []).forEach(context.delete)
        }
        wipe(Baby.self); wipe(LogEntry.self); wipe(VaccineDose.self); wipe(GrowthRecord.self)
        wipe(Expense.self); wipe(FundDeposit.self); wipe(FundAccount.self)
        wipe(Investment.self); wipe(Appointment.self)
        wipe(Milestone.self); wipe(CalendarEvent.self); wipe(Album.self); wipe(Doctor.self)
        wipe(Reminder.self); wipe(PaperworkRecord.self)

        if let settings = root["settings"] as? [String: Any] {
            if let code = settings["currency"] as? String,
               let currency = Currency(rawValue: code) { prefs.currency = currency }
            if let metric = settings["metric"] as? Bool { prefs.metric = metric }
            if let schedule = settings["scheduleId"] as? String { prefs.scheduleId = schedule }
        }

        if let baby = root["baby"] as? [String: Any],
           let name = baby["name"] as? String,
           let dob = date(baby["dob"]) {
            context.insert(Baby(name: name, dob: dob, sexRaw: baby["sex"] as? String))
        }

        for row in rows("logEntries") {
            guard let kind = (row["kind"] as? String).flatMap(LogKind.init(rawValue:)),
                  let startAt = date(row["startAt"]) else { continue }
            context.insert(LogEntry(
                kind: kind, startAt: startAt, endAt: date(row["endAt"]),
                amount: row["amount"] as? Int, side: row["side"] as? String,
                feedType: (row["feedType"] as? String).flatMap(FeedType.init(rawValue:)),
                diaperKind: (row["diaperKind"] as? String).flatMap(DiaperKind.init(rawValue:)),
                note: row["note"] as? String
            ))
        }
        for row in rows("doses") {
            guard let group = row["group"] as? String, let vaccine = row["vaccine"] as? String,
                  let givenOn = date(row["givenOn"]) else { continue }
            context.insert(VaccineDose(groupLabel: group, vaccineName: vaccine, givenOn: givenOn))
        }
        for row in rows("growth") {
            guard let on = date(row["date"]) else { continue }
            context.insert(GrowthRecord(date: on, weightKg: row["weightKg"] as? Double,
                                        lengthCm: row["lengthCm"] as? Double,
                                        headCm: row["headCm"] as? Double))
        }
        for row in rows("expenses") {
            guard let title = row["title"] as? String, let amount = row["amount"] as? Int,
                  let on = date(row["date"]) else { continue }
            context.insert(Expense(
                title: title, vendor: row["vendor"] as? String,
                category: (row["category"] as? String).flatMap(ExpenseCategory.init(rawValue:)) ?? .general,
                amount: amount, date: on, paidFromFund: row["paidFromFund"] as? Bool ?? true
            ))
        }
        for row in rows("deposits") {
            guard let amount = row["amount"] as? Int, let on = date(row["date"]) else { continue }
            // A file written before the field existed holds only deposits, which is what the
            // absent value means.
            let out = row["kind"] as? String == "withdrawal"
            context.insert(FundDeposit(note: row["note"] as? String,
                                       amount: out ? -abs(amount) : abs(amount), date: on))
        }
        for row in rows("investments") {
            guard let name = row["name"] as? String, let started = date(row["startedOn"]) else { continue }
            context.insert(Investment(
                name: name,
                kind: (row["kind"] as? String).flatMap(InvestmentKind.init(rawValue:)) ?? .other,
                investedAmount: row["invested"] as? Int ?? 0,
                monthlyAmount: row["monthly"] as? Int,
                ratePercent: row["rate"] as? Double,
                startedOn: started, maturesOn: date(row["maturesOn"]),
                currentValue: row["value"] as? Int, valuedOn: date(row["valuedOn"])
            ))
        }
        for row in rows("appointments") {
            guard let title = row["title"] as? String, let at = date(row["startAt"]) else { continue }
            context.insert(Appointment(title: title, who: row["who"] as? String, startAt: at))
        }
        for row in rows("milestones") {
            guard let title = row["title"] as? String, let on = date(row["date"]) else { continue }
            context.insert(Milestone(title: title, note: row["note"] as? String, date: on))
        }
        for row in rows("events") {
            guard let title = row["title"] as? String, let on = date(row["date"]) else { continue }
            context.insert(CalendarEvent(title: title, note: row["note"] as? String,
                                         date: on, annual: row["annual"] as? Bool ?? true))
        }
        for row in rows("albums") {
            guard let title = row["title"] as? String, let url = row["url"] as? String else { continue }
            context.insert(Album(title: title, note: row["note"] as? String, url: url))
        }
        for row in rows("doctors") {
            guard let name = row["name"] as? String else { continue }
            context.insert(Doctor(name: name, speciality: row["speciality"] as? String,
                                  clinic: row["clinic"] as? String, phone: row["phone"] as? String))
        }
        var restoredReminders: [Reminder] = []
        for row in rows("reminders") {
            guard let title = row["title"] as? String,
                  let minute = row["minuteOfDay"] as? Int else { continue }
            let reminder = Reminder(
                title: title, minuteOfDay: minute,
                cadence: (row["cadence"] as? String).flatMap(Cadence.init(rawValue:)) ?? .daily,
                weekday: row["weekday"] as? Int ?? 1,
                enabled: row["enabled"] as? Bool ?? true
            )
            context.insert(reminder)
            restoredReminders.append(reminder)
        }
        for row in rows("paperwork") {
            guard let key = row["key"] as? String else { continue }
            context.insert(PaperworkRecord(key: key, obtainedOn: date(row["obtainedOn"]),
                                           skipped: row["skipped"] as? Bool ?? false))
        }

        // The notifications belonged to the reminders that were just deleted, so they are
        // rebuilt rather than left pointing at rows that no longer exist.
        let snapshot = restoredReminders
        Task { await Notifications.reschedule(snapshot) }
    }
}

extension Backup {

    /// Export, wipe, restore, and write down what survived.
    ///
    /// The only honest test of an importer is whether a store put through it comes back the
    /// same. Debug builds only, reached with `--roundtrip-check`; the result lands in the
    /// app's Documents folder so it can be pulled off a simulator and compared.
    @MainActor
    static func roundTripCheck(context: ModelContext) {
        #if DEBUG
        func census() -> [String: Int] {
            func n<T: PersistentModel>(_ type: T.Type) -> Int {
                ((try? context.fetch(FetchDescriptor<T>())) ?? []).count
            }
            return [
                "baby": n(Baby.self), "logEntries": n(LogEntry.self), "doses": n(VaccineDose.self),
                "growth": n(GrowthRecord.self), "expenses": n(Expense.self),
                "deposits": n(FundDeposit.self), "investments": n(Investment.self),
                "appointments": n(Appointment.self), "milestones": n(Milestone.self),
                "events": n(CalendarEvent.self), "albums": n(Album.self),
                "doctors": n(Doctor.self), "reminders": n(Reminder.self),
                "paperwork": n(PaperworkRecord.self),
            ]
        }

        func fundIn() -> Int {
            ((try? context.fetch(FetchDescriptor<FundDeposit>())) ?? []).reduce(0) { $0 + $1.amount }
        }

        let before = census()
        let fundBefore = fundIn()
        guard let exported = json(context: context),
              let (_, root) = try? preview(exported) else { return }

        restore(root, into: context)
        let after = census()

        // A couple of values as well as the counts: a restore that keeps the right number of
        // rows and loses what is in them would pass a census.
        let baby = (try? context.fetch(FetchDescriptor<Baby>()))?.first
        let result: [String: Any] = [
            "before": before,
            "after": after,
            "identical": before == after && fundBefore == fundIn(),
            "babyName": baby?.name ?? "",
            "babySex": baby?.sexRaw ?? "",
            "firstExpense": ((try? context.fetch(FetchDescriptor<Expense>())) ?? [])
                .sorted { $0.date < $1.date }.first.map { ["title": $0.title, "amount": $0.amount] } ?? [:],
            // The fund is signed, and the format is not: a withdrawal that came back as a
            // deposit would keep the row count and double the balance.
            "fundIn": fundIn(),
            "fundInBefore": fundBefore,
        ]
        let folder = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        try? JSONSerialization.data(withJSONObject: result, options: [.prettyPrinted, .sortedKeys])
            .write(to: folder.appendingPathComponent("roundtrip.json"))
        #endif
    }
}
