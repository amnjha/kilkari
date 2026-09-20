import Foundation

/// One document in the chain, and what it takes to get it.
public struct PaperworkKind: Decodable, Sendable, Equatable, Identifiable {
    public let key: String
    public let title: String
    /// Why it matters, in a sentence — the answer to "can I skip this one".
    public let why: String
    /// Days allowed from the moment the one before it was settled. The first counts from birth.
    public let leadDays: Int
    public let leadText: String
    public let needs: [String]

    public var id: String { key }
}

/// Where a document stands today.
public enum PaperworkStatus: String, Sendable {
    /// Its turn has not come: the one before it is still outstanding.
    case waiting
    /// Its turn, and the clock is running.
    case active
    case overdue
    case obtained
    /// Deliberately skipped — not everyone wants a passport for a newborn.
    case skipped
}

/// One document's position in the chain on a given day.
public struct PaperworkStep: Sendable, Identifiable {
    public let kind: PaperworkKind
    public let status: PaperworkStatus
    /// When it should be settled by, once its turn has come.
    public let dueOn: Date?
    /// Whole days until [dueOn]. Negative means it has passed.
    public let inDays: Int?

    public var id: String { kind.key }
}

/// The four documents an Indian child needs, in the order the offices insist on.
///
/// A chain, not a checklist: Aadhaar wants the birth certificate, the passport wants Aadhaar,
/// the PAN wants both. Each one's clock starts when the one before it was settled, so nothing
/// is due until its turn comes and a parent is asked for one thing at a time.
///
/// Read from `common/data/paperwork.json`, the same file the Android app is checked against.
public enum Paperwork {

    private struct Document: Decodable {
        let documents: [PaperworkKind]
    }

    public static let kinds: [PaperworkKind] = SharedData
        .load(Document.self, from: "paperwork").documents

    public static func byKey(_ key: String) -> PaperworkKind? {
        kinds.first { $0.key == key }
    }

    /// Where every document stands on [today].
    ///
    /// - Parameters:
    ///   - settled: key to the day it was obtained or set aside.
    ///   - skipped: keys the parent has chosen not to pursue.
    public static func chain(
        birth: Date,
        settled: [String: Date],
        skipped: Set<String> = [],
        today: Date = .now
    ) -> [PaperworkStep] {
        let cal = Calendar(identifier: .gregorian)
        var previousSettled: Date? = birth
        var steps: [PaperworkStep] = []

        for kind in kinds {
            if skipped.contains(kind.key) {
                steps.append(PaperworkStep(kind: kind, status: .skipped, dueOn: nil, inDays: nil))
                // A skipped document still unblocks the next: the offices will not wait for a
                // passport nobody is applying for.
                previousSettled = settled[kind.key] ?? previousSettled
                continue
            }
            if let on = settled[kind.key] {
                steps.append(PaperworkStep(kind: kind, status: .obtained, dueOn: nil, inDays: nil))
                previousSettled = on
                continue
            }
            guard let from = previousSettled else {
                steps.append(PaperworkStep(kind: kind, status: .waiting, dueOn: nil, inDays: nil))
                continue
            }
            let due = cal.date(byAdding: .day, value: kind.leadDays, to: from) ?? from
            let days = cal.dateComponents([.day], from: cal.startOfDay(for: today),
                                          to: cal.startOfDay(for: due)).day ?? 0
            steps.append(PaperworkStep(
                kind: kind,
                status: days < 0 ? .overdue : .active,
                dueOn: due,
                inDays: days
            ))
            // Everything after the first outstanding document is waiting on it.
            previousSettled = nil
        }
        return steps
    }
}
