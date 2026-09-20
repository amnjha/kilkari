import SwiftUI
import SwiftData

// ── Shared pieces ────────────────────────────────────────────────────────────

/// A heading over a group of choices inside a sheet.
@ViewBuilder
func sheetLabel(_ text: String) -> some View {
    Text(text.uppercased())
        .font(KFont.sans(11, .semibold)).tracking(0.6)
        .foregroundStyle(KC.faint)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 2)
}

/// The dot beside a choice. A ring rather than a tick, because several of these sit in a
/// column and only one of them is on.
struct RadioDot: View {
    let on: Bool
    @Environment(\.accent) private var accent

    var body: some View {
        ZStack {
            Circle().strokeBorder(on ? accent.main : KC.borderStrong, lineWidth: on ? 6 : 1.5)
        }
        .frame(width: 22, height: 22)
    }
}

/// One pickable row in a sheet: a name, something small underneath, and the dot.
struct SheetChoiceRow<Trailing: View>: View {
    let title: String
    var subtitle: String?
    let selected: Bool
    let onPick: () -> Void
    @ViewBuilder var trailing: Trailing

    var body: some View {
        Button(action: onPick) {
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 1) {
                    Text(title).font(KFont.sans(14, .semibold)).foregroundStyle(KC.ink)
                    if let subtitle {
                        Text(subtitle).font(KFont.sans(12)).foregroundStyle(KC.muted)
                    }
                }
                Spacer(minLength: 0)
                trailing
            }
            .padding(.horizontal, 14).padding(.vertical, 10)
            .background(KC.screen)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// A switch on its own row, with the reason for it underneath.
struct SheetToggleRow: View {
    let title: String
    var subtitle: String?
    @Binding var on: Bool

    @Environment(\.accent) private var accent

    var body: some View {
        Button { on.toggle() } label: {
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 1) {
                    Text(title).font(KFont.sans(14, .semibold)).foregroundStyle(KC.ink)
                    if let subtitle {
                        Text(subtitle).font(KFont.sans(12)).foregroundStyle(KC.muted)
                            .fixedSize(horizontal: false, vertical: true)
                            .multilineTextAlignment(.leading)
                    }
                }
                Spacer(minLength: 0)
                KToggle(on: on, tint: accent.main)
            }
            .padding(.horizontal, 14).padding(.vertical, 10)
            .background(KC.screen)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// The destructive action at the foot of a sheet that opened something already recorded.
struct SheetDelete: View {
    let label: String
    let action: () -> Void

    var body: some View {
        Button(role: .destructive, action: action) {
            Text(label)
                .font(KFont.sans(14, .semibold)).foregroundStyle(KC.danger)
                .frame(maxWidth: .infinity).frame(height: 44)
        }
        .buttonStyle(.plain)
    }
}

/// The frame every fund sheet sits in, so they all open, scroll and cancel the same way.
struct SheetFrame<Content: View>: View {
    @ViewBuilder var content: Content
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) { content }
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

/// Which of the fund's accounts a sheet is about.
///
/// Only worth asking once there is more than one place the money could sit — a family with a
/// single account should never have to meet the idea.
@ViewBuilder
func accountChoice(_ label: String, _ accounts: [FundAccount], _ balances: [String: Int],
                   _ prefs: Preferences, _ selection: Binding<String?>) -> some View {
    if accounts.count > 1 {
        sheetLabel(label)
        ForEach(accounts) { account in
            SheetChoiceRow(
                title: account.name,
                subtitle: prefs.money(balances[account.uuid] ?? 0),
                selected: account.uuid == selection.wrappedValue,
                onPick: { selection.wrappedValue = account.uuid }
            ) { RadioDot(on: account.uuid == selection.wrappedValue) }
        }
    }
}

// ── Movements ────────────────────────────────────────────────────────────────

/// Money in or out of the fund, independent of any expense.
///
/// One sheet for both directions, and the same one reopens a movement already recorded —
/// including switching it between the two, which is the correction most often needed.
struct MovementSheet: View {
    let accounts: [FundAccount]
    let balances: [String: Int]
    let fundName: String
    /// The standing monthly amount, offered as a starting figure so the commonest deposit is
    /// one tap. Zero when no plan is set, or when an existing movement is being corrected.
    let suggested: Int
    var existing: FundDeposit?
    var onDelete: (() -> Void)?
    let onSave: (FundDeposit) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent
    @State private var prefs = Preferences.shared

    @State private var deposit = true
    @State private var amount = ""
    @State private var note = ""
    @State private var date = Date.now
    @State private var accountId: String?
    @State private var started = false

    var body: some View {
        SheetFrame {
            sheetTitle(title)
            Picker("", selection: $deposit) {
                Text("Deposit").tag(true)
                Text("Withdrawal").tag(false)
            }
            .pickerStyle(.segmented)

            sheetField("Amount (\(prefs.currency.symbol))", $amount, "0", numeric: true, big: true)
            sheetField("Note", $note, deposit ? "Monthly top-up" : "What it was for")
            sheetRow("Date") {
                DatePicker("", selection: $date, in: ...Date.now, displayedComponents: .date)
                    .labelsHidden().tint(accent.main)
            }

            accountChoice(deposit ? "Into" : "Out of", accounts, balances, prefs, $accountId)

            PrimaryButton(label: saveLabel, enabled: (Int(amount) ?? 0) > 0) { save() }
                .padding(.top, 2)

            if let onDelete {
                SheetDelete(label: "Delete this entry") { onDelete(); dismiss() }
            }
        }
        .onAppear(perform: prime)
    }

    private var title: String {
        if existing != nil { return deposit ? "Edit deposit" : "Edit withdrawal" }
        return deposit ? "Add to \(fundName)" : "Take out of \(fundName)"
    }

    private var saveLabel: String {
        if existing != nil { return "Save changes" }
        return deposit ? "Record deposit" : "Record withdrawal"
    }

    /// Filled once, from whatever is being corrected, or from the plan for a new one.
    private func prime() {
        guard !started else { return }
        started = true
        if let existing {
            deposit = existing.amount >= 0
            amount = String(abs(existing.amount))
            note = existing.note ?? ""
            date = existing.date
            accountId = existing.accountId
        } else {
            if suggested > 0 { amount = String(suggested) }
            accountId = accounts.first?.uuid
        }
    }

    private func save() {
        let magnitude = abs(Int(amount) ?? 0)
        let signed = deposit ? magnitude : -magnitude
        let trimmed = note.trimmingCharacters(in: .whitespaces)
        if let existing {
            existing.amount = signed
            existing.note = trimmed.isEmpty ? nil : trimmed
            existing.date = date
            existing.accountId = accountId
            // A corrected line is no longer the one that was ticked off, so it goes back in
            // the pile to be checked again.
            existing.reconciledOn = nil
            onSave(existing)
        } else {
            onSave(FundDeposit(note: trimmed.isEmpty ? nil : trimmed, amount: signed, date: date,
                               accountId: accountId ?? accounts.first?.uuid))
        }
        dismiss()
    }
}

// ── Accounts ─────────────────────────────────────────────────────────────────

/// Adding an account, or opening one already there.
struct FundAccountSheet: View {
    let order: Int
    var existing: FundAccount?
    var onDelete: (() -> Void)?
    let onSave: (FundAccount) -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var note = ""
    @State private var archived = false
    @State private var started = false

    var body: some View {
        SheetFrame {
            sheetTitle(existing.map { "Edit \($0.name)" } ?? "Add an account")
            sheetHint("Where the money actually sits — a bank account, a gift envelope, a cash tin.")
            sheetField("Name", $name, "e.g. Grandparents' gifts")
            sheetField("Note", $note, "Optional — bank, account number, whose it is")

            if existing != nil {
                SheetToggleRow(
                    title: "Archived",
                    subtitle: "Kept out of the way; its balance still counts towards the total",
                    on: $archived
                )
            }

            PrimaryButton(label: existing == nil ? "Add account" : "Save changes",
                          enabled: !name.trimmingCharacters(in: .whitespaces).isEmpty) { save() }
                .padding(.top, 2)

            if let onDelete {
                SheetDelete(label: "Remove this account") { onDelete(); dismiss() }
            }
        }
        .onAppear {
            guard !started else { return }
            started = true
            name = existing?.name ?? ""
            note = existing?.note ?? ""
            archived = existing?.archived ?? false
        }
    }

    private func save() {
        let cleanName = name.trimmingCharacters(in: .whitespaces)
        let cleanNote = note.trimmingCharacters(in: .whitespaces)
        if let existing {
            existing.name = cleanName
            existing.note = cleanNote.isEmpty ? nil : cleanNote
            existing.archived = archived
            onSave(existing)
        } else {
            onSave(FundAccount(name: cleanName, note: cleanNote.isEmpty ? nil : cleanNote,
                               sortOrder: order))
        }
        dismiss()
    }
}

/// Moving money from one account to another.
///
/// Recorded as a withdrawal and a matching deposit, so neither account's balance is anything
/// other than the sum of what went through it, and the fund's total does not move.
struct TransferSheet: View {
    let accounts: [FundAccount]
    let balances: [String: Int]
    let onTransfer: (FundAccount, FundAccount, Int, Date, String?) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accent) private var accent
    @State private var prefs = Preferences.shared

    @State private var fromId: String?
    @State private var toId: String?
    @State private var amount = ""
    @State private var note = ""
    @State private var date = Date.now
    @State private var started = false

    private var from: FundAccount? { accounts.first { $0.uuid == fromId } }
    private var to: FundAccount? { accounts.first { $0.uuid == toId } }

    var body: some View {
        SheetFrame {
            sheetTitle("Move money between accounts")
            sheetHint("Nothing enters or leaves the fund — the total is unchanged.")

            sheetLabel("From")
            ForEach(accounts) { account in
                SheetChoiceRow(
                    title: account.name,
                    subtitle: prefs.money(balances[account.uuid] ?? 0),
                    selected: account.uuid == fromId,
                    onPick: {
                        fromId = account.uuid
                        if toId == account.uuid {
                            toId = accounts.first { $0.uuid != account.uuid }?.uuid
                        }
                    }
                ) { RadioDot(on: account.uuid == fromId) }
            }

            sheetLabel("To")
            ForEach(accounts.filter { $0.uuid != fromId }) { account in
                SheetChoiceRow(
                    title: account.name,
                    subtitle: prefs.money(balances[account.uuid] ?? 0),
                    selected: account.uuid == toId,
                    onPick: { toId = account.uuid }
                ) { RadioDot(on: account.uuid == toId) }
            }

            sheetField("Amount (\(prefs.currency.symbol))", $amount, "0", numeric: true, big: true)
            sheetRow("Date") {
                DatePicker("", selection: $date, in: ...Date.now, displayedComponents: .date)
                    .labelsHidden().tint(accent.main)
            }
            sheetField("Note", $note, "Optional")

            PrimaryButton(label: "Record transfer", enabled: valid) {
                if let from, let to, let value = Int(amount) {
                    let trimmed = note.trimmingCharacters(in: .whitespaces)
                    onTransfer(from, to, value, date, trimmed.isEmpty ? nil : trimmed)
                }
                dismiss()
            }
            .padding(.top, 2)
        }
        .onAppear {
            guard !started else { return }
            started = true
            fromId = accounts.first?.uuid
            toId = accounts.dropFirst().first?.uuid
        }
    }

    private var valid: Bool {
        (Int(amount) ?? 0) > 0 && from != nil && to != nil && fromId != toId
    }
}

// ── The standing plan ────────────────────────────────────────────────────────

/// What the fund is called, and what goes into it each month.
struct FundPlanSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var prefs = Preferences.shared

    @State private var name = ""
    @State private var amount = ""
    @State private var day = ""
    @State private var started = false

    var body: some View {
        SheetFrame {
            sheetTitle("Monthly plan")
            sheetHint("What you move into the account each month. Kilkari says when it is due.")
            sheetField("Account name", $name, "Baby fund")
            sheetField("Amount (\(prefs.currency.symbol))", $amount, "0", numeric: true, big: true)
            sheetField("Day of month", $day, "1", numeric: true)

            PrimaryButton(label: "Save plan", enabled: valid) {
                prefs.fundName = name.trimmingCharacters(in: .whitespaces).isEmpty
                    ? "Baby fund"
                    : name.trimmingCharacters(in: .whitespaces)
                prefs.fundMonthly = Int(amount) ?? 0
                prefs.fundDepositDay = Int(day) ?? 1
                dismiss()
            }
            .padding(.top, 2)
        }
        .onAppear {
            guard !started else { return }
            started = true
            name = prefs.fundName
            amount = prefs.fundMonthly > 0 ? String(prefs.fundMonthly) : ""
            day = String(prefs.fundDepositDay)
        }
    }

    private var valid: Bool {
        guard let value = Int(amount), value > 0, let d = Int(day) else { return false }
        return (1...28).contains(d)
    }
}

// ── A holding's funding ──────────────────────────────────────────────────────

/// Where a holding's instalments come from.
///
/// Reached by tapping one of its lines on the fund ledger. A contribution is worked out from
/// the plan rather than typed, so there is no single instalment to correct — what changes is
/// whether the plan is funded from the fund at all, and out of which account.
struct HoldingFundingSheet: View {
    let holding: Investment
    let accounts: [FundAccount]
    let fundName: String
    let onDone: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var prefs = Preferences.shared

    @State private var fromFund = false
    @State private var accountId: String?
    @State private var started = false

    var body: some View {
        SheetFrame {
            sheetTitle(holding.name)
            sheetHint("Instalments are worked out from the plan, so there is no single one to "
                      + "change here. What can change is where the money comes from.")

            SheetToggleRow(title: "Paid from \(fundName)",
                           subtitle: "Every instalment comes off the balance",
                           on: $fromFund)

            if fromFund, accounts.count > 1 {
                accountChoice("Out of", accounts, [:], prefs, $accountId)
            }

            PrimaryButton(label: "Save") {
                holding.paidFromFund = fromFund
                holding.accountId = fromFund ? (accountId ?? accounts.first?.uuid) : nil
                onDone()
                dismiss()
            }
            .padding(.top, 2)
        }
        .onAppear {
            guard !started else { return }
            started = true
            fromFund = holding.paidFromFund
            accountId = holding.accountId ?? accounts.first?.uuid
        }
    }
}
