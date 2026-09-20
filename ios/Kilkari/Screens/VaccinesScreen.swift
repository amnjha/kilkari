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
                onSave: { on, clinic, brand in
                    record(open.group.def.label, open.vaccine.name,
                           on: on, clinic: clinic, brand: brand)
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

    private func recorded(_ group: VaccineGroupState, _ vaccine: String) -> String {
        guard let dose = group.records[vaccine] else { return "" }
        return [Fmt.date(dose.givenOn), dose.clinic, dose.brand]
            .compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
    }

    private func record(_ group: String, _ vaccine: String,
                        on: Date, clinic: String?, brand: String?) {
        clear(group, vaccine)
        context.insert(VaccineDose(groupLabel: group, vaccineName: vaccine,
                                   givenOn: on, clinic: clinic, brand: brand))
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
/// The date defaults to today and the rest can stay blank, so confirming takes one tap — but
/// a parent who knows the clinic or the brand has somewhere to put it, instead of a tick they
/// would have to come back and annotate.
struct RecordDoseSheet: View {
    let vaccine: VaccineDef
    let existing: VaccineDose?
    let babyDob: Date
    let onSave: (Date, String?, String?) -> Void
    let onRemove: () -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent

    @State private var on = Date.now
    @State private var clinic = ""
    @State private var brand = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if !vaccine.description.isEmpty {
                        Text(vaccine.description)
                            .font(KFont.sans(13)).foregroundStyle(KC.muted)
                    }

                    DatePicker("Given on", selection: $on, in: babyDob...Date.now,
                               displayedComponents: .date)
                        .font(KFont.sans(14, .semibold)).tint(accent.main)

                    sheetField("Clinic", $clinic, "Optional")
                    sheetField("Brand", $brand, "Optional")

                    PrimaryButton(label: existing == nil ? "Record it" : "Save changes") {
                        func clean(_ s: String) -> String? {
                            let t = s.trimmingCharacters(in: .whitespaces)
                            return t.isEmpty ? nil : t
                        }
                        onSave(on, clean(clinic), clean(brand))
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
            .background(KC.screen)
            .navigationTitle(vaccine.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(accent.deep)
                }
            }
            .onAppear {
                on = existing?.givenOn ?? .now
                clinic = existing?.clinic ?? ""
                brand = existing?.brand ?? ""
            }
        }
    }
}
