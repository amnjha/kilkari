import SwiftUI
import SwiftData
import Charts

/// What the last week or month actually looked like.
///
/// Averages over a window, not a running total: "eight feeds a day" is a thing a parent can
/// check against, and a cumulative count is not. Days with nothing logged are counted as days
/// with nothing rather than skipped, because a missed day pulling the average down is the
/// honest answer — the alternative quietly flatters whoever forgot to log.
struct InsightsScreen: View {
    @Environment(\.accent) private var accent
    @Query(sort: \LogEntry.startAt) private var entries: [LogEntry]

    @State private var window = 7

    private var start: Date {
        Calendar.current.date(byAdding: .day, value: -(window - 1),
                              to: Calendar.current.startOfDay(for: .now))!
    }

    private var days: [Date] {
        (0..<window).compactMap {
            Calendar.current.date(byAdding: .day, value: $0, to: start)
        }
    }

    private func onDay(_ day: Date, _ kind: LogKind) -> [LogEntry] {
        entries.filter { $0.kind == kind && Calendar.current.isDate($0.startAt, inSameDayAs: day) }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Picker("", selection: $window) {
                    Text("7 days").tag(7)
                    Text("30 days").tag(30)
                }
                .pickerStyle(.segmented)

                if entries.isEmpty {
                    KCard {
                        VStack(spacing: 10) {
                            Image(systemName: "chart.bar.fill")
                                .font(.system(size: 28)).foregroundStyle(accent.deep)
                                .frame(width: 92, height: 92)
                                .background(accent.wash.onCream(0.6))
                                .clipShape(RoundedRectangle(cornerRadius: 38, style: .continuous))
                            Text("Nothing to average yet")
                                .font(KFont.display(17, .bold)).foregroundStyle(KC.ink)
                            Text("A few days of logging and the patterns show up here.")
                                .font(KFont.sans(13)).foregroundStyle(KC.muted)
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 26).padding(.horizontal, 20)
                    }
                } else {
                    section("Feeding", KC.goldDeep) {
                        stat("a day", average { Double(onDay($0, .feed).count) }, "feeds")
                        bars(KC.chartGold) { Double(onDay($0, .feed).count) }
                        sideSplit
                    }
                    section("Sleep", KC.lilacDeep) {
                        stat("a day", average(sleepHours), "hours")
                        bars(KC.chartSea, format: "%.1f", value: sleepHours)
                    }
                    section("Nappies", KC.seaDeep) {
                        stat("a day", average { Double(onDay($0, .diaper).count) }, "changes")
                        bars(KC.chartCoral) { Double(onDay($0, .diaper).count) }
                    }
                }
            }
            .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 110)
        }
    }

    private func sleepHours(_ day: Date) -> Double {
        onDay(day, .sleep).reduce(0) { total, entry in
            guard let end = entry.endAt else { return total }
            return total + end.timeIntervalSince(entry.startAt) / 3600
        }
    }

    private func average(_ value: (Date) -> Double) -> Double {
        guard !days.isEmpty else { return 0 }
        return days.reduce(0) { $0 + value($1) } / Double(days.count)
    }

    private func section<Content: View>(
        _ title: String, _ tint: Color, @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Circle().fill(tint).frame(width: 8, height: 8)
                Text(title).font(KFont.display(17, .bold)).foregroundStyle(KC.ink)
            }
            KCard {
                VStack(alignment: .leading, spacing: 14, content: content)
                    .padding(16)
            }
        }
    }

    private func stat(_ caption: String, _ value: Double, _ unit: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            Text(String(format: value < 10 ? "%.1f" : "%.0f", value))
                .font(KFont.number(30)).foregroundStyle(KC.ink)
            Text("\(unit) \(caption)").font(KFont.sans(13)).foregroundStyle(KC.mutedStrong)
        }
    }

    private func bars(_ tint: Color, format: String = "%.0f", value: @escaping (Date) -> Double) -> some View {
        Chart {
            ForEach(days, id: \.self) { day in
                BarMark(
                    x: .value("Day", day, unit: .day),
                    y: .value("Count", value(day))
                )
                .foregroundStyle(tint)
                .cornerRadius(4)
            }
        }
        .chartYAxis {
            AxisMarks(position: .leading) { AxisGridLine().foregroundStyle(KC.chartGrid); AxisValueLabel() }
        }
        .chartXAxis {
            AxisMarks(values: .stride(by: .day, count: window == 7 ? 1 : 7)) {
                AxisValueLabel(format: .dateTime.day().month(.narrow))
            }
        }
        .frame(height: 120)
    }

    /// Which side a breastfeed started on, over the window. Wildly uneven is worth noticing;
    /// a few percent either way is not, so the numbers are shown rather than a verdict.
    @ViewBuilder private var sideSplit: some View {
        let feeds = entries.filter {
            $0.kind == .feed && $0.feedType == .breast && $0.startAt >= start && $0.side != nil
        }
        if !feeds.isEmpty {
            let left = feeds.filter { $0.side == "L" }.count
            let share = Double(left) / Double(feeds.count)
            VStack(alignment: .leading, spacing: 6) {
                Text("Which side").font(KFont.sans(13, .semibold)).foregroundStyle(KC.mutedStrong)
                GeometryReader { geo in
                    HStack(spacing: 2) {
                        Capsule().fill(KC.chartGold)
                            .frame(width: max(0, (geo.size.width - 2) * share))
                        Capsule().fill(KC.chartSea)
                    }
                }
                .frame(height: 8)
                HStack(spacing: 14) {
                    legend(KC.chartGold, "Left", left)
                    legend(KC.chartSea, "Right", feeds.count - left)
                }
            }
        }
    }

    private func legend(_ colour: Color, _ label: String, _ count: Int) -> some View {
        HStack(spacing: 5) {
            Circle().fill(colour).frame(width: 8, height: 8)
            Text("\(label) \(count)").font(KFont.sans(12)).foregroundStyle(KC.mutedStrong)
        }
    }
}
