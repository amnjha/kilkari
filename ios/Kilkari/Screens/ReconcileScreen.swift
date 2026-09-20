import SwiftUI
import SwiftData

/// Checking the app's record of an account against a bank statement.
///
/// The balance here is derived from what has been entered, so until now the only way to know it
/// was right was to add it up by hand. Each movement up to the statement date can be ticked, the
/// ticked total is compared against the closing balance off the statement, and the difference is
/// shown until it is nothing. Finishing stamps the ticked lines with the statement date, so the
/// next pass starts where this one left off.
///
/// Only deposits and withdrawals are ticked. Expenses and holdings come off the balance too, but
/// they are entered from their own screens and would not appear on a bank statement as lines the
/// parent recognises.
struct ReconcileScreen: View {
    let accounts: [FundAccount]
    let movements: [FundDeposit]

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent
    @State private var prefs = Preferences.shared

    @State private var accountIndex = 0
    @State private var statementDate = Date.now
    @State private var closingBalance = ""
    /// Ticks live for the length of the visit and are written only when it is finished, so a
    /// half-done pass over a statement leaves nothing behind.
    @State private var ticked: [String: Bool] = [:]

    private var account: FundAccount? {
        accounts.indices.contains(accountIndex) ? accounts[accountIndex] : accounts.first
    }

    private var lines: [FundDeposit] {
        guard let account else { return [] }
        return movements
            .filter { $0.accountId == account.uuid }
            .filter { $0.date <= endOfStatementDay }
            .sorted { $0.date == $1.date ? $0.uuid < $1.uuid : $0.date < $1.date }
    }

    /// The statement date means the whole of that day, not the instant it was picked.
    private var endOfStatementDay: Date {
        Calendar.current.date(bySettingHour: 23, minute: 59, second: 59, of: statementDate)
            ?? statementDate
    }

    private func isTicked(_ movement: FundDeposit) -> Bool {
        ticked[movement.uuid] ?? (movement.reconciledOn != nil)
    }

    private var cleared: Int { lines.filter(isTicked).reduce(0) { $0 + $1.amount } }

    private var statement: Int? { closingBalance.isEmpty ? nil : Int(closingBalance) }

    private var difference: Int? { statement.map { $0 - cleared } }

    private var changes: [FundDeposit] {
        lines.filter { isTicked($0) != ($0.reconciledOn != nil) }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if account == nil {
                        KCard {
                            Text("No account to check yet. Record a deposit first.")
                                .font(KFont.sans(13)).foregroundStyle(KC.muted).padding(14)
                        }
                    } else {
                        content
                    }
                }
                .padding(.horizontal, 16).padding(.top, 10).padding(.bottom, 28)
            }
            .background(KC.screen)
            .navigationTitle("Check against a statement")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }.tint(accent.deep)
                }
            }
        }
    }

    @ViewBuilder private var content: some View {
        if accounts.count > 1 {
            Picker("", selection: $accountIndex) {
                ForEach(Array(accounts.enumerated()), id: \.offset) { i, account in
                    Text(account.name).tag(i)
                }
            }
            .pickerStyle(.segmented)
        }

        KCard {
            VStack(spacing: 10) {
                sheetRow("Statement date") {
                    DatePicker("", selection: $statementDate, in: ...Date.now,
                               displayedComponents: .date)
                        .labelsHidden().tint(accent.main)
                }
                sheetField("Closing balance (\(prefs.currency.symbol))", $closingBalance, "0",
                           numeric: true)
            }
            .padding(14)
        }

        KCard(background: difference == 0 ? KC.tealBg : KC.surface) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Ticked so far: \(prefs.money(cleared))")
                    .font(KFont.sans(15, .bold)).foregroundStyle(KC.ink)
                Text(verdict)
                    .font(KFont.sans(12)).foregroundStyle(difference == 0 ? KC.tealDeep : KC.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(14)
        }

        SectionLabel("Deposits and withdrawals")
        KCard {
            if lines.isEmpty {
                Text("Nothing recorded on this account up to that date.")
                    .font(KFont.sans(13)).foregroundStyle(KC.muted).padding(14)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(lines.enumerated()), id: \.element.persistentModelID) { i, line in
                        row(line)
                        if i != lines.count - 1 { Rectangle().fill(KC.divider).frame(height: 1) }
                    }
                }
            }
        }

        PrimaryButton(label: changes.isEmpty
                      ? "Nothing to save"
                      : "Mark \(changes.count) as checked",
                      enabled: !changes.isEmpty) {
            for line in changes {
                line.reconciledOn = isTicked(line) ? statementDate : nil
            }
            dismiss()
        }
        .padding(.top, 2)

        Text("Expenses and holdings paid from this account come off the balance too. They are "
             + "entered from their own screens, so they are not ticked here.")
            .font(KFont.sans(12)).foregroundStyle(KC.muted)
            .lineSpacing(3).padding(.horizontal, 4)
            .fixedSize(horizontal: false, vertical: true)
    }

    private var verdict: String {
        guard let difference else {
            return "Enter the closing balance from the statement to compare."
        }
        if difference == 0 { return "Matches the statement exactly." }
        return difference > 0
            ? "\(prefs.money(abs(difference))) on the statement is not ticked here."
            : "\(prefs.money(abs(difference))) ticked here is not on the statement."
    }

    private func row(_ line: FundDeposit) -> some View {
        let on = isTicked(line)
        let incoming = line.amount >= 0
        return Button {
            ticked[line.uuid] = !on
        } label: {
            HStack(spacing: 12) {
                Image(systemName: on ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 22))
                    .foregroundStyle(on ? accent.main : KC.borderStrong)
                VStack(alignment: .leading, spacing: 1) {
                    Text(line.note ?? (incoming ? "Deposit" : "Withdrawal"))
                        .font(KFont.sans(14, .semibold)).foregroundStyle(KC.ink).lineLimit(1)
                    Text([Fmt.date(line.date),
                          line.reconciledOn.map { "checked \(Fmt.date($0))" }]
                        .compactMap { $0 }.joined(separator: " · "))
                        .font(KFont.sans(12)).foregroundStyle(KC.muted)
                }
                Spacer()
                Text((incoming ? "+" : "−") + prefs.money(abs(line.amount)))
                    .font(KFont.sans(14, .bold))
                    .foregroundStyle(incoming ? KC.tealDeep : KC.ink)
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
