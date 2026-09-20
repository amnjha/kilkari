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
/// the PAN wants both. So the screen leads with the one whose turn it is and nothing else,
/// and the rest sit below it as an order to expect rather than four things to do now.
struct PaperworkScreen: View {
    let baby: Baby

    @Environment(\.modelContext) private var context
    @Environment(\.accent) private var accent
    @Query private var records: [PaperworkRecord]

    @State private var editing: PaperworkStep?

    private var steps: [PaperworkStep] {
        var settled: [String: Date] = [:]
        var skipped: Set<String> = []
        for record in records {
            if let on = record.obtainedOn { settled[record.key] = on }
            if record.skipped { skipped.insert(record.key) }
        }
        return Paperwork.chain(birth: baby.dob, settled: settled, skipped: skipped)
    }

    private var active: PaperworkStep? {
        steps.first { $0.status == .active || $0.status == .overdue }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let active {
                    nextUp(active)
                } else {
                    allSettled
                }

                SectionLabel("In this order").padding(.top, 2)
                KCard {
                    VStack(spacing: 0) {
                        ForEach(Array(steps.enumerated()), id: \.element.id) { i, step in
                            stepRow(
                                step,
                                railAbove: i == 0 ? nil : railColour(steps[i - 1]),
                                railBelow: i == steps.count - 1 ? nil : railColour(step)
                            )
                        }
                    }
                }

                Text("""
                Each office asks for the document before it, so Kilkari asks for one at a time: \
                the birth certificate 45 days from \(baby.name)'s birth, then each of the others \
                a little after the last. The dates are suggestions — open a step to record your \
                own, or to set it aside.
                """)
                    .font(KFont.sans(12)).foregroundStyle(KC.muted)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 4)
            }
            .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 110)
        }
        .sheet(item: $editing) { step in
            PaperworkStepSheet(
                step: step,
                onObtained: { on in settle(step.kind.key, obtainedOn: on); editing = nil },
                onSkip: { settle(step.kind.key, obtainedOn: nil); editing = nil },
                onUndo: { clear(step.kind.key); editing = nil }
            )
            .presentationDetents([.medium, .large])
            .environment(\.accent, accent)
        }
    }

    /// The document whose turn it is. Its date is the headline, recording it is one tap, and
    /// everything else is behind the card.
    private func nextUp(_ step: PaperworkStep) -> some View {
        let index = (steps.firstIndex { $0.id == step.id } ?? 0) + 1
        return GradientCard(colors: step.status == .overdue
                            ? [KC.danger, KC.clayDeep]
                            : [KC.clayDeep, KC.clay]) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("NEXT UP · \(index) OF \(steps.count)")
                        .font(KFont.sans(12, .semibold)).tracking(0.5)
                        .foregroundStyle(.white.opacity(0.85))
                    Spacer()
                    Text(badge(step))
                        .font(KFont.sans(12)).foregroundStyle(.white)
                        .padding(.horizontal, 9).padding(.vertical, 3)
                        .background(.white.opacity(0.18)).clipShape(Capsule())
                }
                Text(step.kind.title)
                    .font(KFont.display(22, .bold)).foregroundStyle(.white)
                Text(step.kind.why)
                    .font(KFont.sans(13)).foregroundStyle(.white.opacity(0.9))
                    .fixedSize(horizontal: false, vertical: true)

                VStack(alignment: .leading, spacing: 5) {
                    ForEach(step.kind.needs, id: \.self) { need in
                        HStack(alignment: .top, spacing: 7) {
                            Circle().fill(.white.opacity(0.7))
                                .frame(width: 4, height: 4).padding(.top, 6)
                            Text(need)
                                .font(KFont.sans(12)).foregroundStyle(.white.opacity(0.9))
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }

                HStack(spacing: 8) {
                    Button {
                        settle(step.kind.key, obtainedOn: .now)
                    } label: {
                        Text("Got it")
                            .font(KFont.sans(13, .bold))
                            .foregroundStyle(step.status == .overdue ? KC.danger : KC.clayDeep)
                            .padding(.horizontal, 16).frame(height: 38)
                            .background(.white).clipShape(Capsule())
                    }
                    Button {
                        editing = step
                    } label: {
                        Text("Details")
                            .font(KFont.sans(13, .semibold)).foregroundStyle(.white)
                            .padding(.horizontal, 14).frame(height: 38)
                            .background(.white.opacity(0.18)).clipShape(Capsule())
                    }
                }
                .buttonStyle(.plain)
                .padding(.top, 2)
            }
        }
    }

    private var allSettled: some View {
        let done = steps.filter { $0.status == .obtained }.count
        return GradientCard(colors: [KC.leafDeep, KC.leaf]) {
            VStack(alignment: .leading, spacing: 6) {
                Text("ALL SETTLED")
                    .font(KFont.sans(12, .semibold)).tracking(0.5)
                    .foregroundStyle(.white.opacity(0.85))
                Text(done == steps.count ? "All four in hand" : "\(done) in hand, the rest set aside")
                    .font(KFont.display(20, .bold)).foregroundStyle(.white)
                Text("Nothing is waiting on you.")
                    .font(KFont.sans(13)).foregroundStyle(.white.opacity(0.9))
            }
        }
    }

    /// One step in the order, with the rail that ties it to the ones either side.
    ///
    /// The rail is the whole point of the list: these are not four independent errands, and a
    /// plain list of rows would say they were.
    private func stepRow(_ step: PaperworkStep, railAbove: Color?, railBelow: Color?) -> some View {
        Button {
            editing = step
        } label: {
            HStack(alignment: .top, spacing: 12) {
                VStack(spacing: 0) {
                    Rectangle().fill(railAbove ?? .clear).frame(width: 2, height: 10)
                    ZStack {
                        Circle().fill(dotBackground(step)).frame(width: 26, height: 26)
                        Image(systemName: dotSymbol(step))
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(dotTint(step))
                    }
                    Rectangle().fill(railBelow ?? .clear).frame(width: 2).frame(maxHeight: .infinity)
                }
                .frame(width: 26)

                VStack(alignment: .leading, spacing: 2) {
                    Text(step.kind.title)
                        .font(KFont.sans(15, .semibold))
                        .foregroundStyle(step.status == .obtained || step.status == .skipped
                                         ? KC.faint : KC.ink)
                    Text(subtitle(step))
                        .font(KFont.sans(12)).foregroundStyle(KC.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.top, 8)

                Spacer(minLength: 0)

                Text(badge(step))
                    .font(KFont.sans(11, .semibold))
                    .foregroundStyle(dotTint(step))
                    .padding(.horizontal, 8).padding(.vertical, 3)
                    .background(dotBackground(step)).clipShape(Capsule())
                    .padding(.top, 10)
            }
            .padding(.horizontal, 14)
            .padding(.bottom, 12)
            .frame(minHeight: 62)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func subtitle(_ step: PaperworkStep) -> String {
        switch step.status {
        case .obtained: return "In hand"
        case .skipped: return "Set aside"
        case .waiting: return step.kind.leadText
        case .active, .overdue: return step.dueOn.map { "By \(Fmt.date($0))" } ?? step.kind.leadText
        }
    }

    private func badge(_ step: PaperworkStep) -> String {
        switch step.status {
        case .obtained: return "Done"
        case .skipped: return "Skipped"
        case .waiting: return "Waits"
        case .active:
            let days = step.inDays ?? 0
            return days == 0 ? "today" : "\(days) \(Fmt.plural(days, "day"))"
        case .overdue:
            let late = -(step.inDays ?? 0)
            return "\(late) over"
        }
    }

    private func dotSymbol(_ step: PaperworkStep) -> String {
        switch step.status {
        case .obtained: return "checkmark"
        case .skipped: return "minus"
        case .overdue: return "exclamationmark"
        case .active: return "arrow.right"
        case .waiting: return "clock"
        }
    }

    private func dotTint(_ step: PaperworkStep) -> Color {
        switch step.status {
        case .obtained: return KC.leafDeep
        case .overdue: return KC.danger
        case .active: return KC.clayDeep
        case .skipped, .waiting: return KC.muted
        }
    }

    private func dotBackground(_ step: PaperworkStep) -> Color {
        switch step.status {
        case .obtained: return KC.leafBg
        case .overdue: return KC.dangerBg
        case .active: return KC.clayBg
        case .skipped, .waiting: return KC.stoneBg
        }
    }

    /// The rail is coloured by what has been settled, so the chain reads as a progress line.
    private func railColour(_ step: PaperworkStep) -> Color {
        switch step.status {
        case .obtained: return KC.leafRing
        case .skipped: return KC.track
        default: return KC.border
        }
    }

    private func settle(_ key: String, obtainedOn: Date?) {
        clear(key)
        context.insert(PaperworkRecord(key: key, obtainedOn: obtainedOn, skipped: obtainedOn == nil))
    }

    private func clear(_ key: String) {
        records.filter { $0.key == key }.forEach(context.delete)
    }
}

/// One step, opened: what it takes, and the three things you can do about it.
struct PaperworkStepSheet: View {
    let step: PaperworkStep
    let onObtained: (Date) -> Void
    let onSkip: () -> Void
    let onUndo: () -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent

    @State private var on = Date.now

    private var settled: Bool { step.status == .obtained || step.status == .skipped }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(step.kind.why)
                        .font(KFont.sans(14)).foregroundStyle(KC.mutedStrong)
                        .fixedSize(horizontal: false, vertical: true)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("What you need")
                            .font(KFont.sans(13, .semibold)).foregroundStyle(KC.mutedStrong)
                        ForEach(step.kind.needs, id: \.self) { need in
                            HStack(alignment: .top, spacing: 8) {
                                Circle().fill(accent.ring).frame(width: 5, height: 5).padding(.top, 6)
                                Text(need).font(KFont.sans(13)).foregroundStyle(KC.ink)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }

                    if settled {
                        PrimaryButton(label: "Undo") { onUndo() }
                    } else {
                        // The date is asked for rather than assumed: paperwork is usually
                        // recorded days after the queue it was collected in.
                        DatePicker("Got it on", selection: $on, in: ...Date.now,
                                   displayedComponents: .date)
                            .font(KFont.sans(14, .semibold)).tint(accent.main)

                        PrimaryButton(label: "Record it") { onObtained(on) }

                        Button("Not doing this one") { onSkip() }
                            .font(KFont.sans(14, .semibold))
                            .foregroundStyle(KC.muted)
                            .frame(maxWidth: .infinity)
                            .padding(.top, 2)
                    }

                    Text(step.kind.leadText)
                        .font(KFont.sans(11)).foregroundStyle(KC.faint)
                        .frame(maxWidth: .infinity, alignment: .center)
                }
                .padding(20)
            }
            .background(KC.surface)
            .navigationTitle(step.kind.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }.tint(accent.deep)
                }
            }
        }
    }
}
