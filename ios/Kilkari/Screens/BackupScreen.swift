import SwiftUI
import SwiftData
import UniformTypeIdentifiers

/// What a backup contains, and the two ways to take one.
///
/// The writing itself lives in [Backup], which works from a `ModelContext` rather than from
/// this screen's queries — that is what lets the output be checked outside a rendered view.
/// Photographs and scanned pages are not included: they are large, they already live in the
/// photo library and the app's own store, and a parent should not believe a 40KB file is
/// their whole album.
struct BackupScreen: View {
    let baby: Baby

    @Environment(\.accent) private var accent
    @Environment(\.modelContext) private var context
    @State private var prefs = Preferences.shared

    @Query private var entries: [LogEntry]
    @Query private var doses: [VaccineDose]
    @Query private var growth: [GrowthRecord]
    @Query private var expenses: [Expense]
    @Query private var deposits: [FundDeposit]
    @Query private var appointments: [Appointment]
    @Query private var milestones: [Milestone]
    @Query private var doctors: [Doctor]
    @Query private var reminders: [Reminder]

    @State private var exported: ExportFile?
    @State private var failure: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                KCard(background: accent.wash.onCream(0.5), border: nil) {
                    VStack(alignment: .leading, spacing: 6) {
                        Label("Everything stays on this phone", systemImage: "lock.fill")
                            .font(KFont.sans(15, .bold)).foregroundStyle(KC.ink)
                        Text("No account, no server, no analytics. A backup is a file you keep, wherever you choose to put it.")
                            .font(KFont.sans(13)).foregroundStyle(KC.mutedStrong)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(16)
                }

                SectionLabel("What is in here")
                KCard {
                    VStack(spacing: 0) {
                        ForEach(Array(counts.enumerated()), id: \.offset) { i, row in
                            HStack {
                                Text(row.0).font(KFont.sans(14)).foregroundStyle(KC.ink)
                                Spacer()
                                Text("\(row.1)").font(KFont.sans(14, .semibold)).foregroundStyle(KC.mutedStrong)
                            }
                            .padding(.horizontal, 14).padding(.vertical, 10)
                            if i != counts.count - 1 { Rectangle().fill(KC.divider).frame(height: 1) }
                        }
                    }
                }

                SectionLabel("Take a copy").padding(.top, 4)
                Button { exportJSON() } label: {
                    row("Everything, as JSON", "Readable without Kilkari. The format is documented in the repo.", "doc.badge.arrow.up")
                }
                .buttonStyle(.plain)

                Button { exportCSV() } label: {
                    row("Spending, as CSV", "Opens in any spreadsheet.", "tablecells")
                }
                .buttonStyle(.plain)

                Text("Photographs and scanned pages are not included. They are large, and they already live in the photo library and in the app's own storage.")
                    .font(KFont.sans(12)).foregroundStyle(KC.muted)
                    .padding(.horizontal, 4)
                    .fixedSize(horizontal: false, vertical: true)

                if let failure {
                    Text(failure).font(KFont.sans(13)).foregroundStyle(KC.danger).padding(.horizontal, 4)
                }
            }
            .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 110)
        }
        .sheet(item: $exported) { file in
            ShareSheet(items: [file.url])
        }
    }

    private var counts: [(String, Int)] {
        [
            ("Log entries", entries.count),
            ("Doses given", doses.count),
            ("Measurements", growth.count),
            ("Expenses", expenses.count),
            ("Deposits", deposits.count),
            ("Appointments", appointments.count),
            ("Moments", milestones.count),
            ("Doctors", doctors.count),
            ("Reminders", reminders.count),
        ]
    }

    private func row(_ title: String, _ subtitle: String, _ symbol: String) -> some View {
        KCard {
            HStack(spacing: 12) {
                IconBadge(symbol: symbol, tint: accent.deep, background: accent.bg,
                          size: 40, corner: 12, iconSize: 18)
                VStack(alignment: .leading, spacing: 1) {
                    Text(title).font(KFont.sans(15, .semibold)).foregroundStyle(KC.ink)
                    Text(subtitle).font(KFont.sans(12)).foregroundStyle(KC.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer()
                Image(systemName: "square.and.arrow.up")
                    .font(.system(size: 15, weight: .semibold)).foregroundStyle(KC.stoneLight)
            }
            .padding(14)
        }
    }

    /// The document as JSONSerialization wants it.
    ///
    /// Optionals cannot go in directly: `nil as Any` is an Optional, not a null, and
    /// JSONSerialization refuses the whole document rather than that one field. Everything
    /// that can be absent goes through [j], which turns it into a real null.
    private func backupDocument() -> [String: Any] {
        let iso = ISO8601DateFormatter()
        func day(_ date: Date) -> String { iso.string(from: date) }
        func j<T>(_ value: T?) -> Any { value ?? NSNull() }

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
            "baby": [
                "name": baby.name,
                "dob": day(baby.dob),
                "sex": j(baby.sexRaw),
            ],
            "logEntries": entries.map {
                [
                    "kind": $0.kindRaw, "startAt": day($0.startAt),
                    "endAt": j($0.endAt.map(day)), "amount": j($0.amount),
                    "side": j($0.side), "feedType": j($0.feedTypeRaw),
                    "diaperKind": j($0.diaperKindRaw), "note": j($0.note),
                ]
            },
            "doses": doses.map {
                ["group": $0.groupLabel, "vaccine": $0.vaccineName, "givenOn": day($0.givenOn)]
            },
            "growth": growth.map {
                ["date": day($0.date), "weightKg": j($0.weightKg),
                 "lengthCm": j($0.lengthCm), "headCm": j($0.headCm)]
            },
            "expenses": expenses.map {
                ["title": $0.title, "vendor": j($0.vendor), "category": $0.categoryRaw,
                 "amount": $0.amount, "date": day($0.date), "paidFromFund": $0.paidFromFund]
            },
            "deposits": deposits.map {
                ["note": j($0.note), "amount": $0.amount, "date": day($0.date)]
            },
            "appointments": appointments.map {
                ["title": $0.title, "who": j($0.who), "startAt": day($0.startAt)]
            },
            "milestones": milestones.map {
                ["title": $0.title, "note": j($0.note), "date": day($0.date)]
            },
            "doctors": doctors.map {
                ["name": $0.name, "speciality": j($0.speciality),
                 "clinic": j($0.clinic), "phone": j($0.phone)]
            },
            "reminders": reminders.map {
                ["title": $0.title, "minuteOfDay": $0.minuteOfDay,
                 "cadence": $0.cadenceRaw, "weekday": $0.weekday, "enabled": $0.enabled]
            },
        ]
    }

    private func exportJSON() {
        write(name: "kilkari-\(stamp()).json", data: Backup.json(context: context))
    }

    private func exportCSV() {
        write(name: "kilkari-spending-\(stamp()).csv",
              data: Backup.spendingCSV(context: context).data(using: .utf8))
    }

    private func stamp() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: .now)
    }

    /// Written to a temporary file and handed to the share sheet, so the parent chooses where
    /// it lands — Files, Drive, a mail to themselves — rather than the app deciding.
    private func write(name: String, data: Data?) {
        guard let data else {
            failure = "That export could not be written."
            return
        }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        do {
            try data.write(to: url, options: .atomic)
            failure = nil
            exported = ExportFile(url: url)
        } catch {
            failure = "Could not write the file: \(error.localizedDescription)"
        }
    }
}

private struct ExportFile: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

/// The system share sheet, which is how a file leaves an iOS app.
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
