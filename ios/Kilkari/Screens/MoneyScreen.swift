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
    @Query(sort: \FundAccount.sortOrder) private var accounts: [FundAccount]

    @State private var tab = SampleData.moneySegment
    @State private var filter: ExpenseCategory?
    @State private var addingExpense = false
    @State private var addingDeposit = false
    @State private var addingAccount = false
    @State private var transferring = false

    private var thisMonth: [Expense] {
        let cal = Calendar.current
        return expenses.filter { cal.isDate($0.date, equalTo: .now, toGranularity: .month) }
    }

    private var shown: [Expense] {
        guard let filter else { return thisMonth }
        return thisMonth.filter { $0.category == filter }
    }

    private func total(_ items: [Expense]) -> Int { items.reduce(0) { $0 + $1.amount } }

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
            AddExpenseSheet(accounts: accounts) { context.insert($0); addingExpense = false }
                .presentationDetents([.medium, .large])
                .environment(\.accent, accent)
        }
        .sheet(isPresented: $addingDeposit) {
            AddDepositSheet(accounts: accounts) { context.insert($0); addingDeposit = false }
                .presentationDetents([.medium, .large])
                .environment(\.accent, accent)
        }
        .sheet(isPresented: $addingAccount) {
            AddAccountSheet(order: accounts.count) { context.insert($0); addingAccount = false }
                .presentationDetents([.medium])
                .environment(\.accent, accent)
        }
        .sheet(isPresented: $transferring) {
            TransferSheet(accounts: accounts) { from, to, amount, date in
                // Two movements, one group: the total does not change, and the pair can be
                // recognised — and undone — as one thing later.
                let group = UUID().uuidString
                context.insert(FundDeposit(note: "To \(to.name)", amount: -amount, date: date,
                                           accountId: from.uuid,
                                           transferGroup: group))
                context.insert(FundDeposit(note: "From \(from.name)", amount: amount, date: date,
                                           accountId: to.uuid,
                                           transferGroup: group))
                transferring = false
            }
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

    /// Deposits in, less everything paid out of the fund.
    private var fundBalanceTotal: Int {
        deposits.reduce(0) { $0 + $1.amount }
            - expenses.filter(\.paidFromFund).reduce(0) { $0 + $1.amount }
    }

    /// The same sum per account. A transfer falls out of this without a special case: one
    /// account is down and the other up by the same amount, and the total does not move.
    private func balance(of account: FundAccount) -> Int {
        let id = account.uuid
        return deposits.filter { $0.accountId == id }.reduce(0) { $0 + $1.amount }
            - expenses.filter { $0.paidFromFund && $0.accountId == id }.reduce(0) { $0 + $1.amount }
    }

    /// Money in the fund that predates any account, or was never assigned to one.
    private var unassigned: Int {
        let ids = Set(accounts.map(\.uuid))
        return deposits.filter { $0.accountId.map { !ids.contains($0) } ?? true }
                .reduce(0) { $0 + $1.amount }
            - expenses.filter { $0.paidFromFund && ($0.accountId.map { !ids.contains($0) } ?? true) }
                .reduce(0) { $0 + $1.amount }
    }

    @ViewBuilder private var fund: some View {
        GradientCard(colors: [KC.goldDeep, KC.gold]) {
            VStack(alignment: .leading, spacing: 6) {
                Text("IN THE FUND")
                    .font(KFont.sans(12, .semibold)).tracking(0.5)
                    .foregroundStyle(.white.opacity(0.85))
                Text(prefs.money(fundBalanceTotal))
                    .font(KFont.number(34)).foregroundStyle(.white)
                Text(accounts.isEmpty
                     ? "Deposits in, less everything paid out of it."
                     : "Across \(accounts.count) \(Fmt.plural(accounts.count, "account")).")
                    .font(KFont.sans(13)).foregroundStyle(.white.opacity(0.9))
            }
        }

        if !accounts.isEmpty {
            SectionLabel("Accounts")
            KCard {
                VStack(spacing: 0) {
                    ForEach(Array(accounts.enumerated()), id: \.element.persistentModelID) { i, account in
                        HStack(spacing: 12) {
                            IconBadge(symbol: "building.columns.fill", tint: KC.goldDeep,
                                      background: KC.goldBg, size: 36, corner: 10, iconSize: 16)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(account.name)
                                    .font(KFont.sans(14, .semibold)).foregroundStyle(KC.ink)
                                if let note = account.note, !note.isEmpty {
                                    Text(note).font(KFont.sans(12)).foregroundStyle(KC.muted)
                                }
                            }
                            Spacer()
                            Text(prefs.money(balance(of: account)))
                                .font(KFont.sans(14, .bold)).foregroundStyle(KC.ink)
                        }
                        .padding(.horizontal, 14).padding(.vertical, 11)
                        .swipeActions {
                            Button("Delete", role: .destructive) { context.delete(account) }
                        }
                        if i != accounts.count - 1 { Rectangle().fill(KC.divider).frame(height: 1) }
                    }
                    if unassigned != 0 {
                        Rectangle().fill(KC.divider).frame(height: 1)
                        HStack {
                            Text("Not in an account")
                                .font(KFont.sans(14)).foregroundStyle(KC.mutedStrong)
                            Spacer()
                            Text(prefs.money(unassigned))
                                .font(KFont.sans(14, .semibold)).foregroundStyle(KC.mutedStrong)
                        }
                        .padding(.horizontal, 14).padding(.vertical, 11)
                    }
                }
            }
        }

        HStack(spacing: 8) {
            Button { addingAccount = true } label: {
                Label("Add an account", systemImage: "plus")
                    .font(KFont.sans(13, .semibold)).foregroundStyle(accent.deep)
                    .frame(maxWidth: .infinity).frame(height: 42)
                    .background(KC.surface).clipShape(Capsule())
                    .overlay(Capsule().strokeBorder(accent.ring, lineWidth: 1))
            }
            if accounts.count >= 2 {
                Button { transferring = true } label: {
                    Label("Transfer", systemImage: "arrow.left.arrow.right")
                        .font(KFont.sans(13, .semibold)).foregroundStyle(accent.deep)
                        .frame(maxWidth: .infinity).frame(height: 42)
                        .background(KC.surface).clipShape(Capsule())
                        .overlay(Capsule().strokeBorder(accent.ring, lineWidth: 1))
                }
            }
        }
        .buttonStyle(SpringPress())

        SectionLabel("Movements")
        KCard {
            if deposits.isEmpty {
                Text("Nothing put in yet.")
                    .font(KFont.sans(13)).foregroundStyle(KC.muted).padding(14)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(deposits.enumerated()), id: \.element.persistentModelID) { i, movement in
                        let out = movement.amount < 0
                        HStack {
                            IconBadge(
                                symbol: movement.transferGroup != nil
                                    ? "arrow.left.arrow.right"
                                    : (out ? "arrow.up.circle.fill" : "arrow.down.circle.fill"),
                                tint: out ? KC.clayDeep : KC.goldDeep,
                                background: out ? KC.clayBg : KC.goldBg,
                                size: 36, corner: 10, iconSize: 16
                            )
                            VStack(alignment: .leading, spacing: 1) {
                                Text(movement.note ?? (out ? "Withdrawal" : "Deposit"))
                                    .font(KFont.sans(14, .semibold)).foregroundStyle(KC.ink)
                                Text([Fmt.date(movement.date), accountName(movement.accountId)]
                                    .compactMap { $0 }.joined(separator: " · "))
                                    .font(KFont.sans(12)).foregroundStyle(KC.muted)
                            }
                            Spacer()
                            Text((out ? "−" : "+") + prefs.money(abs(movement.amount)))
                                .font(KFont.sans(14, .semibold))
                                .foregroundStyle(out ? KC.clayDeep : KC.leafDeep)
                        }
                        .padding(.horizontal, 14).padding(.vertical, 11)
                        if i != deposits.count - 1 { Rectangle().fill(KC.divider).frame(height: 1) }
                    }
                }
            }
        }
    }

    private func accountName(_ id: String?) -> String? {
        guard let id else { return nil }
        return accounts.first { $0.uuid == id }?.name
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
    let accounts: [FundAccount]
    let onSave: (Expense) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent

    @State private var category: ExpenseCategory = .general
    @State private var amount = ""
    @State private var title = ""
    @State private var vendor = ""
    @State private var date = Date.now
    @State private var fromFund = true
    @State private var account: FundAccount?

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

                    if fromFund && !accounts.isEmpty {
                        accountRow($account, accounts, accent)
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
                            paidFromFund: fromFund,
                            accountId: fromFund
                                ? (account ?? accounts.first)?.uuid
                                : nil
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
    let accounts: [FundAccount]
    let onSave: (FundDeposit) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent

    @State private var amount = ""
    @State private var note = ""
    @State private var date = Date.now
    @State private var account: FundAccount?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    sheetTitle("Add to the fund")
                    sheetField("Amount (\(Preferences.shared.currency.symbol))", $amount, "0",
                               numeric: true, big: true)
                    sheetField("Note", $note, "e.g. Monthly transfer")
                    if !accounts.isEmpty {
                        accountRow($account, accounts, accent)
                    }
                    sheetRow("Date") {
                        DatePicker("", selection: $date, in: ...Date.now, displayedComponents: .date)
                            .labelsHidden().tint(accent.main)
                    }
                    PrimaryButton(label: "Add to the fund", enabled: Int(amount) ?? 0 > 0) {
                        onSave(FundDeposit(
                            note: note.trimmingCharacters(in: .whitespaces).isEmpty ? nil : note,
                            amount: Int(amount) ?? 0,
                            date: date,
                            accountId: (account ?? accounts.first)?.uuid
                        ))
                        dismiss()
                    }
                    .padding(.top, 4)
                }
                .padding(20)
            }
            .background(KC.surface)
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


/// Which account a movement belongs to, in the same row style as everything else.
@ViewBuilder
func accountRow(_ selection: Binding<FundAccount?>, _ accounts: [FundAccount], _ accent: Accent) -> some View {
    sheetRow("Account") {
        Menu {
            ForEach(accounts) { account in
                Button(account.name) { selection.wrappedValue = account }
            }
        } label: {
            HStack(spacing: 4) {
                Text((selection.wrappedValue ?? accounts.first)?.name ?? "None")
                    .font(KFont.sans(14, .bold)).foregroundStyle(KC.ink)
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .semibold)).foregroundStyle(accent.deep)
            }
        }
    }
}

struct AddAccountSheet: View {
    let order: Int
    let onSave: (FundAccount) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent

    @State private var name = ""
    @State private var note = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    sheetTitle("Add an account")
                    sheetHint("Somewhere the fund's money actually sits — a bank account, an envelope, a wallet.")
                    sheetField("Name", $name, "e.g. SBI savings")
                    sheetField("Note", $note, "Optional")
                    PrimaryButton(label: "Save account",
                                  enabled: !name.trimmingCharacters(in: .whitespaces).isEmpty) {
                        let n = note.trimmingCharacters(in: .whitespaces)
                        onSave(FundAccount(name: name.trimmingCharacters(in: .whitespaces),
                                           note: n.isEmpty ? nil : n, sortOrder: order))
                        dismiss()
                    }
                    .padding(.top, 2)
                }
                .padding(20)
            }
            .background(KC.surface)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(accent.deep)
                }
            }
        }
    }
}

/// Moving money between two accounts. Not a deposit and not a withdrawal: the fund's total
/// does not change, and the screen should not pretend it did.
struct TransferSheet: View {
    let accounts: [FundAccount]
    let onTransfer: (FundAccount, FundAccount, Int, Date) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent

    @State private var from: FundAccount?
    @State private var to: FundAccount?
    @State private var amount = ""
    @State private var date = Date.now

    private var resolvedFrom: FundAccount? { from ?? accounts.first }
    private var resolvedTo: FundAccount? { to ?? accounts.dropFirst().first }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    sheetTitle("Move money")
                    sheetField("Amount (\(Preferences.shared.currency.symbol))", $amount, "0",
                               numeric: true, big: true)
                    picker("From", $from, resolvedFrom)
                    picker("To", $to, resolvedTo)
                    sheetRow("Date") {
                        DatePicker("", selection: $date, in: ...Date.now, displayedComponents: .date)
                            .labelsHidden().tint(accent.main)
                    }

                    if resolvedFrom?.persistentModelID == resolvedTo?.persistentModelID {
                        Text("Pick two different accounts.")
                            .font(KFont.sans(12)).foregroundStyle(KC.danger)
                    }

                    PrimaryButton(label: "Move it", enabled: valid) {
                        if let f = resolvedFrom, let t = resolvedTo, let value = Int(amount) {
                            onTransfer(f, t, value, date)
                        }
                        dismiss()
                    }
                    .padding(.top, 2)
                }
                .padding(20)
            }
            .background(KC.surface)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(accent.deep)
                }
            }
        }
    }

    private var valid: Bool {
        (Int(amount) ?? 0) > 0
            && resolvedFrom != nil && resolvedTo != nil
            && resolvedFrom?.persistentModelID != resolvedTo?.persistentModelID
    }

    private func picker(_ label: String, _ binding: Binding<FundAccount?>,
                        _ resolved: FundAccount?) -> some View {
        sheetRow(label) {
            Menu {
                ForEach(accounts) { account in
                    Button(account.name) { binding.wrappedValue = account }
                }
            } label: {
                HStack(spacing: 4) {
                    Text(resolved?.name ?? "Choose")
                        .font(KFont.sans(14, .bold)).foregroundStyle(KC.ink)
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12, weight: .semibold)).foregroundStyle(accent.deep)
                }
            }
        }
    }
}
