import SwiftUI
import SwiftData

/// Six quick-log tiles over the day's entries. Tapping a tile opens its sheet.
struct LogScreen: View {
    let baby: Baby

    @Environment(\.modelContext) private var context
    @Environment(\.accent) private var accent
    @Query(sort: \LogEntry.startAt, order: .reverse) private var entries: [LogEntry]

    @State private var logging: LogKind?

    private var openSleep: LogEntry? {
        entries.first { $0.kind == .sleep && $0.endAt == nil }
    }

    private var todays: [LogEntry] {
        let today = Calendar.current.startOfDay(for: .now)
        return entries.filter { $0.startAt >= today }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Log").font(KFont.screenTitle).foregroundStyle(KC.ink)
                    Text("Tap a tile to add an entry.")
                        .font(KFont.sans(13))
                        .foregroundStyle(KC.muted)
                }

                ForEach(Array(LogKind.allCases.chunked(2).enumerated()), id: \.offset) { _, pair in
                    HStack(alignment: .top, spacing: 10) {
                        ForEach(pair) { kind in
                            tile(kind)
                        }
                        if pair.count == 1 { Color.clear.frame(maxWidth: .infinity) }
                    }
                }

                SectionLabel("Today's entries").padding(.top, 4)
                KCard {
                    if todays.isEmpty {
                        Text("Nothing logged today yet.")
                            .font(KFont.sans(13))
                            .foregroundStyle(KC.muted)
                            .padding(14)
                    } else {
                        VStack(spacing: 0) {
                            ForEach(todays) { entry in
                                LogRow(entry: entry)
                                if entry.id != todays.last?.id {
                                    Rectangle().fill(KC.divider).frame(height: 1)
                                }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 18)
            .padding(.bottom, 110)
        }
        .sheet(item: $logging) { kind in
            QuickLogSheet(kind: kind) { entry in
                context.insert(entry)
                logging = nil
            }
            .presentationDetents([.medium])
        }
    }

    private func tile(_ kind: LogKind) -> some View {
        Button {
            logging = kind
        } label: {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Circle()
                        .fill(.white.opacity(0.85))
                        .frame(width: 44, height: 44)
                        .overlay {
                            Image(systemName: kind.symbol)
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundStyle(kind.foreground)
                        }
                    Spacer()
                    Text(agoText(kind))
                        .font(KFont.sans(11, .bold))
                        .foregroundStyle(kind.foreground)
                        .padding(.horizontal, 8).padding(.vertical, 3)
                        .background(.white.opacity(0.7))
                        .clipShape(Capsule())
                        .lineLimit(1)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(kind.title)
                        .font(KFont.sans(16, .bold))
                        .foregroundStyle(KC.ink)
                    Text(detail(kind))
                        .font(KFont.sans(12))
                        .foregroundStyle(KC.mutedStrong)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .frame(minHeight: 104, alignment: .topLeading)
            .background(kind.wash)
            .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
            .clay(corner: 26)
        }
        .buttonStyle(SpringPress())
    }

    private func agoText(_ kind: LogKind) -> String {
        if kind == .sleep, let nap = openSleep { return "asleep \(Fmt.elapsed(from: nap.startAt))" }
        return Fmt.ago(entries.first { $0.kind == kind }?.startAt)
    }

    private func detail(_ kind: LogKind) -> String {
        guard let last = entries.first(where: { $0.kind == kind }) else {
            switch kind {
            case .diaper: return "None today"
            case .growth: return "No measurements"
            case .tooth: return "0 of 20 erupted"
            case .medicine: return "No active medicine"
            default: return "Nothing logged yet"
            }
        }
        switch kind {
        case .diaper:
            let count = todays.filter { $0.kind == .diaper }.count
            return count == 0 ? "None today" : "\(count) today"
        case .sleep:
            if let nap = openSleep { return "Since \(Fmt.time(nap.startAt))" }
            return "Last: \(Fmt.time(last.startAt))"
        default:
            return last.summary
        }
    }
}

extension Array {
    /// Fixed-size groups, for laying a list out as a grid of rows.
    func chunked(_ size: Int) -> [[Element]] {
        stride(from: 0, to: count, by: size).map { Array(self[$0..<Swift.min($0 + size, count)]) }
    }
}
