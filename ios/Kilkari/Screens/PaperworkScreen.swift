import SwiftUI
import SwiftData
import KilkariCore

/// One document's state, kept only when the parent has done something about it.
@Model
final class PaperworkRecord {
    var key: String
    /// The day it was obtained. Nil while it is only set aside.
    var obtainedOn: Date?
    var skipped: Bool

    init(key: String, obtainedOn: Date? = nil, skipped: Bool = false) {
        self.key = key
        self.obtainedOn = obtainedOn
        self.skipped = skipped
    }
}

/// The four documents an Indian child needs, in the order the offices insist on.
///
/// A chain, not a checklist: Aadhaar wants the birth certificate, the passport wants Aadhaar,
/// the PAN wants both. Nothing is due until its turn comes, so a parent is asked for one
/// thing at a time. The chain itself comes from `common/data`, shared with Android.
struct PaperworkScreen: View {
    let baby: Baby

    @Environment(\.modelContext) private var context
    @Environment(\.accent) private var accent
    @Query private var records: [PaperworkRecord]

    private var steps: [PaperworkStep] {
        var settled: [String: Date] = [:]
        var skipped: Set<String> = []
        for record in records {
            if let on = record.obtainedOn { settled[record.key] = on }
            if record.skipped { skipped.insert(record.key) }
        }
        return Paperwork.chain(birth: baby.dob, settled: settled, skipped: skipped)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                let done = steps.filter { $0.status == .obtained }.count
                KCard(background: accent.wash.onCream(0.5), border: nil) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(done) of \(steps.count) in hand")
                            .font(KFont.display(18, .bold)).foregroundStyle(KC.ink)
                        Text("Each one's clock starts when the one before it was settled, so only one is ever being asked of you.")
                            .font(KFont.sans(12)).foregroundStyle(KC.mutedStrong)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(16)
                }

                ForEach(steps) { step in
                    card(step)
                }
            }
            .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 110)
        }
    }

    private func card(_ step: PaperworkStep) -> some View {
        KCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(step.kind.title)
                            .font(KFont.display(17, .bold))
                            .foregroundStyle(step.status == .obtained || step.status == .skipped
                                             ? KC.faint : KC.ink)
                        Text(step.kind.why)
                            .font(KFont.sans(12)).foregroundStyle(KC.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer()
                    Text(badge(step))
                        .font(KFont.sans(11, .semibold))
                        .foregroundStyle(tint(step.status))
                        .padding(.horizontal, 9).padding(.vertical, 4)
                        .background(background(step.status))
                        .clipShape(Capsule())
                }

                // What it takes is only shown for the one actually being asked for. Four
                // expanded checklists at once is the wall of paperwork this screen exists to
                // save a parent from.
                if step.status == .active || step.status == .overdue {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("What you need").font(KFont.sans(12, .semibold)).foregroundStyle(KC.mutedStrong)
                        ForEach(step.kind.needs, id: \.self) { need in
                            HStack(alignment: .top, spacing: 8) {
                                Circle().fill(accent.ring).frame(width: 5, height: 5).padding(.top, 6)
                                Text(need).font(KFont.sans(12)).foregroundStyle(KC.ink)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                        Text(step.kind.leadText)
                            .font(KFont.sans(11)).foregroundStyle(KC.faint)
                    }

                    HStack(spacing: 8) {
                        Button("Got it") { settle(step.kind.key, obtained: true) }
                            .font(KFont.sans(13, .bold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 16).frame(height: 38)
                            .background(accent.main).clipShape(Capsule())
                        Button("Not doing this one") { settle(step.kind.key, obtained: false) }
                            .font(KFont.sans(13, .semibold))
                            .foregroundStyle(accent.deep)
                            .padding(.horizontal, 14).frame(height: 38)
                            .background(KC.surface).clipShape(Capsule())
                            .overlay(Capsule().strokeBorder(accent.ring, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                } else if step.status == .obtained || step.status == .skipped {
                    Button("Undo") { clear(step.kind.key) }
                        .font(KFont.sans(12, .semibold)).tint(KC.muted)
                }
            }
            .padding(16)
        }
    }

    private func badge(_ step: PaperworkStep) -> String {
        switch step.status {
        case .obtained: return "In hand"
        case .skipped: return "Set aside"
        case .waiting: return "Waits its turn"
        case .active:
            let days = step.inDays ?? 0
            return days == 0 ? "due today" : "in \(days) \(Fmt.plural(days, "day"))"
        case .overdue:
            let late = -(step.inDays ?? 0)
            return "\(late) \(Fmt.plural(late, "day")) over"
        }
    }

    private func tint(_ status: PaperworkStatus) -> Color {
        switch status {
        case .obtained: return KC.leafDeep
        case .overdue: return KC.danger
        case .active: return accent.deep
        case .skipped, .waiting: return KC.muted
        }
    }

    private func background(_ status: PaperworkStatus) -> Color {
        switch status {
        case .obtained: return KC.leafBg
        case .overdue: return KC.dangerBg
        case .active: return accent.bg
        case .skipped, .waiting: return KC.stoneBg
        }
    }

    private func settle(_ key: String, obtained: Bool) {
        clear(key)
        context.insert(PaperworkRecord(key: key,
                                       obtainedOn: obtained ? .now : nil,
                                       skipped: !obtained))
    }

    private func clear(_ key: String) {
        records.filter { $0.key == key }.forEach(context.delete)
    }
}
