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
                    Button {
                        toggle(group: group.def.label, vaccine: vaccine.name, given: given)
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
                                if !vaccine.description.isEmpty {
                                    Text(vaccine.description)
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

    private func toggle(group: String, vaccine: String, given: Bool) {
        if given {
            doses.filter { $0.groupLabel == group && $0.vaccineName == vaccine }
                .forEach(context.delete)
        } else {
            context.insert(VaccineDose(groupLabel: group, vaccineName: vaccine))
        }
    }
}
