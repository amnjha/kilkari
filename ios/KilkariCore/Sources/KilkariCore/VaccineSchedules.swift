import Foundation

/// One vaccine within a group.
public struct VaccineDef: Decodable, Sendable, Equatable, Identifiable {
    public let name: String
    public let description: String

    public var id: String { name }
}

/// A set of vaccines all due at the same age.
public struct VaccineGroupDef: Decodable, Sendable, Equatable, Identifiable {
    public let label: String
    /// Days after the date of birth this group falls due.
    public let dayOffset: Int
    public let vaccines: [VaccineDef]

    public var id: String { label }
}

/// One country's or body's schedule.
public struct ScheduleDef: Decodable, Sendable, Equatable, Identifiable {
    public let id: String
    public let name: String
    public let description: String
    public let groups: [VaccineGroupDef]

    /// "IAP" out of "IAP · India (private)" — the short chip label.
    public var shortName: String {
        String(name.split(separator: "·").first ?? Substring(name))
            .trimmingCharacters(in: .whitespaces)
    }
}

/// The vaccination schedules, read from `common/data/vaccine-schedules.json`.
///
/// Due dates are derived per baby by offsetting each group's `dayOffset` from the date of
/// birth, so one definition serves any baby. The file is the same one Android reads: a
/// schedule that differs by a week between two phones is the kind of bug nobody catches until
/// a parent compares them at a clinic.
public enum VaccineSchedules {

    private struct Document: Decodable {
        let schedules: [ScheduleDef]
    }

    public static let all: [ScheduleDef] = SharedData
        .load(Document.self, from: "vaccine-schedules").schedules

    /// Falls back to the first schedule rather than failing: an unknown id means a setting
    /// written by a newer version, and a parent should still see a usable list.
    public static func byId(_ id: String) -> ScheduleDef {
        all.first { $0.id == id } ?? all[0]
    }
}
