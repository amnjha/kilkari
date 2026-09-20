import SwiftUI
import SwiftData
import KilkariCore

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
    @Query(sort: \Investment.startedOn) private var investments: [Investment]

    @State private var tab = SampleData.moneySegment
    @State private var filter: ExpenseCategory?
    @State private var addingExpense = false
    @State private var addingDeposit = false
    @State private var addingAccount = false
    @State private var transferring = false
    @State private var editingPlan = false
    @State private var reconciling = false
    /// Each is non-null while the entry it holds is open for correction. Collected here rather
    /// than in the view so a ledger line can be traced back to whichever of them wrote it.
    @State private var editingAccount: FundAccount?
    @State private var editingMovement: FundDeposit?
    @State private var editingExpense: Expense?
    @State private var editingHolding: Investment?

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
                PrimaryButton(label: tab == 0 ? "Add an expense" : "Record a movement") {
                    if tab == 0 { addingExpense = true } else { addingDeposit = true }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 96)
            }
        }
        .sheet(isPresented: $addingExpense) {
            AddExpenseSheet(accounts: openAccounts) { context.insert($0); addingExpense = false }
                .presentationDetents([.medium, .large])
                .environment(\.accent, accent)
        }
        .sheet(isPresented: $addingDeposit) {
            MovementSheet(accounts: openAccounts, balances: accountBalances,
                          fundName: prefs.fundName, suggested: prefs.fundMonthly) { movement in
                context.insert(movement)
                addingDeposit = false
            }
            .presentationDetents([.medium, .large])
            .environment(\.accent, accent)
        }
        .sheet(item: $editingMovement) { movement in
            MovementSheet(accounts: openAccounts, balances: accountBalances,
                          fundName: prefs.fundName, suggested: 0, existing: movement,
                          onDelete: { context.delete(movement); editingMovement = nil }) { _ in
                editingMovement = nil
            }
            .presentationDetents([.medium, .large])
            .environment(\.accent, accent)
        }
        .sheet(item: $editingExpense) { expense in
            AddExpenseSheet(accounts: openAccounts, existing: expense,
                            onDelete: { context.delete(expense); editingExpense = nil }) { _ in
                editingExpense = nil
            }
            .presentationDetents([.medium, .large])
            .environment(\.accent, accent)
        }
        .sheet(item: $editingHolding) { holding in
            HoldingFundingSheet(holding: holding, accounts: openAccounts,
                                fundName: prefs.fundName) { editingHolding = nil }
                .presentationDetents([.medium])
                .environment(\.accent, accent)
        }
        .sheet(isPresented: $addingAccount) {
            FundAccountSheet(order: accounts.count) { account in
                context.insert(account)
                addingAccount = false
            }
            .presentationDetents([.medium])
            .environment(\.accent, accent)
        }
        .sheet(item: $editingAccount) { account in
            FundAccountSheet(order: accounts.count, existing: account,
                             onDelete: { context.delete(account); editingAccount = nil }) { _ in
                editingAccount = nil
            }
            .presentationDetents([.medium])
            .environment(\.accent, accent)
        }
        .sheet(isPresented: $editingPlan) {
            FundPlanSheet().presentationDetents([.medium]).environment(\.accent, accent)
        }
        .sheet(isPresented: $reconciling) {
            ReconcileScreen(accounts: openAccounts, movements: deposits)
                .environment(\.accent, accent)
        }
        .sheet(isPresented: $transferring) {
            TransferSheet(accounts: openAccounts, balances: accountBalances) { from, to, amount, date, note in
                // Two movements, one group: the total does not change, and the pair can be
                // recognised — and undone — as one thing later.
                let group = UUID().uuidString
                let said = note.map { " · \($0)" } ?? ""
                context.insert(FundDeposit(note: "To \(to.name)" + said, amount: -amount,
                                           date: date, accountId: from.uuid,
                                           transferGroup: group))
                context.insert(FundDeposit(note: "From \(from.name)" + said, amount: amount,
                                           date: date, accountId: to.uuid,
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

    // ── The fund ─────────────────────────────────────────────────────────────
    //
    // Every figure below is derived by FundMath from the rows themselves, so a correction
    // anywhere moves the balance with it. The same arithmetic runs on Android, and is checked
    // as arithmetic rather than through the screen.

    /// The signed movements, as the arithmetic wants them.
    private var movements: [FundMath.Movement] {
        deposits.map {
            FundMath.Movement(id: "m" + $0.uuid, date: $0.date, amount: $0.amount, note: $0.note,
                              accountId: $0.accountId, group: $0.transferGroup,
                              reconciled: $0.reconciledOn != nil)
        }
    }

    /// Everything the fund has paid for: expenses marked as coming out of it, and each
    /// instalment of a holding funded from it.
    ///
    /// Contributions are derived from the holding's plan rather than stored as rows — a SIP is
    /// a standing instruction, not a series of typed entries — so they are worked out here and
    /// carry the holding's id with the month on the end, which keeps a line stable.
    private var outgoings: [FundMath.Outgoing] {
        let fromExpenses = expenses.filter(\.paidFromFund).map {
            FundMath.Outgoing(id: "e" + $0.uuid, date: $0.date, title: $0.title,
                              subtitle: [$0.category.label, $0.vendor].compactMap { $0 }
                                  .joined(separator: " · "),
                              amount: $0.amount, accountId: $0.accountId)
        }
        let fromHoldings = investments.filter(\.paidFromFund).flatMap { holding in
            Money.instalments(of: holding).map { instalment in
                FundMath.Outgoing(id: "c\(holding.uuid).\(instalment.index)",
                                  date: instalment.date, title: holding.name,
                                  subtitle: holding.kind.label, amount: instalment.amount,
                                  accountId: holding.accountId, investment: true)
            }
        }
        return fromExpenses + fromHoldings
    }

    private var fundBalanceTotal: Int { FundMath.total(movements: movements, outgoings: outgoings) }

    private var accountBalances: [String: Int] {
        FundMath.balances(movements: movements, outgoings: outgoings)
    }

    private var unassigned: Int {
        FundMath.unassigned(movements: movements, outgoings: outgoings,
                            accountIds: Set(accounts.map(\.uuid)))
    }

    private var ledger: [FundMath.Line] {
        FundMath.ledger(movements: movements, outgoings: outgoings)
    }

    /// Only the two halves of a transfer are excluded — they move between accounts without
    /// entering or leaving the fund, and counting them would double every month's figures.
    private func thisMonth(incoming: Bool) -> Int {
        let cal = Calendar.current
        return ledger
            .filter { !$0.transfer && $0.incoming == incoming }
            .filter { cal.isDate($0.date, equalTo: .now, toGranularity: .month) }
            .reduce(0) { $0 + $1.amount }
    }

    private var openAccounts: [FundAccount] { accounts.filter { !$0.archived } }

    @ViewBuilder private var fund: some View {
        GradientCard(colors: [KC.goldDeep, KC.gold]) {
            VStack(alignment: .leading, spacing: 8) {
                Text(prefs.fundName.uppercased())
                    .font(KFont.sans(12, .semibold)).tracking(0.5)
                    .foregroundStyle(.white.opacity(0.85))
                Text(prefs.money(fundBalanceTotal))
                    .font(KFont.number(34)).foregroundStyle(.white)
                HStack(spacing: 8) {
                    fundStat("In this month", prefs.money(thisMonth(incoming: true)))
                    fundStat("Out this month", prefs.money(thisMonth(incoming: false)))
                }
                if fundBalanceTotal < 0 {
                    Text("More has gone out than in — record the deposits you have made.")
                        .font(KFont.sans(12)).foregroundStyle(.white.opacity(0.9))
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }

        Button { editingPlan = true } label: {
            KCard {
                HStack(spacing: 12) {
                    IconBadge(symbol: "calendar", tint: accent.deep, background: accent.bg,
                              size: 36, corner: 10, iconSize: 16)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(prefs.fundMonthly > 0
                             ? "\(prefs.money(prefs.fundMonthly)) a month"
                             : "Set a monthly plan")
                            .font(KFont.sans(15, .bold)).foregroundStyle(KC.ink)
                        Text(prefs.fundMonthly > 0
                             ? depositStatus
                             : "How much you move in each month")
                            .font(KFont.sans(12)).foregroundStyle(KC.muted)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold)).foregroundStyle(KC.stoneLight)
                }
                .padding(14)
            }
        }
        .buttonStyle(SpringPress())

        // Accounts appear once there is more than one. A family that keeps everything in one
        // place never has to meet the idea at all.
        if accounts.count > 1 {
            SectionLabel("Accounts")
            KCard {
                VStack(spacing: 0) {
                    let ordered = openAccounts + accounts.filter(\.archived)
                    ForEach(Array(ordered.enumerated()), id: \.element.persistentModelID) { i, account in
                        Button { editingAccount = account } label: {
                            accountRowLabel(account)
                        }
                        .buttonStyle(.plain)
                        if i != ordered.count - 1 { Rectangle().fill(KC.divider).frame(height: 1) }
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
                        .padding(.horizontal, 14).padding(.vertical, 12)
                    }
                }
            }
        }

        HStack(spacing: 8) {
            secondaryButton("Add account", "building.columns") {
                editingAccount = nil
                addingAccount = true
            }
            if openAccounts.count > 1 {
                secondaryButton("Transfer", "arrow.left.arrow.right") { transferring = true }
            }
        }

        Button { reconciling = true } label: {
            KCard {
                HStack(spacing: 12) {
                    IconBadge(symbol: "checkmark.circle.fill", tint: KC.seaDeep,
                              background: KC.seaBg, size: 36, corner: 10, iconSize: 16)
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Check against a statement")
                            .font(KFont.sans(15, .bold)).foregroundStyle(KC.ink)
                        Text(unreconciled == 0
                             ? "Everything recorded is ticked off"
                             : "\(unreconciled) \(Fmt.plural(unreconciled, "line")) not ticked off yet")
                            .font(KFont.sans(12)).foregroundStyle(KC.muted)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold)).foregroundStyle(KC.stoneLight)
                }
                .padding(14)
            }
        }
        .buttonStyle(SpringPress())

        SectionLabel("Account activity")
        KCard {
            if ledger.isEmpty {
                Text("Nothing yet. Tap the button below to record a deposit.")
                    .font(KFont.sans(13)).foregroundStyle(KC.muted).padding(14)
            } else {
                VStack(spacing: 0) {
                    let shown = Array(ledger.prefix(Self.ledgerShown))
                    ForEach(Array(shown.enumerated()), id: \.element.id) { i, line in
                        ledgerRow(line)
                        if i != shown.count - 1 { Rectangle().fill(KC.divider).frame(height: 1) }
                    }
                }
            }
        }

        Text("Expenses and holdings marked as paid from \(prefs.fundName) come off this balance "
             + "automatically — they are not recorded twice.")
            .font(KFont.sans(12)).foregroundStyle(KC.muted)
            .lineSpacing(3).padding(.horizontal, 4)
            .fixedSize(horizontal: false, vertical: true)
    }

    /// How many lines of the ledger are worth showing before it stops being a summary.
    private static let ledgerShown = 12

    private var unreconciled: Int { deposits.filter { $0.reconciledOn == nil }.count }

    /// Where the standing deposit stands this month: done, due, or late. Once this month's is
    /// recorded the line looks ahead to next month instead.
    private var depositStatus: String {
        let cal = Calendar.current
        let day = min(max(prefs.fundDepositDay, 1), 28)
        var parts = cal.dateComponents([.year, .month], from: .now)
        parts.day = day
        guard let due = cal.date(from: parts) else { return "" }
        let paidThisMonth = deposits.contains {
            $0.amount > 0 && $0.transferGroup == nil
                && cal.isDate($0.date, equalTo: .now, toGranularity: .month)
        }
        if paidThisMonth {
            let next = cal.date(byAdding: .month, value: 1, to: due) ?? due
            return "Done this month · next \(Fmt.date(next))"
        }
        if cal.isDateInToday(due) { return "Due today" }
        return due < .now ? "Overdue since \(Fmt.date(due))" : "Due \(Fmt.date(due))"
    }

    private func fundStat(_ caption: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(caption).font(KFont.sans(11)).foregroundStyle(.white.opacity(0.85))
            Text(value).font(KFont.sans(16, .bold)).foregroundStyle(.white).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(.white.opacity(0.16))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func secondaryButton(_ label: String, _ symbol: String,
                                 action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(label, systemImage: symbol)
                .font(KFont.sans(13, .semibold)).foregroundStyle(accent.deep)
                .frame(maxWidth: .infinity).frame(height: 42)
                .background(KC.surface).clipShape(Capsule())
                .overlay(Capsule().strokeBorder(accent.ring, lineWidth: 1))
        }
        .buttonStyle(SpringPress())
    }

    private func accountRowLabel(_ account: FundAccount) -> some View {
        let balance = accountBalances[account.uuid] ?? 0
        return HStack(spacing: 12) {
            IconBadge(symbol: "building.columns.fill",
                      tint: account.archived ? KC.muted : KC.goldDeep,
                      background: account.archived ? KC.stoneBg : KC.goldBg,
                      size: 36, corner: 10, iconSize: 16)
            VStack(alignment: .leading, spacing: 1) {
                Text(account.archived ? "\(account.name) · archived" : account.name)
                    .font(KFont.sans(14, .semibold))
                    .foregroundStyle(account.archived ? KC.muted : KC.ink)
                    .lineLimit(1)
                if let note = account.note, !note.isEmpty {
                    Text(note).font(KFont.sans(12)).foregroundStyle(KC.muted).lineLimit(1)
                }
            }
            Spacer()
            Text(prefs.money(balance))
                .font(KFont.sans(14, .bold))
                .foregroundStyle(balance < 0 ? KC.danger : KC.ink)
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .contentShape(Rectangle())
    }

    @ViewBuilder private func ledgerRow(_ line: FundMath.Line) -> some View {
        let look = Self.look(line)
        Button {
            open(line)
        } label: {
            HStack(spacing: 12) {
                IconBadge(symbol: look.symbol, tint: look.tint, background: look.background,
                          size: 36, corner: 10, iconSize: 18)
                VStack(alignment: .leading, spacing: 1) {
                    Text(line.title)
                        .font(KFont.sans(14, .semibold)).foregroundStyle(KC.ink).lineLimit(1)
                    // Date and what the line is, and no account name: the note on a transfer
                    // already says where it went, and a third part only truncates the second.
                    Text([Fmt.date(line.date), line.subtitle.isEmpty ? nil : line.subtitle]
                        .compactMap { $0 }.joined(separator: " · "))
                        .font(KFont.sans(12)).foregroundStyle(KC.muted).lineLimit(1)
                }
                Spacer()
                Text((line.incoming ? "+" : "−") + prefs.money(line.amount))
                    .font(KFont.sans(14, .bold))
                    .foregroundStyle(line.incoming ? KC.tealDeep : KC.ink)
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private static func look(_ line: FundMath.Line) -> (symbol: String, tint: Color, background: Color) {
        if line.transfer { return ("arrow.left.arrow.right", KC.clayDeep, KC.clayBg) }
        switch line.origin {
        case .deposit: return ("arrow.down.circle.fill", KC.tealDeep, KC.tealBg)
        case .withdrawal: return ("arrow.up.circle.fill", KC.dangerDeep, KC.dangerBg)
        case .expense: return ("bag.fill", KC.seaMid, KC.seaBg)
        case .investment: return ("chart.line.uptrend.xyaxis", KC.clayDeep, KC.clayBg)
        }
    }

    /// A line opens whatever wrote it, so a mistake is corrected where it was made.
    private func open(_ line: FundMath.Line) {
        switch line.origin {
        case .deposit, .withdrawal:
            editingMovement = deposits.first { "m" + $0.uuid == line.id }
        case .expense:
            editingExpense = expenses.first { "e" + $0.uuid == line.id }
        case .investment:
            // Derived from the holding's plan rather than typed, so there is no single
            // instalment to correct — the plan behind it is what changes.
            editingHolding = investments.first { line.id.hasPrefix("c" + $0.uuid + ".") }
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
    let accounts: [FundAccount]
    var existing: Expense?
    var onDelete: (() -> Void)?
    let onSave: (Expense) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent

    @State private var category: ExpenseCategory = .general
    @State private var amount = ""
    @State private var title = ""
    @State private var vendor = ""
    @State private var date = Date.now
    @State private var fromFund = true
    @State private var accountId: String?
    @State private var started = false

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

                    if fromFund {
                        accountChoice("Out of", accounts, [:], Preferences.shared, $accountId)
                    }

                    PrimaryButton(label: existing == nil ? "Save expense" : "Save changes",
                                  enabled: Int(amount) ?? 0 > 0) { save() }
                        .padding(.top, 4)

                    if let onDelete {
                        SheetDelete(label: "Delete this expense") { onDelete(); dismiss() }
                    }
                }
                .padding(20)
            }
            .background(KC.surface)
            .onAppear(perform: prime)
            .navigationTitle(existing == nil ? "Add expense" : "Edit expense")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.tint(accent.deep)
                }
            }
        }
    }

    private func prime() {
        guard !started, let existing else { started = true; accountId = accounts.first?.uuid; return }
        started = true
        category = existing.category
        amount = String(existing.amount)
        title = existing.title
        vendor = existing.vendor ?? ""
        date = existing.date
        fromFund = existing.paidFromFund
        accountId = existing.accountId ?? accounts.first?.uuid
    }

    private func save() {
        let cleanTitle = title.trimmingCharacters(in: .whitespaces)
        let resolved = cleanTitle.isEmpty
            ? (category == .medical ? "Medical expense" : "General expense")
            : cleanTitle
        let cleanVendor = vendor.trimmingCharacters(in: .whitespaces)
        let account = fromFund ? (accountId ?? accounts.first?.uuid) : nil
        if let existing {
            existing.title = resolved
            existing.vendor = cleanVendor.isEmpty ? nil : cleanVendor
            existing.categoryRaw = category.rawValue
            existing.amount = Int(amount) ?? 0
            existing.date = date
            existing.paidFromFund = fromFund
            existing.accountId = account
            onSave(existing)
        } else {
            onSave(Expense(title: resolved, vendor: cleanVendor.isEmpty ? nil : cleanVendor,
                           category: category, amount: Int(amount) ?? 0, date: date,
                           paidFromFund: fromFund, accountId: account))
        }
        dismiss()
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
