import SwiftUI
import SwiftData

/// Twenty teeth, in the order they usually arrive.
///
/// Each one is tapped when it comes through, and the chart is the record. "Usually" is doing
/// work: the spread is months wide and a late tooth is not a problem, so the screen gives a
/// rough window rather than a date to miss.
struct TeethScreen: View {
    @Environment(\.modelContext) private var context
    @Environment(\.accent) private var accent
    @Query(sort: \LogEntry.startAt) private var entries: [LogEntry]

    /// Name and the month range it typically appears in.
    private static let chart: [(String, ClosedRange<Int>)] = [
        ("Lower central incisor (L)", 6...10), ("Lower central incisor (R)", 6...10),
        ("Upper central incisor (L)", 8...12), ("Upper central incisor (R)", 8...12),
        ("Upper lateral incisor (L)", 9...13), ("Upper lateral incisor (R)", 9...13),
        ("Lower lateral incisor (L)", 10...16), ("Lower lateral incisor (R)", 10...16),
        ("Upper first molar (L)", 13...19), ("Upper first molar (R)", 13...19),
        ("Lower first molar (L)", 14...18), ("Lower first molar (R)", 14...18),
        ("Upper canine (L)", 16...22), ("Upper canine (R)", 16...22),
        ("Lower canine (L)", 17...23), ("Lower canine (R)", 17...23),
        ("Lower second molar (L)", 23...31), ("Lower second molar (R)", 23...31),
        ("Upper second molar (L)", 25...33), ("Upper second molar (R)", 25...33),
    ]

    private var through: [LogEntry] { entries.filter { $0.kind == .tooth } }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                KCard(background: accent.wash.onCream(0.5), border: nil) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(through.count) of 20 through")
                            .font(KFont.display(18, .bold)).foregroundStyle(KC.ink)
                        Text("The order is typical, the timing is not. Months apart either way is ordinary.")
                            .font(KFont.sans(12)).foregroundStyle(KC.mutedStrong)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(16)
                }

                KCard {
                    VStack(spacing: 0) {
                        ForEach(Array(Self.chart.enumerated()), id: \.offset) { i, tooth in
                            let done = i < through.count
                            HStack(spacing: 12) {
                                Circle()
                                    .fill(done ? accent.main : KC.surface)
                                    .frame(width: 22, height: 22)
                                    .overlay {
                                        Circle().strokeBorder(done ? accent.main : accent.ring, lineWidth: 2)
                                        if done {
                                            Image(systemName: "checkmark")
                                                .font(.system(size: 11, weight: .bold))
                                                .foregroundStyle(.white)
                                        }
                                    }
                                Text(tooth.0)
                                    .font(KFont.sans(14)).foregroundStyle(done ? KC.faint : KC.ink)
                                Spacer()
                                Text("~\(tooth.1.lowerBound)–\(tooth.1.upperBound) mo")
                                    .font(KFont.sans(12)).foregroundStyle(KC.muted)
                            }
                            .padding(.horizontal, 14).padding(.vertical, 10)
                            if i != Self.chart.count - 1 { Rectangle().fill(KC.divider).frame(height: 1) }
                        }
                    }
                }
            }
            .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 110)
        }
        .safeAreaInset(edge: .bottom) {
            PrimaryButton(label: "A tooth came through", enabled: through.count < 20) {
                context.insert(LogEntry(kind: .tooth))
            }
            .padding(.horizontal, 16).padding(.bottom, 96)
        }
    }
}

/// Doses recorded, newest first. Medicines themselves live on Android; this records the
/// giving, which is the part that gets forgotten.
struct MedsScreen: View {
    @Environment(\.accent) private var accent
    @Query(sort: \LogEntry.startAt, order: .reverse) private var entries: [LogEntry]

    private var doses: [LogEntry] { entries.filter { $0.kind == .medicine } }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if doses.isEmpty {
                    KCard {
                        VStack(spacing: 10) {
                            Image(systemName: "pills.fill")
                                .font(.system(size: 28)).foregroundStyle(accent.deep)
                                .frame(width: 92, height: 92)
                                .background(accent.wash.onCream(0.6))
                                .clipShape(RoundedRectangle(cornerRadius: 38, style: .continuous))
                            Text("Nothing being taken")
                                .font(KFont.display(17, .bold)).foregroundStyle(KC.ink)
                            Text("Record a dose from the Log tab and it shows up here.")
                                .font(KFont.sans(13)).foregroundStyle(KC.muted)
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 26).padding(.horizontal, 20)
                    }
                } else {
                    KCard {
                        VStack(spacing: 0) {
                            ForEach(Array(doses.enumerated()), id: \.element.persistentModelID) { i, dose in
                                HStack(spacing: 12) {
                                    IconBadge(symbol: "pills.fill", tint: KC.clayDeep,
                                              background: KC.clayBg, size: 36, corner: 10, iconSize: 16)
                                    Text(dose.note ?? "Dose")
                                        .font(KFont.sans(14)).foregroundStyle(KC.ink)
                                    Spacer()
                                    Text("\(Fmt.date(dose.startAt)) \(Fmt.time(dose.startAt))")
                                        .font(KFont.sans(12)).foregroundStyle(KC.muted)
                                }
                                .padding(.horizontal, 14).padding(.vertical, 11)
                                if i != doses.count - 1 { Rectangle().fill(KC.divider).frame(height: 1) }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 110)
        }
    }
}

/// Visits, upcoming first.
struct AppointmentsScreen: View {
    @Environment(\.modelContext) private var context
    @Environment(\.accent) private var accent
    @Query(sort: \Appointment.startAt) private var appointments: [Appointment]
    @State private var adding = false

    private var upcoming: [Appointment] { appointments.filter { $0.startAt >= .now } }
    private var past: [Appointment] { appointments.filter { $0.startAt < .now }.reversed() }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                SectionLabel("Upcoming")
                if upcoming.isEmpty {
                    KCard {
                        VStack(spacing: 10) {
                            Image(systemName: "stethoscope")
                                .font(.system(size: 28)).foregroundStyle(accent.deep)
                                .frame(width: 92, height: 92)
                                .background(accent.wash.onCream(0.6))
                                .clipShape(RoundedRectangle(cornerRadius: 38, style: .continuous))
                            Text("Nothing booked")
                                .font(KFont.display(17, .bold)).foregroundStyle(KC.ink)
                            Text("Add the next check-up and it waits on the home screen.")
                                .font(KFont.sans(13)).foregroundStyle(KC.muted)
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 26).padding(.horizontal, 20)
                    }
                } else {
                    ForEach(upcoming) { card($0, emphasis: $0.id == upcoming.first?.id) }
                }

                if !past.isEmpty {
                    SectionLabel("Past").padding(.top, 4)
                    ForEach(past) { card($0, emphasis: false) }
                }
            }
            .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 110)
        }
        .safeAreaInset(edge: .bottom) {
            PrimaryButton(label: "Book a visit") { adding = true }
                .padding(.horizontal, 16).padding(.bottom, 96)
        }
        .sheet(isPresented: $adding) {
            AddAppointmentSheet { context.insert($0); adding = false }
                .presentationDetents([.medium])
                .environment(\.accent, accent)
        }
    }

    private func card(_ appointment: Appointment, emphasis: Bool) -> some View {
        KCard(background: emphasis ? accent.wash.onCream(0.5) : KC.surface,
              border: emphasis ? nil : KC.border) {
            HStack(spacing: 12) {
                IconBadge(symbol: "calendar", tint: accent.deep,
                          background: emphasis ? .white.opacity(0.7) : accent.bg,
                          size: 40, corner: 12, iconSize: 18)
                VStack(alignment: .leading, spacing: 1) {
                    Text(appointment.title)
                        .font(KFont.sans(15, .semibold)).foregroundStyle(KC.ink)
                    Text([appointment.who, "\(Fmt.date(appointment.startAt)) · \(Fmt.time(appointment.startAt))"]
                        .compactMap { $0 }.joined(separator: " · "))
                        .font(KFont.sans(12)).foregroundStyle(KC.mutedStrong)
                }
                Spacer()
            }
            .padding(14)
        }
    }
}

struct AddAppointmentSheet: View {
    let onSave: (Appointment) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent

    @State private var title = ""
    @State private var who = ""
    @State private var at = Date.now.addingTimeInterval(86_400)

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    sheetField("What", $title, "e.g. Six-week check")
                    sheetField("Who", $who, "Doctor or clinic")
                    sheetRow("When") {
                        DatePicker("", selection: $at)
                            .labelsHidden().tint(accent.main)
                    }
                    PrimaryButton(label: "Save the visit",
                                  enabled: !title.trimmingCharacters(in: .whitespaces).isEmpty) {
                        onSave(Appointment(
                            title: title.trimmingCharacters(in: .whitespaces),
                            who: who.trimmingCharacters(in: .whitespaces).isEmpty ? nil : who,
                            startAt: at
                        ))
                        dismiss()
                    }
                    .padding(.top, 4)
                }
                .padding(20)
            }
            .background(KC.surface)
            .navigationTitle("Book a visit")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(accent.deep)
                }
            }
        }
    }
}
