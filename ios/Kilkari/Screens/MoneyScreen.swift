import SwiftUI
import SwiftData

/// What has been spent, and what is in the fund.
///
/// Every figure is derived from the rows rather than stored: delete an expense and the
/// balance moves with it, and there is nothing to keep in step.
struct MoneyScreen: View {
    @Environment(\.modelContext) private var context
    @Environment(\.accent) private var accent
    @State private var prefs = Preferences.shared

    @Query(sort: \Expense.date, order: .reverse) private var expenses: [Expense]
    @Query(sort: \FundDeposit.date, order: .reverse) private var deposits: [FundDeposit]

    @State private var tab = SampleData.moneySegment
    @State private var filter: ExpenseCategory?
    @State private var addingExpense = false
    @State private var addingDeposit = false

    private var thisMonth: [Expense] {
        let cal = Calendar.current
        return expenses.filter { cal.isDate($0.date, equalTo: .now, toGranularity: .month) }
    }

    private var shown: [Expense] {
        guard let filter else { return thisMonth }
        return thisMonth.filter { $0.category == filter }
    }

    private func total(_ items: [Expense]) -> Int { items.reduce(0) { $0 + $1.amount } }

    /// Deposits in, less everything paid out of the fund.
    private var fundBalance: Int {
        deposits.reduce(0) { $0 + $1.amount } - expenses.filter(\.paidFromFund).reduce(0) { $0 + $1.amount }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("Money").font(KFont.screenTitle).foregroundStyle(KC.ink)

                Picker("", selection: $tab) {
                    Text("Spending").tag(0)
                    Text("Fund").tag(1)
                    Text("Invest").tag(2)
                }
                .pickerStyle(.segmented)

                switch tab {
                case 0: spending
                case 1: fund
                default: InvestScreen()
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 18)
            .padding(.bottom, 110)
        }
        .safeAreaInset(edge: .bottom) {
            // The holdings tab carries its own add button, because "add a holding" asks for
            // a different form and a button that changes what it does three ways is a button
            // nobody trusts.
            if tab < 2 {
                PrimaryButton(label: tab == 0 ? "Add an expense" : "Add to the fund") {
                    if tab == 0 { addingExpense = true } else { addingDeposit = true }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 96)
            }
        }
        .sheet(isPresented: $addingExpense) {
            AddExpenseSheet { context.insert($0); addingExpense = false }
                .presentationDetents([.medium, .large])
                .environment(\.accent, accent)
        }
        .sheet(isPresented: $addingDeposit) {
            AddDepositSheet { context.insert($0); addingDeposit = false }
                .presentationDetents([.medium])
                .environment(\.accent, accent)
        }
    }

    @ViewBuilder private var spending: some View {
        KCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("Spent this month")
                    .font(KFont.sans(13)).foregroundStyle(KC.muted)
                Text(prefs.money(total(thisMonth)))
                    .font(KFont.number(34)).foregroundStyle(KC.ink)

                // A split bar rather than a pie: two categories, and the question is how they
                // compare, which a length answers faster than an angle.
                let medical = total(thisMonth.filter { $0.category == .medical })
                let general = total(thisMonth.filter { $0.category == .general })
                if medical + general > 0 {
                    GeometryReader { geo in
                        HStack(spacing: 2) {
                            let share = Double(medical) / Double(medical + general)
                            Capsule().fill(KC.coral)
                                .frame(width: max(0, (geo.size.width - 2) * share))
                            Capsule().fill(KC.sea)
                        }
                    }
                    .frame(height: 8)

                    HStack(spacing: 16) {
                        amountLegend(KC.coral, "Medical", medical)
                        amountLegend(KC.sea, "General", general)
                    }
                }
            }
            .padding(16)
        }

        HStack(spacing: 8) {
            KChip(label: "All", selected: filter == nil) { filter = nil }
            ForEach(ExpenseCategory.allCases) { category in
                KChip(label: category.label, selected: filter == category) { filter = category }
            }
        }

        SectionLabel("This month")
        KCard {
            if shown.isEmpty {
                Text("Nothing spent yet this month.")
                    .font(KFont.sans(13)).foregroundStyle(KC.muted).padding(14)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(shown.enumerated()), id: \.element.persistentModelID) { i, expense in
                        HStack(spacing: 12) {
                            IconBadge(symbol: expense.category == .medical ? "cross.case.fill" : "bag.fill",
                                      tint: expense.category.tint,
                                      background: expense.category == .medical ? KC.coralBg : KC.seaBg,
                                      size: 36, corner: 10, iconSize: 16)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(expense.title).font(KFont.sans(14, .semibold)).foregroundStyle(KC.ink)
                                Text([Fmt.date(expense.date), expense.vendor].compactMap { $0 }.joined(separator: " · "))
                                    .font(KFont.sans(12)).foregroundStyle(KC.muted)
                            }
                            Spacer()
                            Text(prefs.money(expense.amount))
                                .font(KFont.sans(14, .semibold)).foregroundStyle(KC.ink)
                        }
                        .padding(.horizontal, 14).padding(.vertical, 11)
                        if i != shown.count - 1 { Rectangle().fill(KC.divider).frame(height: 1) }
                    }
                }
            }
        }
    }

    @ViewBuilder private var fund: some View {
        GradientCard(colors: [KC.goldDeep, KC.gold]) {
            VStack(alignment: .leading, spacing: 6) {
                Text("IN THE FUND")
                    .font(KFont.sans(12, .semibold)).tracking(0.5)
                    .foregroundStyle(.white.opacity(0.85))
                Text(prefs.money(fundBalance))
                    .font(KFont.number(34)).foregroundStyle(.white)
                Text("Deposits in, less everything paid out of it.")
                    .font(KFont.sans(13)).foregroundStyle(.white.opacity(0.9))
            }
        }

        SectionLabel("Deposits")
        KCard {
            if deposits.isEmpty {
                Text("Nothing put in yet.")
                    .font(KFont.sans(13)).foregroundStyle(KC.muted).padding(14)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(deposits.enumerated()), id: \.element.persistentModelID) { i, deposit in
                        HStack {
                            IconBadge(symbol: "arrow.down.circle.fill", tint: KC.goldDeep,
                                      background: KC.goldBg, size: 36, corner: 10, iconSize: 16)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(deposit.note ?? "Deposit")
                                    .font(KFont.sans(14, .semibold)).foregroundStyle(KC.ink)
                                Text(Fmt.date(deposit.date))
                                    .font(KFont.sans(12)).foregroundStyle(KC.muted)
                            }
                            Spacer()
                            Text("+" + prefs.money(deposit.amount))
                                .font(KFont.sans(14, .semibold)).foregroundStyle(KC.leafDeep)
                        }
                        .padding(.horizontal, 14).padding(.vertical, 11)
                        if i != deposits.count - 1 { Rectangle().fill(KC.divider).frame(height: 1) }
                    }
                }
            }
        }
    }

    private func amountLegend(_ colour: Color, _ label: String, _ amount: Int) -> some View {
        HStack(spacing: 6) {
            Circle().fill(colour).frame(width: 8, height: 8)
            Text(label).font(KFont.sans(12)).foregroundStyle(KC.mutedStrong)
            Text(prefs.money(amount)).font(KFont.sans(12, .semibold)).foregroundStyle(KC.ink)
        }
    }
}

struct AddExpenseSheet: View {
    let onSave: (Expense) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent

    @State private var category: ExpenseCategory = .general
    @State private var amount = ""
    @State private var title = ""
    @State private var vendor = ""
    @State private var date = Date.now
    @State private var fromFund = true

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Picker("", selection: $category) {
                        ForEach(ExpenseCategory.allCases) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)

                    sheetField("Amount (\(Preferences.shared.currency.symbol))", $amount, "0", numeric: true)
                    sheetField("For", $title, "e.g. Diapers, size 1")
                    sheetField("Where", $vendor, "Shop or clinic")

                    sheetRow("Date") {
                        DatePicker("", selection: $date, in: ...Date.now, displayedComponents: .date)
                            .labelsHidden().tint(accent.main)
                    }

                    sheetRow("Paid from the fund") {
                        Button { fromFund.toggle() } label: {
                            KToggle(on: fromFund, tint: accent.main)
                        }
                        .buttonStyle(.plain)
                    }

                    PrimaryButton(label: "Save expense", enabled: Int(amount) ?? 0 > 0) {
                        onSave(Expense(
                            title: title.trimmingCharacters(in: .whitespaces).isEmpty
                                ? (category == .medical ? "Medical expense" : "General expense")
                                : title.trimmingCharacters(in: .whitespaces),
                            vendor: vendor.trimmingCharacters(in: .whitespaces).isEmpty ? nil : vendor,
                            category: category,
                            amount: Int(amount) ?? 0,
                            date: date,
                            paidFromFund: fromFund
                        ))
                        dismiss()
                    }
                    .padding(.top, 4)
                }
                .padding(20)
            }
            .background(KC.surface)
            .navigationTitle("Add expense")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(accent.deep)
                }
            }
        }
    }
}

struct AddDepositSheet: View {
    let onSave: (FundDeposit) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent

    @State private var amount = ""
    @State private var note = ""
    @State private var date = Date.now

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    sheetField("Amount (\(Preferences.shared.currency.symbol))", $amount, "0", numeric: true)
                    sheetField("Note", $note, "e.g. Monthly transfer")
                    sheetRow("Date") {
                        DatePicker("", selection: $date, in: ...Date.now, displayedComponents: .date)
                            .labelsHidden().tint(accent.main)
                    }
                    PrimaryButton(label: "Add to the fund", enabled: Int(amount) ?? 0 > 0) {
                        onSave(FundDeposit(
                            note: note.trimmingCharacters(in: .whitespaces).isEmpty ? nil : note,
                            amount: Int(amount) ?? 0,
                            date: date
                        ))
                        dismiss()
                    }
                    .padding(.top, 4)
                }
                .padding(20)
            }
            .background(KC.surface)
            .navigationTitle("Add to the fund")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(accent.deep)
                }
            }
        }
    }
}

/// The input every sheet uses: label on the left, value on the right, on a cream field.
///
/// The same shape as Android's, and the reason is the same — a sheet is read down its left
/// edge to find the thing you want to change, and a stack of labels above their fields makes
/// that column twice as tall for no gain. The label stays visible once the field is filled,
/// which a placeholder standing in for one does not.
@ViewBuilder
func sheetField(
    _ label: String,
    _ text: Binding<String>,
    _ prompt: String,
    numeric: Bool = false,
    big: Bool = false
) -> some View {
    HStack(spacing: 10) {
        Text(label)
            .font(KFont.sans(13))
            .foregroundStyle(KC.muted)
        TextField("", text: text, prompt:
            Text(prompt).font(KFont.sans(big ? 18 : 14)).foregroundStyle(KC.faint))
            .keyboardType(numeric ? .numberPad : .default)
            .multilineTextAlignment(.trailing)
            .font(KFont.sans(big ? 18 : 14, .bold))
            .foregroundStyle(KC.ink)
            .tint(KC.coral)
    }
    .padding(.horizontal, 14)
    .padding(.vertical, big ? 10 : 13)
    .background(KC.screen)
    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
}

/// The same row, for a value the parent picks rather than types.
@ViewBuilder
func sheetRow<Trailing: View>(
    _ label: String,
    @ViewBuilder trailing: () -> Trailing
) -> some View {
    HStack(spacing: 10) {
        Text(label)
            .font(KFont.sans(13))
            .foregroundStyle(KC.muted)
        Spacer(minLength: 0)
        trailing()
    }
    .padding(.horizontal, 14)
    .padding(.vertical, 10)
    .background(KC.screen)
    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
}

/// A sheet's heading and the line under it, so every sheet opens the same way.
@ViewBuilder
func sheetTitle(_ text: String) -> some View {
    Text(text)
        .font(KFont.display(20, .bold))
        .foregroundStyle(KC.ink)
        .frame(maxWidth: .infinity, alignment: .leading)
}

@ViewBuilder
func sheetHint(_ text: String) -> some View {
    Text(text)
        .font(KFont.sans(13))
        .foregroundStyle(KC.muted)
        .fixedSize(horizontal: false, vertical: true)
        .frame(maxWidth: .infinity, alignment: .leading)
}
