import SwiftUI
import SwiftData
import KilkariCore

/// A holding in the fund: a deposit, a recurring plan, a SIP.
@Model
final class Investment {
    var name: String
    var kindRaw: String
    /// What went in up front, for a one-off.
    var investedAmount: Int
    /// What goes in each month, for a recurring plan.
    var monthlyAmount: Int?
    var ratePercent: Double?
    var startedOn: Date
    var maturesOn: Date?
    /// The last value the parent wrote down, and when. There is no price feed.
    var currentValue: Int?
    var valuedOn: Date?

    init(
        name: String,
        kind: InvestmentKind,
        investedAmount: Int,
        monthlyAmount: Int? = nil,
        ratePercent: Double? = nil,
        startedOn: Date = .now,
        maturesOn: Date? = nil,
        currentValue: Int? = nil,
        valuedOn: Date? = nil
    ) {
        self.name = name
        self.kindRaw = kind.rawValue
        self.investedAmount = investedAmount
        self.monthlyAmount = monthlyAmount
        self.ratePercent = ratePercent
        self.startedOn = startedOn
        self.maturesOn = maturesOn
        self.currentValue = currentValue
        self.valuedOn = valuedOn
    }

    var kind: InvestmentKind { InvestmentKind(rawValue: kindRaw) ?? .other }
}

extension InvestmentKind {
    var label: String {
        switch self {
        case .fd: return "Fixed deposit"
        case .rd: return "Recurring deposit"
        case .sip: return "SIP"
        case .ppf: return "PPF"
        case .ssy: return "Sukanya Samriddhi"
        case .other: return "Other"
        }
    }
}

/// What the fund is invested in, what it has earned, and what it should come to.
///
/// Nothing is fetched. The app has no network and no price feed, so a holding's return is
/// only as current as the value the parent last wrote down — and the screen says so rather
/// than implying a live number. Both figures come from `KilkariCore`, the same code the
/// Android app's tests pin.
struct InvestScreen: View {
    @Environment(\.modelContext) private var context
    @Environment(\.accent) private var accent
    @State private var prefs = Preferences.shared
    @Query(sort: \Investment.startedOn) private var investments: [Investment]
    @State private var adding = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if investments.isEmpty {
                KCard {
                    VStack(spacing: 10) {
                        Image(systemName: "chart.line.uptrend.xyaxis")
                            .font(.system(size: 28)).foregroundStyle(accent.deep)
                            .frame(width: 92, height: 92)
                            .background(accent.wash.onCream(0.6))
                            .clipShape(RoundedRectangle(cornerRadius: 38, style: .continuous))
                        Text("Nothing invested yet")
                            .font(KFont.display(17, .bold)).foregroundStyle(KC.ink)
                        Text("Add a deposit or a plan and Kilkari works out what it has earned and what it matures to.")
                            .font(KFont.sans(13)).foregroundStyle(KC.muted)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 26).padding(.horizontal, 20)
                }
            } else {
                ForEach(investments) { holding in
                    card(holding)
                }
            }

            Button {
                adding = true
            } label: {
                Label("Add a holding", systemImage: "plus")
                    .font(KFont.sans(14, .bold))
                    .foregroundStyle(accent.deep)
                    .frame(maxWidth: .infinity)
                    .frame(height: 46)
                    .background(KC.surface)
                    .clipShape(Capsule())
                    .overlay(Capsule().strokeBorder(accent.ring, lineWidth: 1))
            }
            .buttonStyle(SpringPress())
        }
        .sheet(isPresented: $adding) {
            AddInvestmentSheet { context.insert($0); adding = false }
                .presentationDetents([.large])
                .environment(\.accent, accent)
        }
    }

    private func card(_ holding: Investment) -> some View {
        KCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    VStack(alignment: .leading, spacing: 1) {
                        Text(holding.name)
                            .font(KFont.sans(16, .bold)).foregroundStyle(KC.ink)
                        Text(holding.kind.label)
                            .font(KFont.sans(12)).foregroundStyle(KC.muted)
                    }
                    Spacer()
                    if let rate = holding.ratePercent {
                        Text(String(format: "%.1f%%", rate))
                            .font(KFont.sans(12, .semibold))
                            .foregroundStyle(accent.deep)
                            .padding(.horizontal, 9).padding(.vertical, 4)
                            .background(accent.bg).clipShape(Capsule())
                    }
                }

                HStack(spacing: 20) {
                    figure("In", prefs.money(paidIn(holding)))
                    if let value = holding.currentValue {
                        figure("Worth", prefs.money(value))
                    }
                }

                if let xirr = annualised(holding), let valuedOn = holding.valuedOn {
                    line("Annualised return",
                         String(format: "%@%.1f%%", xirr < 0 ? "−" : "+", abs(xirr) * 100),
                         xirr < 0 ? KC.danger : KC.leafDeep)
                    let atValuation = paidIn(holding, upTo: valuedOn)
                    Text(atValuation == paidIn(holding)
                         ? "Worked out from what went in and the value you last wrote down on \(Fmt.date(valuedOn)). Nothing is fetched."
                         : "On the \(prefs.money(atValuation)) that had gone in by \(Fmt.date(valuedOn)), the day you last wrote a value down. Nothing is fetched.")
                        .font(KFont.sans(11)).foregroundStyle(KC.faint)
                        .fixedSize(horizontal: false, vertical: true)
                }

                if let projected = projection(holding), let matures = holding.maturesOn {
                    line("At maturity", prefs.money(Int(projected)), KC.ink)
                    Text("On \(Fmt.date(matures)) at the rate entered, compounded quarterly.")
                        .font(KFont.sans(11)).foregroundStyle(KC.faint)
                }
            }
            .padding(16)
        }
    }

    private func figure(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(label).font(KFont.sans(11)).foregroundStyle(KC.muted)
            Text(value).font(KFont.sans(17, .bold)).foregroundStyle(KC.ink)
        }
    }

    private func line(_ label: String, _ value: String, _ tint: Color) -> some View {
        HStack {
            Text(label).font(KFont.sans(13)).foregroundStyle(KC.mutedStrong)
            Spacer()
            Text(value).font(KFont.sans(14, .bold)).foregroundStyle(tint)
        }
    }

    /// Every payment into a holding up to a date: the lump sum, or one instalment a month.
    ///
    /// One definition, used twice with different end dates — what has gone in by today, and
    /// what had gone in by the day the value was written down. Working those out separately
    /// is how "in ₹55,000" ends up beside a return calculated on ₹50,000.
    private func contributions(_ holding: Investment, upTo end: Date) -> [(date: Date, amountInr: Int64)] {
        let cal = Calendar.current
        guard holding.kind.recurring, let monthly = holding.monthlyAmount else {
            return holding.startedOn <= end ? [(holding.startedOn, Int64(holding.investedAmount))] : []
        }
        let months = cal.dateComponents([.month], from: holding.startedOn, to: end).month ?? 0
        guard months >= 0 else { return [] }
        return (0...months).compactMap { m in
            cal.date(byAdding: .month, value: m, to: holding.startedOn).map { ($0, Int64(monthly)) }
        }
    }

    private func paidIn(_ holding: Investment, upTo end: Date = .now) -> Int {
        Int(contributions(holding, upTo: end).reduce(0) { $0 + $1.amountInr })
    }

    private func annualised(_ holding: Investment) -> Double? {
        guard let value = holding.currentValue, let valuedOn = holding.valuedOn else { return nil }
        return Returns.holdingReturn(
            contributions: contributions(holding, upTo: valuedOn),
            valueInr: Int64(value),
            valuedOn: valuedOn
        )
    }

    private func projection(_ holding: Investment) -> Int64? {
        Returns.projectedMaturityInr(
            kind: holding.kind,
            investedInr: Int64(holding.investedAmount),
            monthlyInr: holding.monthlyAmount.map(Int64.init),
            ratePercent: holding.ratePercent,
            start: holding.startedOn,
            maturity: holding.maturesOn
        )
    }
}

struct AddInvestmentSheet: View {
    let onSave: (Investment) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent

    @State private var name = ""
    @State private var kind: InvestmentKind = .fd
    @State private var amount = ""
    @State private var monthly = ""
    @State private var rate = ""
    @State private var started = Date.now
    @State private var matures = Calendar.current.date(byAdding: .year, value: 5, to: .now) ?? .now
    @State private var hasMaturity = true
    @State private var value = ""

    private var symbol: String { Preferences.shared.currency.symbol }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    sheetField("Name", $name, "e.g. SBI fixed deposit")

                    Picker("Kind", selection: $kind) {
                        ForEach(InvestmentKind.allCases, id: \.self) { Text($0.label).tag($0) }
                    }
                    .tint(accent.deep)

                    if kind.recurring {
                        sheetField("Each month (\(symbol))", $monthly, "0", numeric: true)
                    } else {
                        sheetField("Amount in (\(symbol))", $amount, "0", numeric: true)
                    }
                    sheetField("Rate (% a year)", $rate, "e.g. 7.1")

                    sheetRow("Started") {
                        DatePicker("", selection: $started, displayedComponents: .date)
                            .labelsHidden().tint(accent.main)
                    }

                    sheetRow("Has a maturity date") {
                        Button { hasMaturity.toggle() } label: {
                            KToggle(on: hasMaturity, tint: accent.main)
                        }
                        .buttonStyle(.plain)
                    }
                    if hasMaturity {
                        sheetRow("Matures") {
                        DatePicker("", selection: $matures, displayedComponents: .date)
                            .labelsHidden().tint(accent.main)
                    }
                    }

                    sheetField("Worth today (\(symbol))", $value, "optional", numeric: true)
                    Text("Only needed for a return. There is no price feed, so this is whatever you last saw on a statement.")
                        .font(KFont.sans(11)).foregroundStyle(KC.faint)
                        .fixedSize(horizontal: false, vertical: true)

                    PrimaryButton(label: "Save holding", enabled: canSave) {
                        onSave(Investment(
                            name: name.trimmingCharacters(in: .whitespaces),
                            kind: kind,
                            investedAmount: Int(amount) ?? 0,
                            monthlyAmount: kind.recurring ? Int(monthly) : nil,
                            ratePercent: Double(rate),
                            startedOn: started,
                            maturesOn: hasMaturity ? matures : nil,
                            currentValue: Int(value),
                            valuedOn: Int(value) == nil ? nil : .now
                        ))
                        dismiss()
                    }
                    .padding(.top, 4)
                }
                .padding(20)
            }
            .background(KC.surface)
            .navigationTitle("Add a holding")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(accent.deep)
                }
            }
        }
    }

    private var canSave: Bool {
        guard !name.trimmingCharacters(in: .whitespaces).isEmpty else { return false }
        return kind.recurring ? (Int(monthly) ?? 0) > 0 : (Int(amount) ?? 0) > 0
    }
}
