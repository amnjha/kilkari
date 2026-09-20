import SwiftUI
import SwiftData
import Charts
import KilkariCore

/// Weight over time against the WHO percentile bands, and where the last reading sits.
///
/// The bands are the middle 70% and 94% of healthy children, drawn from the same LMS tables
/// the Android chart uses. A percentile is a position, not a grade — the screen says so,
/// because a parent reading "15th" without that sentence hears "failing".
struct GrowthScreen: View {
    let baby: Baby

    @Environment(\.modelContext) private var context
    @Environment(\.accent) private var accent
    @State private var prefs = Preferences.shared
    @Query(sort: \GrowthRecord.date) private var records: [GrowthRecord]

    @State private var adding = false

    private var sex: Sex? {
        baby.sexRaw.flatMap(Sex.init(rawValue:))
    }

    /// Age in months at a date, as a fraction, so a point sits where it belongs on the curve.
    private func months(_ date: Date) -> Double {
        let days = Calendar.current.dateComponents([.day], from: baby.dob, to: date).day ?? 0
        return Double(days) / 30.4375
    }

    private var weighed: [GrowthRecord] {
        records.filter { $0.weightKg != nil }
    }

    /// How far along the table to draw: a little past the newest reading, never past the end.
    private var span: Double {
        let latest = weighed.last.map { months($0.date) } ?? 3
        return min(Double(GrowthStandards.maxMonths), max(3, latest + 2))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if weighed.isEmpty {
                    empty
                } else {
                    standing
                    chart
                    history
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 110)
        }
        .safeAreaInset(edge: .bottom) {
            PrimaryButton(label: "Add a measurement") { adding = true }
                .padding(.horizontal, 16)
                .padding(.bottom, 96)
        }
        .sheet(isPresented: $adding) {
            AddMeasurementSheet { record in
                context.insert(record)
                adding = false
            }
            .presentationDetents([.medium])
            .environment(\.accent, accent)
        }
    }

    private var empty: some View {
        KCard {
            VStack(spacing: 10) {
                Image(systemName: "scalemass.fill")
                    .font(.system(size: 30)).foregroundStyle(accent.deep)
                    .frame(width: 96, height: 96)
                    .background(accent.wash.onCream(0.6))
                    .clipShape(RoundedRectangle(cornerRadius: 40, style: .continuous))
                Text("No measurements yet")
                    .font(KFont.display(17, .bold)).foregroundStyle(KC.ink)
                Text("Add a weigh-in and the chart draws it against the WHO curves for \(baby.name)'s age.")
                    .font(KFont.sans(13)).foregroundStyle(KC.muted)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 26).padding(.horizontal, 20)
        }
    }

    private var standing: some View {
        let last = weighed.last!
        let age = months(last.date)
        let percentile = sex.flatMap {
            GrowthStandards.percentile(ofKg: last.weightKg!, months: age, sex: $0)
        }
        return KCard(background: accent.wash.onCream(0.5), border: nil) {
            VStack(alignment: .leading, spacing: 4) {
                Text(prefs.weight(last.weightKg))
                    .font(KFont.number(30)).foregroundStyle(KC.ink)
                if let percentile {
                    Text("\(ordinal(percentile)) percentile for \(sex == .boy ? "boys" : "girls") at \(Fmt.age(from: baby.dob, to: last.date))")
                        .font(KFont.sans(13)).foregroundStyle(KC.mutedStrong)
                } else {
                    Text("Set a sex in settings to see a percentile — averaging the two curves would be a number about nobody.")
                        .font(KFont.sans(12)).foregroundStyle(KC.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Text("A percentile is a position among healthy children, not a grade.")
                    .font(KFont.sans(11)).foregroundStyle(KC.faint)
                    .padding(.top, 2)
            }
            .padding(16)
        }
    }

    private var chart: some View {
        let steps = stride(from: 0.0, through: span, by: 0.5).map { $0 }
        return KCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Weight for age").font(KFont.sans(14, .bold)).foregroundStyle(KC.ink)

                Chart {
                    // Outer band, 3rd to 97th, then the inner 15th to 85th over it: two steps
                    // of one hue so the inner reads as clearly darker without either
                    // competing with the line drawn on top.
                    ForEach(steps, id: \.self) { m in
                        if let low = GrowthStandards.weightAtPercentile(months: m, sex: sex, percentile: 3),
                           let high = GrowthStandards.weightAtPercentile(months: m, sex: sex, percentile: 97) {
                            AreaMark(x: .value("Months", m),
                                     yStart: .value("kg", low), yEnd: .value("kg", high))
                                .foregroundStyle(KC.chartBandOuter)
                        }
                    }
                    ForEach(steps, id: \.self) { m in
                        if let low = GrowthStandards.weightAtPercentile(months: m, sex: sex, percentile: 15),
                           let high = GrowthStandards.weightAtPercentile(months: m, sex: sex, percentile: 85) {
                            AreaMark(x: .value("Months", m),
                                     yStart: .value("kg", low), yEnd: .value("kg", high))
                                .foregroundStyle(KC.chartBandInner)
                        }
                    }
                    ForEach(steps, id: \.self) { m in
                        if let median = GrowthStandards.medianWeightKg(months: m, sex: sex) {
                            LineMark(x: .value("Months", m), y: .value("kg", median))
                                .foregroundStyle(KC.chartSea)
                                .lineStyle(.init(lineWidth: 1.5, dash: [4, 4]))
                        }
                    }
                    ForEach(weighed) { record in
                        LineMark(x: .value("Months", months(record.date)),
                                 y: .value("kg", record.weightKg!))
                            .foregroundStyle(KC.chartCoral)
                            .lineStyle(.init(lineWidth: 2))
                        PointMark(x: .value("Months", months(record.date)),
                                  y: .value("kg", record.weightKg!))
                            .foregroundStyle(KC.chartCoral)
                            .symbolSize(60)
                    }
                }
                .chartXAxisLabel("months", position: .bottom)
                .chartYAxisLabel("kg", position: .leading)
                .chartXScale(domain: 0...span)
                .frame(height: 220)

                // Identity is never colour alone: the line is named, not just tinted.
                HStack(spacing: 14) {
                    legend(KC.chartCoral, baby.name)
                    legend(KC.chartSea, "WHO median")
                    legend(KC.chartBandInner, "Middle 70%")
                }
            }
            .padding(16)
        }
    }

    private func legend(_ colour: Color, _ label: String) -> some View {
        HStack(spacing: 5) {
            Circle().fill(colour).frame(width: 8, height: 8)
            Text(label).font(KFont.sans(11)).foregroundStyle(KC.mutedStrong)
        }
    }

    private var history: some View {
        KCard {
            VStack(spacing: 0) {
                let rows = Array(records.reversed())
                ForEach(Array(rows.enumerated()), id: \.element.persistentModelID) { i, record in
                    HStack {
                        VStack(alignment: .leading, spacing: 1) {
                            Text([record.weightKg == nil ? nil : prefs.weight(record.weightKg),
                                  record.lengthCm == nil ? nil : prefs.length(record.lengthCm)]
                                .compactMap { $0 }.joined(separator: " · "))
                                .font(KFont.sans(14)).foregroundStyle(KC.ink)
                            Text(Fmt.age(from: baby.dob, to: record.date))
                                .font(KFont.sans(12)).foregroundStyle(KC.muted)
                        }
                        Spacer()
                        Text(Fmt.date(record.date)).font(KFont.sans(12)).foregroundStyle(KC.muted)
                    }
                    .padding(.horizontal, 14).padding(.vertical, 11)
                    if i != rows.count - 1 { Rectangle().fill(KC.divider).frame(height: 1) }
                }
            }
        }
    }

    private func ordinal(_ n: Int) -> String {
        let suffix: String
        switch (n % 100, n % 10) {
        case (11...13, _): suffix = "th"
        case (_, 1): suffix = "st"
        case (_, 2): suffix = "nd"
        case (_, 3): suffix = "rd"
        default: suffix = "th"
        }
        return "\(n)\(suffix)"
    }
}

/// Weight, length and head — all optional, because a home scale gives one of the three.
struct AddMeasurementSheet: View {
    let onSave: (GrowthRecord) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent

    @State private var weight = ""
    @State private var length = ""
    @State private var head = ""
    @State private var date = Date.now

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    field(Preferences.shared.metric ? "Weight (kg)" : "Weight (lb)", $weight, Preferences.shared.metric ? "e.g. 4.2" : "e.g. 9.2")
                    field(Preferences.shared.metric ? "Length (cm)" : "Length (in)", $length, "optional")
                    field(Preferences.shared.metric ? "Head (cm)" : "Head (in)", $head, "optional")
                    sheetRow("Taken") {
                        DatePicker("", selection: $date, in: ...Date.now, displayedComponents: .date)
                            .labelsHidden().tint(accent.main)
                    }
                    PrimaryButton(label: "Save measurement", enabled: anyValue) {
                        // Typed in whatever the parent reads, stored in kilograms and
                        // centimetres, because that is what the WHO tables speak.
                        let metric = Preferences.shared.metric
                        onSave(GrowthRecord(
                            date: date,
                            weightKg: Double(weight).map { metric ? $0 : Units.lbToKg($0) },
                            lengthCm: Double(length).map { metric ? $0 : Units.inToCm($0) },
                            headCm: Double(head).map { metric ? $0 : Units.inToCm($0) }
                        ))
                        dismiss()
                    }
                    .padding(.top, 4)
                }
                .padding(20)
            }
            .background(KC.surface)
            .navigationTitle("Measurement")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(accent.deep)
                }
            }
        }
    }

    private var anyValue: Bool {
        Double(weight) != nil || Double(length) != nil || Double(head) != nil
    }

    private func field(_ label: String, _ text: Binding<String>, _ prompt: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(KFont.sans(13, .semibold)).foregroundStyle(KC.mutedStrong)
            TextField("", text: text, prompt: Text(prompt))
                .keyboardType(.decimalPad)
                .font(KFont.sans(16))
                .padding(14)
                .background(KC.surface)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
    }
}
