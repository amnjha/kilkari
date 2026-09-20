import SwiftUI
import SwiftData
import KilkariCore

/// The whole schedule, generated from the date of birth, with each dose tickable.
///
/// Only given doses are stored, so correcting a birthday or switching schedules moves
/// everything outstanding without touching a row.
struct VaccinesScreen: View {
    let baby: Baby

    @Environment(\.modelContext) private var context
    @Environment(\.accent) private var accent
    @Query private var doses: [VaccineDose]
    @Query(sort: \Doctor.name) private var doctors: [Doctor]
    @Query(sort: \Appointment.startAt, order: .reverse) private var appointments: [Appointment]

    /// The dose whose sheet is open, with the group it belongs to.
    @State private var recording: (group: VaccineGroupState, vaccine: VaccineDef)?

    private var groups: [VaccineGroupState] {
        VaccinePlan.groups(for: baby, doses: doses)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                summary
                ForEach(groups) { group in
                    groupCard(group)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 110)
        }
        .sheet(item: Binding(
            get: { recording.map { RecordingDose(group: $0.group, vaccine: $0.vaccine) } },
            set: { if $0 == nil { recording = nil } }
        )) { open in
            RecordDoseSheet(
                vaccine: open.vaccine,
                existing: open.group.records[open.vaccine.name],
                babyDob: baby.dob,
                doctors: doctors,
                lastClinic: lastClinic,
                lastDoctor: appointments.first?.who ?? "",
                onSave: { on, clinic, brand, cost, addExpense in
                    record(open.group.def.label, open.vaccine.name,
                           on: on, clinic: clinic, brand: brand,
                           cost: cost, addExpense: addExpense)
                    recording = nil
                },
                onRemove: {
                    clear(open.group.def.label, open.vaccine.name)
                    recording = nil
                }
            )
            .presentationDetents([.medium, .large])
            .environment(\.accent, accent)
        }
    }

    private var summary: some View {
        let given = groups.reduce(0) { $0 + $1.givenCount }
        let total = groups.reduce(0) { $0 + $1.total }
        let overdue = groups.filter { $0.status == .overdue }.count
        return KCard(background: accent.wash.onCream(0.5), border: nil) {
            VStack(alignment: .leading, spacing: 4) {
                Text("\(given) of \(total) doses given")
                    .font(KFont.display(18, .bold)).foregroundStyle(KC.ink)
                Text(overdue == 0
                     ? "Nothing overdue."
                     : "\(overdue) \(Fmt.plural(overdue, "group")) overdue.")
                    .font(KFont.sans(13))
                    .foregroundStyle(overdue == 0 ? KC.mutedStrong : KC.danger)
            }
            .padding(16)
        }
    }

    private func groupCard(_ group: VaccineGroupState) -> some View {
        KCard {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(group.def.label)
                            .font(KFont.display(17, .bold)).foregroundStyle(KC.ink)
                        Text(Fmt.date(group.dueDate))
                            .font(KFont.sans(12)).foregroundStyle(KC.muted)
                    }
                    Spacer()
                    Text(group.dueText)
                        .font(KFont.sans(12, .semibold))
                        .foregroundStyle(group.status.tint)
                        .padding(.horizontal, 9).padding(.vertical, 4)
                        .background(group.status.background)
                        .clipShape(Capsule())
                }
                .padding(.horizontal, 14).padding(.top, 14).padding(.bottom, 10)

                ForEach(group.def.vaccines) { vaccine in
                    let given = group.given.contains(vaccine.name)
                    // Never a bare tick. A dose is a thing that happened somewhere, on a day,
                    // and asking once beats a tick the parent has to go back and annotate —
                    // everything in the sheet is optional, so it is still one tap to confirm.
                    Button {
                        recording = (group, vaccine)
                    } label: {
                        HStack(spacing: 12) {
                            ZStack {
                                Circle()
                                    .strokeBorder(given ? accent.main : accent.ring, lineWidth: 2)
                                    .background(Circle().fill(given ? accent.main : KC.surface))
                                    .frame(width: 24, height: 24)
                                if given {
                                    Image(systemName: "checkmark")
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundStyle(.white)
                                }
                            }
                            VStack(alignment: .leading, spacing: 1) {
                                Text(vaccine.name)
                                    .font(KFont.sans(14, .semibold))
                                    .foregroundStyle(given ? KC.faint : KC.ink)
                                    .strikethrough(given, color: KC.faint)
                                // Once recorded, when and where it happened is more use than
                                // what it protects against, which has just been read.
                                let detail = given
                                    ? recorded(group, vaccine.name)
                                    : vaccine.description
                                if !detail.isEmpty {
                                    Text(detail)
                                        .font(KFont.sans(12)).foregroundStyle(KC.muted)
                                }
                            }
                            Spacer()
                        }
                        .padding(.horizontal, 14).padding(.vertical, 9)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
                Color.clear.frame(height: 6)
            }
        }
    }

    /// Where the last dose was given. Most are given at the same place as the one before, and
    /// an appointment records who rather than where, so the doses themselves are the better
    /// source for this.
    private var lastClinic: String {
        doses.sorted { $0.givenOn > $1.givenOn }
            .compactMap(\.clinic).first { !$0.isEmpty } ?? ""
    }

    private func recorded(_ group: VaccineGroupState, _ vaccine: String) -> String {
        guard let dose = group.records[vaccine] else { return "" }
        return [Fmt.date(dose.givenOn), dose.clinic, dose.brand]
            .compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
    }

    private func record(_ group: String, _ vaccine: String, on: Date,
                        clinic: String?, brand: String?, cost: Int?, addExpense: Bool) {
        let alreadyRecorded = doses.contains { $0.groupLabel == group && $0.vaccineName == vaccine }
        clear(group, vaccine)
        context.insert(VaccineDose(groupLabel: group, vaccineName: vaccine,
                                   givenOn: on, clinic: clinic, brand: brand))

        // Only on the way in. Re-opening a dose to fix its date should not post the cost a
        // second time or add a second line to the timeline.
        guard !alreadyRecorded else { return }
        if let cost, addExpense {
            context.insert(Expense(title: "\(vaccine) vaccine", vendor: clinic,
                                   category: .medical, amount: cost, date: on))
        }
        context.insert(Milestone(title: "\(vaccine) given",
                                 note: [clinic, brand].compactMap { $0 }.joined(separator: " · "),
                                 date: on))
    }

    private func clear(_ group: String, _ vaccine: String) {
        doses.filter { $0.groupLabel == group && $0.vaccineName == vaccine }
            .forEach(context.delete)
    }
}

/// Identifies the sheet's subject, so SwiftUI can present it by item.
private struct RecordingDose: Identifiable {
    let group: VaccineGroupState
    let vaccine: VaccineDef
    var id: String { "\(group.def.label)/\(vaccine.name)" }
}

/// Recording one dose. Everything is optional except the fact that it happened.
///
/// The same questions Android asks, in the same order: the date, where it was given, who gave
/// it, the brand off the vial, and what it cost — which can post to Medical expenses so the
/// fund stays right without the parent entering it twice. All of it can stay blank, so
/// confirming is one tap for anyone who does not have the receipt to hand.
struct RecordDoseSheet: View {
    let vaccine: VaccineDef
    let existing: VaccineDose?
    let babyDob: Date
    let doctors: [Doctor]
    /// The clinic and doctor of the last visit, because most doses happen where the last one did.
    let lastClinic: String
    let lastDoctor: String
    let onSave: (Date, String?, String?, Int?, Bool) -> Void
    let onRemove: () -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent
    @State private var prefs = Preferences.shared

    @State private var on = Date.now
    @State private var clinic = ""
    @State private var doctor = ""
    @State private var brand = ""
    @State private var cost = ""
    @State private var addExpense = true

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    sheetTitle("\(vaccine.name) given")
                    if !vaccine.description.isEmpty {
                        sheetHint(vaccine.description)
                    }

                    sheetRow("Date") {
                        DatePicker("", selection: $on, in: babyDob...Date.now,
                                   displayedComponents: .date)
                            .labelsHidden()
                            .tint(accent.main)
                    }

                    sheetField("Clinic", $clinic, "Where it was given")

                    sheetRow("Doctor") {
                        if doctors.isEmpty {
                            TextField("", text: $doctor, prompt:
                                Text("Who gave it").font(KFont.sans(14)).foregroundStyle(KC.faint))
                                .multilineTextAlignment(.trailing)
                                .font(KFont.sans(14, .bold))
                                .foregroundStyle(KC.ink)
                                .tint(KC.coral)
                        } else {
                            // Picked from the ones on file, with a way out for a locum.
                            Menu {
                                ForEach(doctors) { saved in
                                    Button(saved.name) {
                                        doctor = saved.name
                                        if let theirs = saved.clinic, !theirs.isEmpty { clinic = theirs }
                                    }
                                }
                                Button("Someone else") { doctor = "" }
                            } label: {
                                HStack(spacing: 4) {
                                    Text(doctor.isEmpty ? "Choose" : doctor)
                                        .font(KFont.sans(14, .bold))
                                        .foregroundStyle(doctor.isEmpty ? KC.faint : KC.ink)
                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundStyle(accent.deep)
                                }
                            }
                        }
                    }

                    sheetField("Brand (optional)", $brand, "e.g. Pentavac")
                    sheetField("Cost (\(prefs.currency.symbol))", $cost, "0",
                               numeric: true, big: true)

                    Button {
                        addExpense.toggle()
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 1) {
                                Text("Add to Medical expenses")
                                    .font(KFont.sans(14, .semibold)).foregroundStyle(KC.ink)
                                Text("Shows up under Money")
                                    .font(KFont.sans(12)).foregroundStyle(KC.muted)
                            }
                            Spacer()
                            KToggle(on: addExpense, tint: accent.main)
                        }
                        .padding(4)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)

                    PrimaryButton(label: existing == nil ? "Save · add to timeline" : "Save changes") {
                        func clean(_ s: String) -> String? {
                            let t = s.trimmingCharacters(in: .whitespaces)
                            return t.isEmpty ? nil : t
                        }
                        let amount = Int(cost).flatMap { $0 > 0 ? $0 : nil }
                        onSave(on, clean(clinic), clean(brand), amount, addExpense && amount != nil)
                        dismiss()
                    }
                    .padding(.top, 2)

                    if existing != nil {
                        Button("Not given after all") {
                            onRemove()
                            dismiss()
                        }
                        .font(KFont.sans(14, .semibold))
                        .foregroundStyle(KC.danger)
                        .frame(maxWidth: .infinity)
                    }
                }
                .padding(20)
            }
            .background(KC.surface)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(accent.deep)
                }
            }
            .onAppear {
                on = existing?.givenOn ?? .now
                clinic = existing?.clinic ?? lastClinic
                doctor = lastDoctor
                brand = existing?.brand ?? ""
            }
        }
    }
}

/// The 44x26 switch Android draws, so a toggle looks the same on both.
struct KToggle: View {
    let on: Bool
    let tint: Color

    var body: some View {
        Capsule()
            .fill(on ? tint : KC.track)
            .frame(width: 44, height: 26)
            .overlay(alignment: on ? .trailing : .leading) {
                Circle().fill(.white).frame(width: 20, height: 20).padding(3)
            }
            .animation(.spring(response: 0.25, dampingFraction: 0.8), value: on)
    }
}
