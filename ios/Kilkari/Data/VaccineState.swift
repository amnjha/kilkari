import Foundation
import SwiftUI
import KilkariCore

/// Where a group of doses stands relative to today.
enum VaccineStatus {
    case given, overdue, dueSoon, upcoming

    var label: String {
        switch self {
        case .given: return "Given"
        case .overdue: return "Overdue"
        case .dueSoon: return "Due soon"
        case .upcoming: return "Upcoming"
        }
    }

    /// Coral is the app's colour for anything asking something of a parent, so lateness gets
    /// the deeper, browner danger step and everything finished goes green.
    var tint: Color {
        switch self {
        case .given: return KC.leafDeep
        case .overdue: return KC.danger
        case .dueSoon: return KC.clayDeep
        case .upcoming: return KC.muted
        }
    }

    var background: Color {
        switch self {
        case .given: return KC.leafBg
        case .overdue: return KC.dangerBg
        case .dueSoon: return KC.clayBg
        case .upcoming: return KC.stoneBg
        }
    }
}

/// One group of doses, with what is known about it today.
struct VaccineGroupState: Identifiable {
    let def: VaccineGroupDef
    let dueDate: Date
    let given: Set<String>

    var id: String { def.label }
    var total: Int { def.vaccines.count }
    var givenCount: Int { def.vaccines.filter { given.contains($0.name) }.count }
    var isComplete: Bool { givenCount == total }

    /// Whole days from today. Negative means it has passed.
    var inDays: Int {
        let cal = Calendar.current
        return cal.dateComponents([.day], from: cal.startOfDay(for: .now),
                                  to: cal.startOfDay(for: dueDate)).day ?? 0
    }

    var status: VaccineStatus {
        if isComplete { return .given }
        if inDays < 0 { return .overdue }
        if inDays <= 14 { return .dueSoon }
        return .upcoming
    }

    /// "3 days overdue", "today", "in 5 weeks".
    var dueText: String {
        if isComplete { return "Given" }
        let days = inDays
        if days < 0 {
            let late = -days
            return "\(late) \(Fmt.plural(late, "day")) overdue"
        }
        if days == 0 { return "today" }
        if days < 14 { return "in \(days) \(Fmt.plural(days, "day"))" }
        let weeks = days / 7
        return "in \(weeks) \(Fmt.plural(weeks, "week"))"
    }
}

/// The schedule turned into what this baby actually needs, and when.
enum VaccinePlan {

    static func groups(
        for baby: Baby,
        scheduleId: String = "iap",
        doses: [VaccineDose]
    ) -> [VaccineGroupState] {
        let cal = Calendar.current
        let byGroup = Dictionary(grouping: doses, by: \.groupLabel)
        return VaccineSchedules.byId(scheduleId).groups.map { def in
            VaccineGroupState(
                def: def,
                dueDate: cal.date(byAdding: .day, value: def.dayOffset, to: baby.dob) ?? baby.dob,
                given: Set((byGroup[def.label] ?? []).map(\.vaccineName))
            )
        }
    }

    /// What the home screen points at: the oldest thing still outstanding, or the next one up.
    static func next(_ groups: [VaccineGroupState]) -> VaccineGroupState? {
        groups.first { !$0.isComplete }
    }
}
