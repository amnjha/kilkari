import Foundation

/// What is in the fund, worked out rather than stored.
///
/// A balance kept as a number drifts: delete an expense and it is wrong until something
/// remembers to put it back. Every figure here is derived from the rows themselves, so a
/// correction anywhere moves the balance with it and there is nothing to keep in step.
///
/// Kept out of the screen, and out of SwiftData, so it can be tested as arithmetic — which is
/// what it is. The app hands it plain values; the same shapes the Android app's `FundMath`
/// works from.
public enum FundMath {

    /// Money in or out of an account, independent of any expense.
    ///
    /// Signed: positive in, negative out. A transfer is two of these sharing a `group`.
    public struct Movement: Sendable {
        public let id: String
        public let date: Date
        public let amount: Int
        public let note: String?
        public let accountId: String?
        public let group: String?
        public let reconciled: Bool

        public init(id: String, date: Date, amount: Int, note: String? = nil,
                    accountId: String? = nil, group: String? = nil, reconciled: Bool = false) {
            self.id = id
            self.date = date
            self.amount = amount
            self.note = note
            self.accountId = accountId
            self.group = group
            self.reconciled = reconciled
        }
    }

    /// Something bought or invested with the fund's money. Always out, so the amount is
    /// positive and the direction is in the type.
    public struct Outgoing: Sendable {
        public let id: String
        public let date: Date
        public let title: String
        public let subtitle: String
        public let amount: Int
        public let accountId: String?
        public let investment: Bool

        public init(id: String, date: Date, title: String, subtitle: String = "",
                    amount: Int, accountId: String? = nil, investment: Bool = false) {
            self.id = id
            self.date = date
            self.title = title
            self.subtitle = subtitle
            self.amount = amount
            self.accountId = accountId
            self.investment = investment
        }
    }

    public enum Origin: Sendable { case deposit, withdrawal, expense, investment }

    /// One line in the ledger, whatever produced it.
    public struct Line: Sendable, Identifiable {
        public let id: String
        public let date: Date
        public let title: String
        public let subtitle: String
        /// Always positive; `incoming` carries the direction.
        public let amount: Int
        public let incoming: Bool
        public let origin: Origin
        public let accountId: String?
        /// True for the two halves of a transfer, which move between accounts rather than in
        /// or out of the fund.
        public let transfer: Bool
    }

    /// Movements in, less everything paid out of the fund.
    public static func total(movements: [Movement], outgoings: [Outgoing]) -> Int {
        movements.reduce(0) { $0 + $1.amount } - outgoings.reduce(0) { $0 + $1.amount }
    }

    /// The same sum per account.
    ///
    /// A transfer falls out of this without a special case: one account is down and the other
    /// up by the same amount, and `total` is unchanged.
    public static func balances(movements: [Movement], outgoings: [Outgoing]) -> [String: Int] {
        var sums: [String: Int] = [:]
        for movement in movements {
            guard let id = movement.accountId else { continue }
            sums[id, default: 0] += movement.amount
        }
        for outgoing in outgoings {
            guard let id = outgoing.accountId else { continue }
            sums[id, default: 0] -= outgoing.amount
        }
        return sums
    }

    /// What is in the fund but not in any of the accounts given — money recorded before the
    /// first account existed, or filed against one since removed.
    public static func unassigned(movements: [Movement], outgoings: [Outgoing],
                                  accountIds: Set<String>) -> Int {
        func loose(_ id: String?) -> Bool { id.map { !accountIds.contains($0) } ?? true }
        return movements.filter { loose($0.accountId) }.reduce(0) { $0 + $1.amount }
            - outgoings.filter { loose($0.accountId) }.reduce(0) { $0 + $1.amount }
    }

    /// Every movement through the fund on one timeline, newest first, whatever produced it.
    public static func ledger(movements: [Movement], outgoings: [Outgoing]) -> [Line] {
        let fromMovements = movements.map { movement -> Line in
            let incoming = movement.amount >= 0
            let transfer = movement.group != nil
            return Line(
                id: movement.id,
                date: movement.date,
                title: {
                    switch (transfer, incoming) {
                    case (true, true): return "Transfer in"
                    case (true, false): return "Transfer out"
                    case (false, true): return "Deposit"
                    case (false, false): return "Withdrawal"
                    }
                }(),
                subtitle: movement.note ?? "",
                amount: abs(movement.amount),
                incoming: incoming,
                origin: incoming ? .deposit : .withdrawal,
                accountId: movement.accountId,
                transfer: transfer
            )
        }
        let fromOutgoings = outgoings.map { outgoing in
            Line(id: outgoing.id, date: outgoing.date, title: outgoing.title,
                 subtitle: outgoing.subtitle, amount: outgoing.amount, incoming: false,
                 origin: outgoing.investment ? .investment : .expense,
                 accountId: outgoing.accountId, transfer: false)
        }
        // Newest first, and stable within a day: two entries made on the same date should not
        // swap places between one render and the next.
        return (fromMovements + fromOutgoings).sorted {
            $0.date == $1.date ? $0.id > $1.id : $0.date > $1.date
        }
    }

    /// Whether this month's standing top-up still needs making: a plan is set, the day has come
    /// round, and no deposit has been recorded this month.
    public static func topUpDue(monthly: Int, depositDay: Int, lastDeposit: Date?,
                                today: Date = .now, calendar: Calendar = .current) -> Bool {
        guard monthly > 0 else { return false }
        guard calendar.component(.day, from: today) >= min(max(depositDay, 1), 28) else { return false }
        guard let last = lastDeposit else { return true }
        return !calendar.isDate(last, equalTo: today, toGranularity: .month)
    }
}
